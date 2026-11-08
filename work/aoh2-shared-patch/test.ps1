param([Parameter(Mandatory=$true)][string]$HostInstall,[Parameter(Mandatory=$true)][string]$ClientInstall,[switch]$Shared,[switch]$Army,[switch]$Race,[switch]$Battle,[switch]$Relay,[string]$RelayConfig)
$ErrorActionPreference='Stop'
$installations=@{host=(Resolve-Path -LiteralPath $HostInstall).Path;client=(Resolve-Path -LiteralPath $ClientInstall).Path}
$build=Join-Path $PSScriptRoot 'test-build'
$patchRoot=$PSScriptRoot
$Patch=$true
& (Join-Path $PSScriptRoot 'build.ps1') -GameJar (Join-Path $installations.host 'game.jar')
New-Item -ItemType Directory -Force -Path $build | Out-Null
$testSources=@(Get-ChildItem (Join-Path $PSScriptRoot 'test') -Filter '*.java' | ForEach-Object {$_.FullName})
& javac -cp "$patchRoot/build;$($installations.host)/game.jar" -d $build @testSources
if($LASTEXITCODE -ne 0){throw 'Test compile failed'}
$channel=Join-Path $PSScriptRoot ('network-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $channel | Out-Null
if($Relay){Copy-Item -LiteralPath $RelayConfig -Destination (Join-Path $channel 'relay-config.txt')}
$listener=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0);$listener.Start();$port=$listener.LocalEndpoint.Port;$listener.Stop()
function Start-Bot([string]$role){
 $install=$installations[$role]
 if(!(Test-Path (Join-Path $install '.coop-test-copy'))){throw 'Incomplete copy'}
 $arguments=@("-Duser.home=$install/test-home","-Djava.io.tmpdir=$install/test-tmp",'-cp',"$build;$install/game.jar",'age.of.civilizations2.jakowski.lukasz.mp.VendorNetworkProbe',$role,$channel,"$port")
 if($Patch){$arguments=@('--add-exports','java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED',"-javaagent:$patchRoot/build-agent/shared-agent.jar","-Dcoop.shared=$($Shared.ToString().ToLowerInvariant())","-Duser.home=$install/test-home","-Djava.io.tmpdir=$install/test-tmp",'-cp',"$build;$patchRoot/build;$patchRoot/../kreyli-steam/build-final/kreyli-steam.jar;$patchRoot/../kreyli-steam/vendor/steamworks4j-1.9.0.jar;$install/game.jar",'age.of.civilizations2.jakowski.lukasz.mp.VendorNetworkProbe',$role,$channel,"$port")}
 if($Patch){$arguments=@('-Dkreyli.steam.disabled=true','-Dkreyli.discovery.disabled=true','-Dcoop.patch=true',"-Dcoop.army=$($Army.ToString().ToLowerInvariant())","-Dcoop.race=$($Race.ToString().ToLowerInvariant())","-Dcoop.battle=$($Battle.ToString().ToLowerInvariant())","-Dcoop.relay=$($Relay.ToString().ToLowerInvariant())")+$arguments}
 $quoted=($arguments|ForEach-Object{'"'+$_+'"'}) -join ' '
 Start-Process (Get-Command java).Source -ArgumentList $quoted -WorkingDirectory $install -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $install 'vendor-network.out.log') -RedirectStandardError (Join-Path $install 'vendor-network.err.log')
}
$hostBot=$null;$clientBot=$null
try{
 $hostBot=Start-Bot 'host';$deadline=[DateTime]::UtcNow.AddSeconds(180)
 while(!(Test-Path (Join-Path $channel 'host-ready'))){if($hostBot.HasExited -or [DateTime]::UtcNow -gt $deadline){throw 'Host did not become ready'};Start-Sleep -Milliseconds 500}
 Write-Output 'Native upstream host ready; starting guest'
 $clientBot=Start-Bot 'client';$deadline=[DateTime]::UtcNow.AddSeconds(420)
 while(!$hostBot.HasExited -or !$clientBot.HasExited){
  if(($hostBot.HasExited -and $hostBot.ExitCode -ne 0) -or ($clientBot.HasExited -and $clientBot.ExitCode -ne 0)){throw 'Native bot failed; inspect logs'}
  if([DateTime]::UtcNow -gt $deadline){throw 'Network test timeout'};Start-Sleep -Milliseconds 500
 }
 foreach($role in @('host','client')){Get-Content (Join-Path $installations[$role] 'vendor-network.out.log')}
 if(!(Test-Path (Join-Path $channel 'client-pass'))){throw 'No client verification'}
 Write-Output 'VENDOR_NETWORK: PASS'
}finally{
 foreach($p in @($hostBot,$clientBot)){if($null -ne $p){if(!$p.HasExited){$p.Kill();$p.WaitForExit()};$p.Dispose()}}
 foreach($role in @('host','client')){foreach($kind in @('out','err')){
  $log=Join-Path $installations[$role] "vendor-network.$kind.log"
  if(Test-Path -LiteralPath $log){Copy-Item -LiteralPath $log -Destination (Join-Path $channel "$role.$kind.log")}
 }}
}


