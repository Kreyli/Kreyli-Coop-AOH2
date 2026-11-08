param([string]$Setup=(Join-Path $PSScriptRoot 'output/Kreyli-Coop-AOH2-Setup.exe'),[switch]$KeepInstalled)
$ErrorActionPreference='Stop'
$testRoot=Join-Path $PSScriptRoot ('qa-'+[Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testRoot | Out-Null
$install=Join-Path $testRoot 'Kreyli Coop AOH2'
$log=Join-Path $testRoot 'install.log'
$timer=[Diagnostics.Stopwatch]::StartNew()
$proc=Start-Process -FilePath (Resolve-Path $Setup) -WindowStyle Hidden -PassThru -Wait -ArgumentList @('/VERYSILENT','/SUPPRESSMSGBOXES','/NORESTART','/WORKSPACETEST=1',('/DIR="'+$install+'"'))
$timer.Stop()
Write-Output ("Installation seconds: "+$timer.Elapsed.TotalSeconds.ToString('F2'))
if($proc.ExitCode -ne 0){throw "Setup failed: $($proc.ExitCode), $log"}
$payload=(Get-Content (Join-Path $PSScriptRoot 'latest-payload.txt') -Raw).Trim()
foreach($file in @('game.jar','shared-support.jar','shared-agent.jar','kreyli-steam.jar','Kreyli Coop AOH2.exe','runtime/bin/javaw.exe','runtime/bin/server/jvm.dll','update-channel.json','lib/steamworks4j-1.9.0.jar','kreyli.ico')){
 if((Get-FileHash (Join-Path $payload $file)).Hash -ne (Get-FileHash (Join-Path $install $file)).Hash){throw "Installed checksum mismatch: $file"}
}
$proc=Start-Process -FilePath (Join-Path $install 'Kreyli Coop AOH2.exe') -ArgumentList '--check' -WindowStyle Hidden -PassThru -Wait
if($proc.ExitCode -ne 0){throw 'Installed launcher check failed'}
& (Join-Path $install 'runtime/bin/java.exe') -version 2>&1 | Out-File (Join-Path $testRoot 'runtime.txt')
if($LASTEXITCODE -ne 0){throw 'Bundled runtime failed'}
Set-Content (Join-Path $install 'saves/qa-preserve.txt') -Value 'User save canary'
New-Item -ItemType Directory -Path (Join-Path $install 'settings') -Force | Out-Null
Set-Content (Join-Path $install 'settings/qa-preserve.txt') -Value 'User settings canary'
if(!$KeepInstalled){
 $resolved=(Resolve-Path $install).Path
 if(!$resolved.StartsWith($PSScriptRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or !(Test-Path (Join-Path $resolved '.kreyli-install'))){throw 'Unsafe uninstall QA path'}
 $proc=Start-Process -FilePath (Join-Path $resolved 'unins000.exe') -ArgumentList @('/VERYSILENT','/SUPPRESSMSGBOXES','/NORESTART') -WindowStyle Hidden -PassThru -Wait
 if($proc.ExitCode -ne 0){throw 'Uninstaller failed'}
 if(Test-Path (Join-Path $install 'game.jar')){throw 'Uninstaller left game.jar'}
 foreach($file in @('saves/qa-preserve.txt','settings/qa-preserve.txt')){if(!(Test-Path (Join-Path $install $file))){throw "Uninstaller removed user data: $file"}}
}
Set-Content (Join-Path $testRoot 'result.txt') -Value "PASS installer, critical file checksums, launcher --check, bundled runtime, user data preservation (uninstall skipped=$KeepInstalled)"
Get-Content (Join-Path $testRoot 'result.txt')
Write-Output "QA=$testRoot"
