param([string]$MySqlBin = 'C:\Program Files\MySQL\MySQL Server 8.0\bin')

# Native smoke check. Uses a NEW data directory and a random port, never an installed service.
$ErrorActionPreference = 'Stop'
$project = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$helper = Join-Path $project 'src\main\resources\setup\provision-mysql.ps1'
$parseTokens = $null
$parseErrors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile($helper, [ref]$parseTokens, [ref]$parseErrors)
if ($parseErrors.Count) { throw ($parseErrors | Out-String) }
$validate = $ast.Find({ param($node)
    $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Assert-OracleInstaller'
}, $true)
. ([scriptblock]::Create($validate.Extent.Text))
$msi = Join-Path $project 'installer\mysql-installer-web-community-8.0.46.0.msi'
if (Test-Path -LiteralPath $msi) { Assert-OracleInstaller $msi }
Write-Output 'Windows helper parsed; bundled Oracle installer signature/product verified when present.'

$mysqld = Join-Path $MySqlBin 'mysqld.exe'
if (-not (Test-Path -LiteralPath $mysqld)) { throw 'Supply -MySqlBin pointing to an existing MySQL Server bin directory.' }
$work = Join-Path $project ('target\mysql-setup-smoke-' + [Guid]::NewGuid().ToString('N'))
$null = New-Item -ItemType Directory -Path $work
$data = Join-Path $work 'data'
$base = Split-Path -Parent $MySqlBin
$initArgs = @('--no-defaults', '--initialize-insecure', '--console', ('--basedir="' + $base + '"'), ('--datadir="' + $data + '"'))
$init = Start-Process -FilePath $mysqld -ArgumentList $initArgs -PassThru -WindowStyle Hidden `
    -RedirectStandardOutput (Join-Path $work 'initialize.stdout.log') -RedirectStandardError (Join-Path $work 'initialize.stderr.log')
$null = $init.Handle
if (-not $init.WaitForExit(120000)) { $init.Kill(); throw 'Disposable server initialization timed out.' }
if ($init.ExitCode -ne 0) { throw ('Disposable server initialization failed. Logs: ' + $work) }

$listener = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback, 0)
$listener.Start()
$port = $listener.LocalEndpoint.Port
$listener.Stop()
$server = $null
$passed = $false
try {
    $serverArgs = @('--no-defaults', '--console', ('--basedir="' + $base + '"'), ('--datadir="' + $data + '"'),
        ('--port=' + $port), '--bind-address=127.0.0.1', '--mysqlx=OFF', '--skip-log-bin')
    $server = Start-Process -FilePath $mysqld -ArgumentList $serverArgs -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $work 'server.stdout.log') -RedirectStandardError (Join-Path $work 'server.stderr.log')
    $null = $server.Handle
    $ready = $false
    $deadline = [DateTime]::UtcNow.AddSeconds(60)
    while ([DateTime]::UtcNow -lt $deadline) {
        if ($server.HasExited) { throw ('Disposable server exited. Logs: ' + $work) }
        $client = New-Object Net.Sockets.TcpClient
        try { $client.Connect('127.0.0.1', $port); $ready = $true; break }
        catch { Start-Sleep -Milliseconds 500 }
        finally { $client.Dispose() }
    }
    if (-not $ready) { throw 'Disposable server did not become ready.' }
    Push-Location $project
    try {
        & mvn -o '-Dtest=MySqlSetupIT' ('-Dpos.setup.it.port=' + $port) test *> (Join-Path $work 'test.log')
        $testExit = $LASTEXITCODE
        Get-Content -LiteralPath (Join-Path $work 'test.log') -Tail 22
        if ($testExit -ne 0) { throw ('Native password test failed. Logs: ' + $work) }
        $passed = $true
    } finally { Pop-Location }
} finally {
    if ($null -ne $server -and -not $server.HasExited) {
        & (Join-Path $MySqlBin 'mysqladmin.exe') --no-defaults --protocol=tcp --host=127.0.0.1 ('--port=' + $port) --user=root shutdown *> (Join-Path $work 'shutdown.log')
        if (-not $server.WaitForExit(10000)) { $server.Kill() }
    }
    if ($passed) {
        $resolvedData = (Resolve-Path -LiteralPath $data).Path
        $targetRoot = [IO.Path]::GetFullPath((Join-Path $project 'target')).TrimEnd('\') + '\'
        if (-not $resolvedData.StartsWith($targetRoot, [StringComparison]::OrdinalIgnoreCase) -or
            (Split-Path -Leaf $work) -notmatch '^mysql-setup-smoke-[0-9a-f]{32}$' -or
            (Split-Path -Leaf $resolvedData) -ne 'data' -or
            ((Get-Item -LiteralPath $resolvedData).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            throw 'Disposable data directory was not within the expected test workspace; cleanup stopped.'
        }
        Remove-Item -LiteralPath $resolvedData -Recurse -Force
    }
}
Write-Output ('Native database setup check passed. Logs: ' + $work)
