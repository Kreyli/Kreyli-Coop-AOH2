param(
 [string]$Fixtures,
 [ValidateSet('reconnect','stranger','oldsave')][string[]]$Modes=@('reconnect','stranger','oldsave')
)
$ErrorActionPreference='Stop'
$localRoot=(Resolve-Path (Join-Path $PSScriptRoot '../coop-anything/local')).Path
$patchRoot=$PSScriptRoot
$testBuild=Join-Path $PSScriptRoot 'test-build'
if(!$Fixtures){$Fixtures=$localRoot}
$Fixtures=(Resolve-Path -LiteralPath $Fixtures).Path
foreach($role in @('host','client')){
 if(!(Test-Path -LiteralPath (Join-Path $Fixtures "vendor-test-$role/.coop-test-copy"))){throw 'Incomplete disposable copy'}
}
$gameJar=Join-Path $Fixtures 'vendor-test-host/game.jar'
& javac -encoding UTF-8 -cp "$PSScriptRoot/build;$gameJar" -d $testBuild (Join-Path $PSScriptRoot 'test/KreyliSeatSecurityProbe.java')
if($LASTEXITCODE -ne 0){throw 'Seat security probe compile failed'}
$steam=(Resolve-Path (Join-Path $PSScriptRoot '../kreyli-steam/build/kreyli-steam.jar')).Path
$sdk=(Resolve-Path (Join-Path $PSScriptRoot '../kreyli-steam/vendor/steamworks4j-1.9.0.jar')).Path

function Run-TestMode([string]$mode){
 $channel=Join-Path $localRoot ('vendor-de-2.2/lobby-'+[guid]::NewGuid().ToString('N'))
 New-Item -ItemType Directory -Path $channel | Out-Null
 $listener=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0);$listener.Start();$port=$listener.LocalEndpoint.Port;$listener.Stop()

 $hostInstall=Join-Path $Fixtures "vendor-test-host"
 $clientInstall=Join-Path $Fixtures "vendor-test-client"

 $hostArgs=@('--add-exports','java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED',"-javaagent:$patchRoot/build-agent/shared-agent.jar",'-Dkreyli.steam.disabled=true','-Dkreyli.discovery.disabled=true',"-Duser.home=$hostInstall/test-home","-Djava.io.tmpdir=$hostInstall/test-tmp",'-cp',"$patchRoot/build;$patchRoot/test-build;$steam;$sdk;$hostInstall/game.jar",'age.of.civilizations2.jakowski.lukasz.mp.KreyliSeatSecurityProbe','host',$channel,"$port",$mode)
 $clientArgs=@('--add-exports','java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED',"-javaagent:$patchRoot/build-agent/shared-agent.jar",'-Dkreyli.steam.disabled=true','-Dkreyli.discovery.disabled=true',"-Duser.home=$clientInstall/test-home","-Djava.io.tmpdir=$clientInstall/test-tmp",'-cp',"$patchRoot/build;$patchRoot/test-build;$steam;$sdk;$clientInstall/game.jar",'age.of.civilizations2.jakowski.lukasz.mp.KreyliSeatSecurityProbe','client',$channel,"$port",$mode)

 $hostProc=$null
 $clientProc=$null
 try {
 $hostProc=Start-Process (Get-Command java).Source -ArgumentList (($hostArgs|ForEach-Object{'"'+$_+'"'}) -join ' ') -WorkingDirectory $hostInstall -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $channel "host.out.log") -RedirectStandardError (Join-Path $channel "host.err.log")
 $clientProc=Start-Process (Get-Command java).Source -ArgumentList (($clientArgs|ForEach-Object{'"'+$_+'"'}) -join ' ') -WorkingDirectory $clientInstall -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $channel "client.out.log") -RedirectStandardError (Join-Path $channel "client.err.log")

 $deadline=[DateTime]::UtcNow.AddSeconds(480)
 while(!(Test-Path (Join-Path $channel "$mode-done"))){
  if(($hostProc.HasExited -and !(Test-Path (Join-Path $channel "$mode-done"))) -or ($clientProc.HasExited -and !(Test-Path (Join-Path $channel "$mode-done")))){
   throw "Security test process exited before completion for mode $mode"
  }
  if([DateTime]::UtcNow -gt $deadline){
   throw "Security test timeout for mode $mode"
  }
  Start-Sleep -Milliseconds 500
 }
 
 if(!$hostProc.WaitForExit(10000) -or $hostProc.ExitCode -ne 0){throw "Host did not verify completion for mode $mode"}
 Write-Output "SEAT_SECURITY: PASS mode $mode $channel"
 } finally {
  foreach($process in @($hostProc,$clientProc)){
   if($null -ne $process){
    if(!$process.HasExited){$process.Kill();$process.WaitForExit()}
    $process.Dispose()
   }
  }
  Get-ChildItem -LiteralPath $channel -Filter '*.log' | ForEach-Object { $_.Name; Get-Content -LiteralPath $_.FullName -Tail 15 }
 }
}

foreach($mode in $Modes){Run-TestMode $mode}
Write-Output "KREYLI_SEAT_SECURITY: REQUESTED TESTS PASSED"
