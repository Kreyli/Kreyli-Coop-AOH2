param()
$ErrorActionPreference='Stop'
$localRoot=(Resolve-Path (Join-Path $PSScriptRoot '../coop-anything/local')).Path
$patchRoot=$PSScriptRoot
$steam=(Resolve-Path (Join-Path $PSScriptRoot '../kreyli-steam/build-final/kreyli-steam.jar')).Path
$sdk=(Resolve-Path (Join-Path $PSScriptRoot '../kreyli-steam/vendor/steamworks4j-1.9.0.jar')).Path
$channel=Join-Path $localRoot ('vendor-de-2.2/lobby-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $channel | Out-Null
$listener=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0);$listener.Start();$port=$listener.LocalEndpoint.Port;$listener.Stop()
function Start-Bot([string]$role){
 $install=Join-Path $localRoot "vendor-test-$role"
 if(!(Test-Path (Join-Path $install '.coop-test-copy'))){throw 'Incomplete disposable copy'}
 $arguments=@('--add-exports','java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED',"-javaagent:$patchRoot/build-agent/shared-agent.jar",'-Dkreyli.steam.disabled=true','-Dkreyli.discovery.disabled=true',"-Duser.home=$install/test-home","-Djava.io.tmpdir=$install/test-tmp",'-cp',"$patchRoot/test-build;$patchRoot/build;$steam;$sdk;$install/game.jar",'age.of.civilizations2.jakowski.lukasz.mp.KreyliLobbyProbe',$role,$channel,"$port")
 $quoted=($arguments|ForEach-Object{'"'+$_+'"'}) -join ' '
 Start-Process (Get-Command java).Source -ArgumentList $quoted -WorkingDirectory $install -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $channel "$role.out.log") -RedirectStandardError (Join-Path $channel "$role.err.log")
}
$hostBot=$null;$clientBot=$null
try{
 $hostBot=Start-Bot 'host';$deadline=[DateTime]::UtcNow.AddSeconds(180)
 while(!(Test-Path (Join-Path $channel 'ready'))){if($hostBot.HasExited -or [DateTime]::UtcNow -gt $deadline){throw 'Host did not become ready'};Start-Sleep -Milliseconds 500}
 $clientBot=Start-Bot 'client';$deadline=[DateTime]::UtcNow.AddSeconds(180)
 while(!$hostBot.HasExited -or !$clientBot.HasExited){
  if(($hostBot.HasExited -and $hostBot.ExitCode -ne 0) -or ($clientBot.HasExited -and $clientBot.ExitCode -ne 0)){throw 'Lobby bot failed'}
  if([DateTime]::UtcNow -gt $deadline){throw 'Lobby test timeout'};Start-Sleep -Milliseconds 500
 }
 if($hostBot.ExitCode -ne 0 -or $clientBot.ExitCode -ne 0 -or !(Test-Path (Join-Path $channel 'client-pass'))){throw 'Incomplete lobby verification'}
 Write-Output "KREYLI_LOBBY: PASS $channel"
}finally{
 foreach($p in @($hostBot,$clientBot)){if($null -ne $p){if(!$p.HasExited){$p.Kill();$p.WaitForExit()};$p.Dispose()}}
 Get-ChildItem -LiteralPath $channel -Filter '*.log' | ForEach-Object { $_.Name; Get-Content -LiteralPath $_.FullName -Tail 10 }
}

