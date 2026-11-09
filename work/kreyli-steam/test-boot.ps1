param([switch]$AllowSteam,[switch]$Invite)
$ErrorActionPreference='Stop'
if(!$AllowSteam){
 throw 'Steam test refused: -AllowSteam parameter is required to run Steam test.'
}
$workspace=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$payload=(Get-Content (Join-Path $PSScriptRoot '../kreyli-installer/latest-payload.txt') -Raw).Trim()
& javac -encoding UTF-8 -cp "$PSScriptRoot/build-final/kreyli-steam.jar;$payload/game.jar" -d "$PSScriptRoot/test-build" "$PSScriptRoot/test/GameBootProbe.java"
if($LASTEXITCODE -ne 0){throw 'Compile failed'}
$arguments=@('--add-exports','java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED',"-javaagent:$workspace/work/aoh2-shared-patch/build-agent/shared-agent.jar",'-Dkreyli.nick=LauncherTest',"-Dkreyli.steam.disabled=false","-Duser.home=$payload/coop-home","-Djava.io.tmpdir=$payload/coop-tmp",'-cp',"$PSScriptRoot/test-build;$PSScriptRoot/build-final/kreyli-steam.jar;$PSScriptRoot/vendor/steamworks4j-1.9.0.jar;$workspace/work/aoh2-shared-patch/build;$payload/game.jar",'age.of.civilizations2.jakowski.lukasz.mp.GameBootProbe')
$arguments=@("-Dkreyli.test.invite=$($Invite.ToString().ToLowerInvariant())")+$arguments
$p=Start-Process -FilePath "$payload/runtime/bin/java.exe" -ArgumentList (($arguments|ForEach-Object{'"'+$_+'"'}) -join ' ') -WorkingDirectory $payload -WindowStyle Hidden -PassThru -RedirectStandardOutput "$payload/boot.out.log" -RedirectStandardError "$payload/boot.err.log"
Set-Content "$PSScriptRoot/boot-pid.txt" $p.Id
try{$p.WaitForExit();Get-Content "$payload/boot.out.log" -Tail 20;Get-Content "$payload/boot.err.log" -Tail 12;if($p.ExitCode -ne 0){throw "Boot failed $($p.ExitCode)"}}finally{if(!$p.HasExited){$p.Kill()};$p.Dispose()}
