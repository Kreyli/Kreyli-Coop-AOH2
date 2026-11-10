param([Parameter(Mandatory=$true)][string]$GameJar,[string]$SteamJar=(Join-Path $PSScriptRoot '../kreyli-steam/build/kreyli-steam.jar'))
$ErrorActionPreference='Stop'
if((Get-FileHash -LiteralPath $GameJar).Hash -ne 'DB144F0DDB7E62DE92AD971EB7BC0B9A66B57CABBFE70B9DCDA7D2A70204704C'){throw 'Unsupported upstream jar'}
$build=Join-Path $PSScriptRoot 'build';$agent=Join-Path $PSScriptRoot 'build-agent'
New-Item -ItemType Directory -Force -Path $build,$agent | Out-Null
$files=@(Get-ChildItem (Join-Path $PSScriptRoot 'src') -Recurse -Filter '*.java'|ForEach-Object{$_.FullName})
if(!(Test-Path -LiteralPath $SteamJar)){throw 'Build kreyli-steam.jar first'}
& javac -encoding UTF-8 -cp "$GameJar;$SteamJar" -d $build @files
if($LASTEXITCODE -ne 0){throw 'Support compile failed'}
& javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED -d $agent (Join-Path $PSScriptRoot 'agent/coop/SharedAgent.java')
if($LASTEXITCODE -ne 0){throw 'Agent compile failed'}
"Premain-Class: coop.SharedAgent`n`n" | Set-Content -Encoding ascii (Join-Path $agent 'MANIFEST.MF')
& jar "-J-Djava.io.tmpdir=$agent" cfm (Join-Path $agent 'shared-agent.jar') (Join-Path $agent 'MANIFEST.MF') -C $agent coop
if($LASTEXITCODE -ne 0){throw 'Jar build failed'}
$resources=Join-Path $PSScriptRoot 'resources'
Copy-Item -LiteralPath (Join-Path $resources 'kreyli-credits.wav') -Destination $build -Force
Copy-Item -LiteralPath (Join-Path $PSScriptRoot '../kreyli-installer/branding/project-logo.png') -Destination (Join-Path $build 'kreyli-splash.png') -Force
& jar "-J-Djava.io.tmpdir=$build" cf (Join-Path $build 'shared-support.jar') -C $build age -C $build 'kreyli-credits.wav' -C $build 'kreyli-splash.png'
if($LASTEXITCODE -ne 0){throw 'Support jar build failed'}
