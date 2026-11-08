param(
 [string]$GameSource=(Join-Path $PSScriptRoot '../aoh2-play'),
 [string]$SupportJar=(Join-Path $PSScriptRoot '../aoh2-shared-patch/build/shared-support.jar'),
 [string]$AgentJar=(Join-Path $PSScriptRoot '../aoh2-shared-patch/build-agent/shared-agent.jar'),
 [string]$SteamJar=(Join-Path $PSScriptRoot '../kreyli-steam/build/kreyli-steam.jar'),
 [switch]$PrepareOnly,
 [string]$ReusePayload
)
$ErrorActionPreference='Stop'
$runtime=Join-Path $PSScriptRoot 'tools/jdk-21.0.12.1+1-jre'
if((Get-FileHash (Join-Path $PSScriptRoot 'downloads/temurin-jre21.zip')).Hash -ne 'D35F31E712F0FCF6AC5A093EDC90204FBFF22F720BA3950BD09D331D5E621636'){throw 'Runtime hash mismatch'}
if((Get-FileHash (Join-Path $GameSource 'game.jar')).Hash -ne 'DB144F0DDB7E62DE92AD971EB7BC0B9A66B57CABBFE70B9DCDA7D2A70204704C'){throw 'Game hash mismatch'}
if($ReusePayload){$payload=(Resolve-Path $ReusePayload).Path}else{
 $payload=Join-Path $PSScriptRoot ('payload-'+[Guid]::NewGuid().ToString('N'))
 New-Item -ItemType Directory -Path $payload | Out-Null
 foreach($folder in @('game','map','music','sounds','UI','lwjgl')){
  & robocopy (Join-Path $GameSource $folder) (Join-Path $payload $folder) /E /NFL /NDL /NJH /NJS /NP | Out-Null
  if($LASTEXITCODE -ge 8){throw "Asset copy failed: $folder"}
 }
 & robocopy $runtime (Join-Path $payload 'runtime') /E /NFL /NDL /NJH /NJS /NP | Out-Null
 if($LASTEXITCODE -ge 8){throw 'Runtime copy failed'}
 Copy-Item -LiteralPath (Join-Path $GameSource 'game.jar') -Destination $payload
}
foreach($pair in @(@($SupportJar,'shared-support.jar'),@($AgentJar,'shared-agent.jar'),@($SteamJar,'kreyli-steam.jar'))){Copy-Item -LiteralPath $pair[0] -Destination (Join-Path $payload $pair[1]) -Force}
$sdk=Join-Path $PSScriptRoot '../kreyli-steam/vendor/steamworks4j-1.9.0.jar'
if((Get-FileHash -LiteralPath $sdk).Hash -ne 'F5E70A648F77D09B3A665AF5F8B110495B14F7E568280313B8A56903A4740379'){throw 'Official Steamworks4j checksum mismatch'}
New-Item -ItemType Directory -Force -Path (Join-Path $payload 'lib') | Out-Null
Copy-Item -LiteralPath $sdk -Destination (Join-Path $payload 'lib/steamworks4j-1.9.0.jar') -Force
Copy-Item -LiteralPath (Join-Path $PSScriptRoot '../kreyli-steam/vendor/STEAMWORKS4J-LICENSE.txt') -Destination (Join-Path $payload 'lib/STEAMWORKS4J-LICENSE.txt') -Force
Set-Content -LiteralPath (Join-Path $payload 'steam_appid.txt') -Value '480' -Encoding ascii
Set-Content -LiteralPath (Join-Path $payload '.kreyli-install') -Value 'Kreyli Coop AOH2 1.0' -Encoding ascii
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'README-PLAYER.txt') -Destination $payload -Force
$launcherIcon=Join-Path $PSScriptRoot 'branding/launcher.ico'
if(!(Test-Path -LiteralPath $launcherIcon)){$launcherIcon=Join-Path $PSScriptRoot 'branding/kreyli.ico'}
Copy-Item -LiteralPath $launcherIcon -Destination (Join-Path $payload 'kreyli.ico') -Force
if(Test-Path (Join-Path $PSScriptRoot 'update-channel.json')){Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'update-channel.json') -Destination $payload -Force}
$compiler=Join-Path $env:WINDIR 'Microsoft.NET/Framework64/v4.0.30319/csc.exe'
& $compiler /nologo /target:winexe /platform:x64 /optimize+ /reference:System.Windows.Forms.dll /reference:System.Drawing.dll /reference:System.Web.Extensions.dll /reference:System.IO.Compression.dll /reference:System.IO.Compression.FileSystem.dll ("/resource:"+(Join-Path $PSScriptRoot 'branding/project-logo.png')+',logo.png') ("/resource:"+(Join-Path $PSScriptRoot 'branding/launcher-background.png')+',background.png') ("/win32icon:"+$launcherIcon) ("/out:"+(Join-Path $payload 'Kreyli Coop AOH2.exe')) (Join-Path $PSScriptRoot 'Launcher.cs') (Join-Path $PSScriptRoot 'LauncherWindow.cs') (Join-Path $PSScriptRoot 'GameInstall.cs') (Join-Path $PSScriptRoot 'RemoteGame.cs')
if($LASTEXITCODE -ne 0){throw 'Launcher compilation failed'}
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Launcher.config') -Destination (Join-Path $payload 'Kreyli Coop AOH2.exe.config') -Force
$packages=Join-Path $payload 'packages'
New-Item -ItemType Directory -Force -Path $packages | Out-Null
Copy-Item -LiteralPath (Join-Path $GameSource 'game.jar') -Destination (Join-Path $packages 'network-game.jar') -Force
& robocopy (Join-Path $GameSource 'lwjgl') (Join-Path $packages 'native') /E /NFL /NDL /NJH /NJS /NP | Out-Null
if($LASTEXITCODE -ge 8){throw 'Native runtime cache copy failed'}
foreach($tool in @('7z.exe','7z.dll')){
 $expected=if($tool -eq '7z.exe'){'2BFF20BD679D45166B8C2D039044A4CA16189E6D69FF9C82345B4C1306986EC4'}else{'9657871FDD714C96A3A21F59ABF04ABB14944516C2A10C8CB606C12A71A75957'}
 $source=Join-Path $PSScriptRoot ('tools/7zip/'+$tool)
 if((Get-FileHash -LiteralPath $source).Hash -ne $expected){throw 'Extractor hash mismatch'}
 $target=Join-Path $packages $tool
 if(!(Test-Path -LiteralPath $target) -or (Get-FileHash -LiteralPath $target).Hash -ne $expected){Copy-Item -LiteralPath $source -Destination $target -Force}
}
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'tools/7zip/License.txt') -Destination (Join-Path $packages '7ZIP-LICENSE.txt') -Force
Set-Content -LiteralPath (Join-Path $packages '7ZIP-SOURCE.txt') -Value '7-Zip 26.01; https://www.7-zip.org/; source distribution available from its Download page.' -Encoding utf8
if(!(Test-Path (Join-Path $packages 'game-package.json'))){
 & $compiler /nologo /reference:System.Web.Extensions.dll ("/out:"+(Join-Path $PSScriptRoot 'MakeGamePackage.exe')) (Join-Path $PSScriptRoot 'MakeGamePackage.cs')
 if($LASTEXITCODE -ne 0){throw 'Package manifest compiler failed'}
 & (Join-Path $PSScriptRoot 'MakeGamePackage.exe') $payload (Join-Path $packages 'game-package.json')
 if($LASTEXITCODE -ne 0){throw 'Package manifest failed'}
}
Set-Content -LiteralPath (Join-Path $PSScriptRoot 'latest-payload.txt') -Value $payload -Encoding utf8
if($PrepareOnly){Write-Output "PAYLOAD=$payload";exit 0}
& (Join-Path $PSScriptRoot 'tools/inno/ISCC.exe') '/Qp' ("/DPayload=$payload") (Join-Path $PSScriptRoot 'setup.iss')
if($LASTEXITCODE -ne 0){throw 'Installer compilation failed'}
Get-FileHash (Join-Path $PSScriptRoot 'output/Kreyli-Coop-AOH2-Setup.exe') | Format-List
