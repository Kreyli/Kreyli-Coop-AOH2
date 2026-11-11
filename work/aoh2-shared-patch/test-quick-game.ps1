$ErrorActionPreference='Stop'
$patch=$PSScriptRoot
$steam=(Resolve-Path (Join-Path $patch '../kreyli-steam/build/kreyli-steam.jar')).Path
$sdk=(Resolve-Path (Join-Path $patch '../kreyli-steam/vendor/steamworks4j-1.9.0.jar')).Path
$install=(Resolve-Path (Join-Path $patch '../coop-anything/local/vendor-test-host')).Path
if(!(Test-Path (Join-Path $install '.coop-test-copy'))){throw 'Disposable copy required'}
$jar=Join-Path $install 'game.jar'
$cp="$patch/test-build;$patch/build;$steam;$sdk;$jar"
javac -encoding UTF-8 -cp $cp -d "$patch/test-build" "$patch/test/KreyliQuickGameProbe.java" "$patch/test/KreyliLobbyProbe.java" "$patch/test/KreyliUiProbe.java"
if($LASTEXITCODE){throw 'Quick probe compilation failed'}
$listener=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0)
$listener.Start()
$port=$listener.LocalEndpoint.Port
$listener.Stop()
Push-Location $install
try {
    java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED "-javaagent:$patch/build-agent/shared-agent.jar" '-Dkreyli.steam.disabled=true' '-Dkreyli.discovery.disabled=true' "-Duser.home=$install/test-home" "-Djava.io.tmpdir=$install/test-tmp" -cp $cp age.of.civilizations2.jakowski.lukasz.mp.KreyliQuickGameProbe $port
    if($LASTEXITCODE){throw 'Quick probe failed'}
} finally { Pop-Location }
