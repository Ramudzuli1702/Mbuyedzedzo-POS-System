param([string]$MySqlBin='C:\Program Files\MySQL\MySQL Server 8.0\bin',[switch]$Interactive,[switch]$DesktopUi)
$ErrorActionPreference='Stop'
$project=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$work=Join-Path $project ('target\companion-smoke-'+[Guid]::NewGuid().ToString('N'))
$null=New-Item -ItemType Directory -Path $work
$data=Join-Path $work 'data';$base=Split-Path -Parent $MySqlBin;$mysqld=Join-Path $MySqlBin 'mysqld.exe'
if(-not(Test-Path -LiteralPath $mysqld)){throw 'Supply -MySqlBin pointing to existing MySQL Server binaries.'}
$init=Start-Process -FilePath $mysqld -ArgumentList @('--no-defaults','--initialize-insecure','--console',('--basedir="'+$base+'"'),('--datadir="'+$data+'"')) -PassThru -WindowStyle Hidden -RedirectStandardOutput (Join-Path $work 'init.out') -RedirectStandardError (Join-Path $work 'init.err')
$null=$init.Handle
if(-not $init.WaitForExit(120000)){$init.Kill();throw 'Test database initialization timed out.'}
if($init.ExitCode-ne 0){throw ('Test database initialization failed. Logs: '+$work)}
$listener=New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback,0);$listener.Start();$port=$listener.LocalEndpoint.Port;$listener.Stop()
$server=$null;$passed=$false
try{
    $server=Start-Process -FilePath $mysqld -ArgumentList @('--no-defaults','--console',('--basedir="'+$base+'"'),('--datadir="'+$data+'"'),('--port='+$port),'--bind-address=127.0.0.1','--mysqlx=OFF','--skip-log-bin') -PassThru -WindowStyle Hidden -RedirectStandardOutput (Join-Path $work 'server.out') -RedirectStandardError (Join-Path $work 'server.err')
    $null=$server.Handle;$ready=$false;$deadline=[DateTime]::UtcNow.AddSeconds(60)
    while([DateTime]::UtcNow-lt $deadline){
        if($server.HasExited){throw ('Test server exited. Logs: '+$work)}
        $client=New-Object Net.Sockets.TcpClient
        try{$client.Connect('127.0.0.1',$port);$ready=$true;break}catch{Start-Sleep -Milliseconds 500}finally{$client.Dispose()}
    }
    if(-not $ready){throw 'Test server did not start.'}
    Push-Location $project
    try{
        $argsForMaven=@('-o','-Dtest=CompanionIT',('-Dpos.companion.it.port='+$port))
        if($Interactive){$argsForMaven+='-Dpos.companion.ui=true'}
        if($DesktopUi){$argsForMaven+='-Dpos.companion.desktop.ui=true'}
        # Capture native stderr before PowerShell interprets library warnings as errors.
        $logPath=Join-Path $work 'tests.log'
        $mavenCommand='mvn '+($argsForMaven -join ' ')+' test > "'+$logPath+'" 2>&1'
        & $env:ComSpec /d /c $mavenCommand
        $code=$LASTEXITCODE;Get-Content -LiteralPath (Join-Path $work 'tests.log') -Tail 26
        if($code-ne 0){throw ('Companion tests failed. Logs: '+$work)}
        $passed=$true
    }finally{Pop-Location}
}finally{
    if($null-ne $server-and -not $server.HasExited){
        & (Join-Path $MySqlBin 'mysqladmin.exe') --no-defaults --protocol=tcp --host=127.0.0.1 ('--port='+$port) --user=root shutdown *> (Join-Path $work 'shutdown.log')
        if(-not $server.WaitForExit(10000)){$server.Kill();$null=$server.WaitForExit(10000)}
    }
    if($passed){
        $resolved=(Resolve-Path -LiteralPath $data).Path;$target=[IO.Path]::GetFullPath((Join-Path $project 'target')).TrimEnd('\')+'\'
        if(-not $resolved.StartsWith($target,[StringComparison]::OrdinalIgnoreCase)-or (Split-Path -Leaf $work)-notmatch '^companion-smoke-[0-9a-f]{32}$'-or ((Get-Item -LiteralPath $resolved).Attributes-band [IO.FileAttributes]::ReparsePoint)){throw 'Test cleanup path validation failed.'}
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
Write-Output ('Companion database/socket checks passed. Logs: '+$work)
