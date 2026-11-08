# Kreyli Coop AOH2 — Steam bridge

Launch `age.of.civilizations2.jakowski.lukasz.mp.KreyliBootstrap` with `kreyli-steam.jar;shared-support.jar;game.jar` on the classpath, the shared agent enabled, working directory set to the game installation, and `steam_appid.txt` containing `480`. Uses the steamworks4j 1.9.0 classes and native libraries already present in the pinned upstream archive. Initializes Steam before the original launcher creates OpenGL. No Steam client installation or login automation is performed.

Root patch calls `tick()` on the game thread, `hostStarted()` after successful hosting, and `leaveSession()` when stopping multiplayer. UI calls `inviteFriends()` and displays `status()`. Steam callbacks also execute on the game thread. `-Dkreyli.steam.disabled=true` disables Steam for loopback tests.

Friends-only lobbies have a maximum of two members. Metadata identifies this protocol, contains the room code, relay endpoint and room password, and is visible to lobby members. Invitations resolve to the existing authoritative upstream TCP relay protocol; Steam is not the game-state transport. Direct LAN / Radmin VPN / Hamachi connections remain available without Steam. In direct LAN mode, the button explains that Steam invitations require room-code mode. No public lobby search or actual automatic friend invitations are sent.

Steam needs to be installed, running and logged in for its overlay and Spacewar presence. Overlay must be enabled in Steam. The package cannot provide a logged-in Steam account. Accept invitations with Kreyli Coop AOH2 already running: Steam's AppID 480 launch association is the Spacewar sample, not this private executable. Bootstrap accepts `+connect_lobby <id>` when a launcher explicitly forwards it, but does not register an OS-wide Steam launch replacement.

Build with `build.ps1`. Native archive SHA-256 is verified before compilation. We redistribute only our new bridge code here, not extracted SDK or game binaries separately.

## Checks actually run

- Java 21 compilation passed.
- Room, relay address and startup lobby argument validation checks passed.
- Disabled-Steam lifecycle (`initialize`, callbacks, leave, shutdown) passed.
- Native Steam initialization on this machine passed with **AppID 480**. Callbacks ran and shutdown completed. No friends were queried or invited.
- Headless native probe reported overlay disabled, expected because no OpenGL window existed. This does not establish that the in-game overlay works.
- Cross-account invitation acceptance, Steam lobby capacity and overlay rendering require the parent integrated tests; not established by the above smoke test.

Official reference: https://partner.steamgames.com/doc/features/overlay and https://partner.steamgames.com/doc/sdk/api/example .

2026-10-26 final integration correction: the vendor archive's steam_api64.dll was NOT the official Maven library (1,958,912 bytes vs official 295,336). Earlier vendor-only init=480 observations are not proof of Steam connectivity. Final launcher classpath explicitly loads official Maven Central steamworks4j 1.9.0 before game.jar. SHA256 F5E70A648F77D09B3A665AF5F8B110495B14F7E568280313B8A56903A4740379; downloaded artifact SHA1 matched repository checksum. MIT license included. Original game.jar is unchanged; its embedded alternative library is shadowed by the official artifact.

Final full-game test with bundled Temurin: native Steam initialized AppID480, overlayEnabled=true, launcher nickname propagated, main multiplayer screen rendered. Separate native test created a friends-only Steam lobby with getLobbyMemberLimit=2, invoked inviteFriends and received onGameOverlayActivated(true). No friend invitation sent. Lobby and relay session closed after test. Cross-account acceptance still untested. Sandbox invocation caused native socket-handle crash; same isolated test outside sandbox succeeded. Initial full-game watchdog expiration retried with frame diagnostics, then rendered through frame450 normally.
