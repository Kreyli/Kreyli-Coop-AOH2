param([switch]$Check)
$ErrorActionPreference='Stop'
try{
 if(!(Test-Path -LiteralPath (Join-Path $PSScriptRoot '.coop-install'))){throw 'Launch only from the separately installed co-op copy'}
 if((Get-FileHash -LiteralPath (Join-Path $PSScriptRoot 'game.jar')).Hash -ne 'DB144F0DDB7E62DE92AD971EB7BC0B9A66B57CABBFE70B9DCDA7D2A70204704C'){throw 'The mod version does not match this patch'}
 $java=(Get-Command java -ErrorAction Stop).Source
 $info=New-Object Diagnostics.ProcessStartInfo
 $info.FileName=$java;$info.Arguments='-version';$info.UseShellExecute=$false;$info.CreateNoWindow=$true;$info.RedirectStandardError=$true
 $versionProcess=[Diagnostics.Process]::Start($info)
 try{$version=$versionProcess.StandardError.ReadToEnd();$versionProcess.WaitForExit()}finally{$versionProcess.Dispose()}
 if($version -notmatch 'version "(\d+)' -or [int]$Matches[1] -lt 21){throw 'Java 21 or newer is required; the game bundled Java 8 is not compatible'}
 if($Check){Write-Output 'COOP_LAUNCHER: PASS archive, isolated installation and Java runtime';exit 0}
 $arguments=@('--add-exports','java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED',"-javaagent:$PSScriptRoot/shared-agent.jar","-Duser.home=$PSScriptRoot/coop-home","-Djava.io.tmpdir=$PSScriptRoot/coop-tmp",'-cp',"$PSScriptRoot/shared-support.jar;$PSScriptRoot/game.jar",'aoc.kingdoms.lukasz.jakowski.desktop.DesktopLauncher')
 $quoted=($arguments|ForEach-Object{if($_.Contains('"')){throw 'Unsupported quote in path'};'"'+$_+'"'}) -join ' '
 Start-Process -FilePath $java -ArgumentList $quoted -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $PSScriptRoot 'coop.out.log') -RedirectStandardError (Join-Path $PSScriptRoot 'coop.err.log')
}catch{Write-Host $_ -ForegroundColor Red;Read-Host 'Press Enter to close';exit 1}
