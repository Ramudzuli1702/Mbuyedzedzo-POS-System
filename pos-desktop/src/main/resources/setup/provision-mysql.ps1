param(
    [Parameter(Mandatory = $true)][ValidateSet('Start', 'Install')][string]$Action,
    [Parameter(Mandatory = $true)][string]$WorkDir,
    [string]$BundledInstaller = ''
)

# This helper is launched with UAC by the POS. It never receives the user's password.
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$bootstrap = ''
$mutex = $null
$locked = $false
$logPath = Join-Path $WorkDir 'setup.log'

function Write-SetupLog([string]$Message) {
    if ($bootstrap) { $Message = $Message.Replace($bootstrap, '[redacted]') }
    Add-Content -LiteralPath $logPath -Value $Message -Encoding UTF8
}

function Write-SetupStatus([string]$Message) {
    Write-SetupLog $Message
    [IO.File]::WriteAllText((Join-Path $WorkDir 'status.txt'), $Message, [Text.Encoding]::UTF8)
}

function Get-MySqlServices {
    @(Get-CimInstance Win32_Service | Where-Object {
        $_.PathName -match '(?i)[\\]mysqld(?:-nt)?\.exe(?:["\s]|$)'
    })
}

function Start-MySqlService($ServiceInfo) {
    Write-SetupLog ('Starting service: ' + $ServiceInfo.Name)
    Write-SetupStatus 'Starting your database service...'
    $service = Get-Service -Name $ServiceInfo.Name
    if ($service.Status -ne 'Running') { Start-Service -Name $ServiceInfo.Name }
    $service.WaitForStatus('Running', [TimeSpan]::FromMinutes(2))
}

function Invoke-SetupProcess([string]$Executable, [string]$Arguments) {
    $info = New-Object System.Diagnostics.ProcessStartInfo
    $info.FileName = $Executable
    $info.Arguments = $Arguments
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.WorkingDirectory = Split-Path -Parent $Executable
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $info
    try {
        if (-not $process.Start()) { throw 'Could not start the MySQL installation process.' }
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit(1800000)) {
            $process.Kill()
            throw 'MySQL installation timed out. Let any Windows installer finish before selecting Retry.'
        }
        Write-SetupLog ($stdout.GetAwaiter().GetResult())
        Write-SetupLog ($stderr.GetAwaiter().GetResult())
        return $process.ExitCode
    } finally { $process.Dispose() }
}

function Assert-OracleInstaller([string]$Installer) {
    $signature = Get-AuthenticodeSignature -LiteralPath $Installer
    if ($signature.Status -ne 'Valid' -or $signature.SignerCertificate.Subject -notmatch 'O="?Oracle (America|Corporation)') {
        throw 'The MySQL download does not have a valid Oracle signature. It was not executed.'
    }
    # Prevent the old renamed server MSI from being mistaken for the web bootstrapper.
    $windowsInstaller = New-Object -ComObject WindowsInstaller.Installer
    $database = $windowsInstaller.OpenDatabase($Installer, 0)
    $view = $database.OpenView("SELECT ``Value`` FROM ``Property`` WHERE ``Property``='ProductName'")
    $view.Execute()
    $record = $view.Fetch()
    if ($null -eq $record -or $record.StringData(1) -notmatch '^MySQL Installer') {
        throw 'This package is not MySQL Installer for Windows. The server MSI cannot be used as its replacement.'
    }
    $view.Close()
}

function Get-WebInstaller {
    if ($BundledInstaller -and (Test-Path -LiteralPath $BundledInstaller -PathType Leaf)) {
        Assert-OracleInstaller $BundledInstaller
        return $BundledInstaller
    }
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $name = 'mysql-installer-web-community-8.0.46.0.msi'
    $destination = Join-Path $WorkDir $name
    $sources = @(
        ('https://dev.mysql.com/get/Downloads/MySQLInstaller/' + $name),
        ('https://cdn.mysql.com/Downloads/MySQLInstaller/' + $name)
    )
    foreach ($source in $sources) {
        try {
            Write-SetupStatus 'Downloading the database installer...'
            Write-SetupLog ('Downloading MySQL Installer from ' + $source)
            Invoke-WebRequest -Uri $source -OutFile $destination -UseBasicParsing -TimeoutSec 300
            Assert-OracleInstaller $destination
            return $destination
        } catch { Write-SetupLog 'This download source was unavailable or did not supply a valid installer.' }
    }
    throw 'MySQL could not be downloaded. Check your internet connection and select Retry.'
}

try {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($identity)
    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        throw 'Windows administrator approval is required to install or start the database service.'
    }
    $mutex = New-Object Threading.Mutex($false, 'Global\MbuyedzedzoMySqlSetup')
    try { $locked = $mutex.WaitOne([TimeSpan]::FromSeconds(5)) }
    catch [Threading.AbandonedMutexException] { $locked = $true }
    if (-not $locked) { throw 'Another database setup is running. Wait for it to finish and select Retry.' }

    $services = @(Get-MySqlServices)
    if ($Action -eq 'Start') {
        if ($services.Count -eq 0) { throw 'No MySQL database service was found.' }
        foreach ($service in $services) { Start-MySqlService $service }
        exit 0
    }
    if ($services.Count -gt 0) {
        throw 'A MySQL service was found after setup started. Select Retry to connect using its existing password.'
    }
    # Do not install over an unrelated listener on the default database port.
    $listener = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback, 3306)
    try { $listener.Start() }
    catch { throw 'Port 3306 is already in use. Close the conflicting service and select Retry.' }
    finally { $listener.Stop() }

    $bootstrap = (Get-Content -LiteralPath (Join-Path $WorkDir 'bootstrap.txt') -Raw).Trim()
    if ($bootstrap -notmatch '^Aa1[0-9a-f]{28}!$') { throw 'The database setup request is invalid.' }
    $installer = Get-WebInstaller
    Write-SetupStatus 'Preparing the database installer...'
    $msiLog = Join-Path $WorkDir 'mysql-installer.log'
    $code = Invoke-SetupProcess (Join-Path $env:SystemRoot 'System32\msiexec.exe') (
        '/i "' + $installer + '" /qn /norestart /l*v "' + $msiLog + '"')
    if ($code -notin @(0, 3010)) {
        throw ('MySQL Installer could not be installed (Windows Installer code ' + $code + '). See mysql-installer.log.')
    }

    $console = $null
    foreach ($root in @(${env:ProgramFiles(x86)}, $env:ProgramFiles)) {
        if (-not $root) { continue }
        $candidate = Join-Path $root 'MySQL\MySQL Installer for Windows\MySQLInstallerConsole.exe'
        if (Test-Path -LiteralPath $candidate -PathType Leaf) { $console = $candidate; break }
    }
    if (-not $console) { throw 'MySQL Installer was installed, but its console could not be found.' }
    # The web MSI only installs the installer. This second step installs AND configures the server.
    # Oracle's documented selector: product;version;architecture:*:type=config;...
    $spec = 'server;8.0.46;x64:*:type=config;open_win_firewall=false;tcp_ip=true;port=3306;root_passwd=' + $bootstrap
    Write-SetupStatus 'Downloading, installing and configuring your database. This can take several minutes...'
    $code = Invoke-SetupProcess $console ('--install ' + $spec + ' --silent --auto-handle-prereqs')
    if ($code -notin @(0, 3010)) { throw ('MySQL Server setup failed (code ' + $code + '). See setup.log.') }
    $services = @(Get-MySqlServices)
    if ($services.Count -eq 0) {
        throw 'MySQL Installer did not create a database service. Check setup.log and select Retry.'
    }
    foreach ($service in $services) {
        Set-Service -Name $service.Name -StartupType Automatic
        Start-MySqlService $service
    }
    Write-SetupLog 'Database service ready. The POS will now set the chosen password and create its database.'
    exit 0
} catch {
    $message = $_.Exception.Message
    if ($bootstrap) { $message = $message.Replace($bootstrap, '[redacted]') }
    Write-SetupLog $message
    [IO.File]::WriteAllText((Join-Path $WorkDir 'error.txt'), $message, [Text.Encoding]::UTF8)
    exit 1
} finally {
    if ($locked) { $mutex.ReleaseMutex() }
    if ($null -ne $mutex) { $mutex.Dispose() }
}
