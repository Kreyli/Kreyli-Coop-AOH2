$ErrorActionPreference='Stop'
$root=Join-Path $PSScriptRoot ('self-update-'+[guid]::NewGuid().ToString('N'))
$stage=Join-Path $root '.updates/release'
New-Item -ItemType Directory -Force -Path $root,$stage | Out-Null
Set-Content -LiteralPath (Join-Path $root '.kreyli-install') -Value 'Self-update test'
Set-Content -LiteralPath (Join-Path $root 'launcher-profile.json') -Value '{"nickname":"SavedNickname"}'
$oldSource=Join-Path $root 'old.cs';$newSource=Join-Path $root 'new.cs'
'using System; using System.IO; using System.Threading; class Stub { static void Main(){File.WriteAllText(Path.Combine(AppDomain.CurrentDomain.BaseDirectory,"old-started.txt"),"running");Thread.Sleep(2000);} }' | Set-Content -LiteralPath $oldSource
'using System; using System.IO; class Stub { static void Main(){File.WriteAllText(Path.Combine(AppDomain.CurrentDomain.BaseDirectory,"new-started.txt"),"replacement restarted");} }' | Set-Content -LiteralPath $newSource
$compiler=Join-Path $env:WINDIR 'Microsoft.NET/Framework64/v4.0.30319/csc.exe'
$old=Join-Path $root 'Kreyli Coop AOH2.exe';$new=Join-Path $stage 'Kreyli Coop AOH2.exe'
& $compiler /nologo /target:winexe ("/out:"+$old) $oldSource
if($LASTEXITCODE -ne 0){throw 'Old stub compile failed'}
& $compiler /nologo /target:winexe ("/out:"+$new) $newSource
if($LASTEXITCODE -ne 0){throw 'New stub compile failed'}
$expected=(Get-FileHash -LiteralPath $new).Hash
@{version='1.1.1';files=@(@{path='Kreyli Coop AOH2.exe';url='https://example.invalid/self-update-test.exe';size=(Get-Item $new).Length;sha256=$expected.ToLowerInvariant()})}|ConvertTo-Json -Depth 5|Set-Content (Join-Path $stage 'manifest.json') -Encoding utf8
$payload=(Get-Content (Join-Path $PSScriptRoot '../kreyli-installer/latest-payload.txt') -Raw).Trim()
$helper=Join-Path $stage 'KreyliUpdate.exe'
Copy-Item -LiteralPath (Join-Path $payload 'Kreyli Coop AOH2.exe') -Destination $helper
$parent=$null;$updater=$null
try{
 $parent=Start-Process -FilePath $old -WindowStyle Hidden -PassThru
 $argsLine='--apply-update "'+$root+'" "'+$stage+'" '+$parent.Id
 $updater=Start-Process -FilePath $helper -ArgumentList $argsLine -WindowStyle Hidden -PassThru
 if(!$updater.WaitForExit(20000)){throw 'Updater timed out'}
 if($updater.ExitCode -ne 0){throw 'Updater failed'}
 $deadline=[DateTime]::UtcNow.AddSeconds(10)
 while(!(Test-Path (Join-Path $root 'new-started.txt')) -and [DateTime]::UtcNow -lt $deadline){Start-Sleep -Milliseconds 100}
 if(!(Test-Path (Join-Path $root 'old-started.txt')) -or !(Test-Path (Join-Path $root 'new-started.txt'))){throw 'Old or replacement launcher did not run'}
 if((Get-FileHash -LiteralPath $old).Hash -ne $expected){throw 'Launcher binary not replaced'}
 if((Get-Content (Join-Path $root 'installed-version.txt') -Raw).Trim() -ne '1.1.1'){throw 'Version not recorded'}
 if((Get-Content (Join-Path $root 'launcher-profile.json') -Raw) -notmatch 'SavedNickname'){throw 'Profile changed'}
 'PASS self-update waits for running old executable, replaces launcher, restarts replacement, preserves nickname' | Tee-Object -FilePath (Join-Path $root 'result.txt')
}finally{foreach($p in @($parent,$updater)){if($null -ne $p){if(!$p.HasExited){$p.Kill();$p.WaitForExit()};$p.Dispose()}}}
