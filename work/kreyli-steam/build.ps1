param([string]$GameJar=(Join-Path $PSScriptRoot '../coop-anything/local/vendor-de-2.2/AoH2DE-MP.jar'))
$ErrorActionPreference='Stop'
if((Get-FileHash -LiteralPath $GameJar).Hash -ne 'DB144F0DDB7E62DE92AD971EB7BC0B9A66B57CABBFE70B9DCDA7D2A70204704C'){throw 'Unsupported vendor jar'}
$build=Join-Path $PSScriptRoot 'build'
New-Item -ItemType Directory -Force $build | Out-Null
$sources=@(Get-ChildItem (Join-Path $PSScriptRoot 'src') -Recurse -Filter '*.java' | ForEach-Object FullName)
& 'C:/Program Files/Java/jdk-21.0.12.1/bin/javac.exe' -encoding UTF-8 -cp $GameJar -d $build @sources
if($LASTEXITCODE -ne 0){throw 'Compile failed'}
& 'C:/Program Files/Java/jdk-21.0.12.1/bin/jar.exe' "-J-Djava.io.tmpdir=$build" cf (Join-Path $build 'kreyli-steam.jar') -C $build age
if($LASTEXITCODE -ne 0){throw 'Jar failed'}
