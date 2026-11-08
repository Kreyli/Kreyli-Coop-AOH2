param([Parameter(Mandatory=$true)][string]$Version,[Parameter(Mandatory=$true)][string]$AssetBaseUrl,[Parameter(Mandatory=$true)][string]$Payload)
$ErrorActionPreference='Stop'
$parsed=[version]$Version
$uri=[uri]$AssetBaseUrl
if(!$uri.IsAbsoluteUri -or $uri.Scheme -ne 'https'){throw 'Use the HTTPS release download URL'}
$out=Join-Path $PSScriptRoot ('release-'+$parsed+'-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $out | Out-Null
$files=@()
foreach($name in @('shared-agent.jar','shared-support.jar','kreyli-steam.jar','Kreyli Coop AOH2.exe','Kreyli Coop AOH2.exe.config','README-PLAYER.txt','kreyli.ico','steam_appid.txt','lib/steamworks4j-1.9.0.jar','lib/STEAMWORKS4J-LICENSE.txt')){
 $source=Join-Path $Payload $name
 $assetName=[IO.Path]::GetFileName($name) -replace '[^A-Za-z0-9._-]','-'
 Copy-Item -LiteralPath $source -Destination (Join-Path $out $assetName)
 $files+=@{path=$name;url=$AssetBaseUrl.TrimEnd('/')+'/'+[uri]::EscapeDataString($assetName);sha256=(Get-FileHash -LiteralPath $source).Hash.ToLowerInvariant();size=(Get-Item -LiteralPath $source).Length}
}
@{version=$parsed.ToString();files=$files}|ConvertTo-Json -Depth 6|Set-Content -LiteralPath (Join-Path $out 'update.json') -Encoding utf8
Write-Output "Release files prepared (not published): $out"
