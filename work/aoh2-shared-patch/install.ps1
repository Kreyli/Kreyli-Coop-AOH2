param(
 [Parameter(Mandatory=$true)][string]$GameDirectory,
 [Parameter(Mandatory=$true)][string]$UpstreamJar,
 [Parameter(Mandatory=$true)][string]$Destination
)
$ErrorActionPreference='Stop'
$source=(Resolve-Path -LiteralPath $GameDirectory).Path
$upstream=(Resolve-Path -LiteralPath $UpstreamJar).Path
$dest=[IO.Path]::GetFullPath($Destination)
if(Test-Path -LiteralPath $dest){throw 'Choose a new, empty destination path; existing folders are never overwritten'}
if($dest.StartsWith($source.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'Destination must be outside the source game folder'}
if((Get-FileHash -LiteralPath $upstream).Hash -ne 'DB144F0DDB7E62DE92AD971EB7BC0B9A66B57CABBFE70B9DCDA7D2A70204704C'){throw 'Unsupported upstream mod archive'}
foreach($name in @('shared-agent.jar','shared-support.jar','launch.ps1','Play-Coop.cmd')){if(!(Test-Path -LiteralPath (Join-Path $PSScriptRoot $name))){throw "Missing package file: $name"}}
foreach($name in @('game','map','UI','settings')){if(!(Test-Path -LiteralPath (Join-Path $source $name))){throw "Missing game assets: $name"}}
New-Item -ItemType Directory -Path $dest | Out-Null
foreach($name in @('game','map','UI','settings','lwjgl','sounds','music')){
 $from=Join-Path $source $name
 if(Test-Path -LiteralPath $from){
  Write-Output "Copying $name"
  & robocopy $from (Join-Path $dest $name) /E /MT:24 /R:0 /W:0 /NFL /NDL /NJH /NJS /NP
  if($LASTEXITCODE -ge 8){throw "Asset copy failed: $name. Incomplete copy retained for diagnosis."}
 }
}
Copy-Item -LiteralPath $upstream -Destination (Join-Path $dest 'game.jar')
foreach($name in @('shared-agent.jar','shared-support.jar','launch.ps1','Play-Coop.cmd')){Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination $dest}
foreach($name in @('saves','mods','coop-home','coop-tmp')){New-Item -ItemType Directory -Path (Join-Path $dest $name) | Out-Null}
@('nick=','hostDirect=true','listInLobby=false','port=27026','turnSeconds=0') | Set-Content -Encoding ascii (Join-Path $dest 'saves/multiplayer.properties')
'Isolated co-op installation. Original game and saves were not changed.' | Set-Content (Join-Path $dest '.coop-install')
Write-Output "Installed to $dest. Launch Play-Coop.cmd (Java 21 or newer required)."
