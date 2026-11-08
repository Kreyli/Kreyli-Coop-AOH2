# Kreyli Coop AOH2 packaging

Private full-game build for the owner's local copy. Do not publish its payload or installer.

Official build tool: Inno Setup 6.7.3, portable install inside `tools/inno`.
Source: https://github.com/jrsoftware/issrc/releases/tag/is-6_7_3
Installer SHA256: `9c73c3bae7ed48d44112a0f48e66742c00090bdb5bef71d9d3c056c66e97b732`.
Verified against immutable release API digest and valid Authenticode publisher Pyrsys B.V.
NSIS mirrors returned HTML rather than a ZIP; none of those files were executed.

Runtime: Eclipse Temurin JRE 21.0.12.1+1 x64, metadata retained in downloads/adoptium-metadata.json.
ZIP SHA256: `d35f31e712f0fcf6ac5a093edc90204fbff22f720ba3950bd09d331d5e621636`.
Bundled legal directory, NOTICE and release information remain with runtime.

Brand source: ../kreyli-branding/kreyli.png, generated with imagegen (white Kreyli on solid red).
Only size/format conversion was used for wizard.bmp and kreyli.ico.

Build: `build.ps1`. After staging, use `build.ps1 -ReusePayload <latest-payload.txt value>`
to refresh all three mod jars and rebuild. The source game and test copies are never modified.
Each new payload uses a unique directory. No recursive deletion is used in the build.

Installer installs for current user without elevation, includes JRE, base game assets,
pinned upstream mod and Kreyli patch jars. It creates a desktop shortcut by default.
Original saves, network passwords, tests, logs, old launchers and cached config are excluded.
The launcher uses a relative bundled runtime, explicit working directory and pinned game hash.
It does not install Java globally, change PATH, open firewall rules or install VPN drivers.
Steam must already be installed and signed in for Steam-specific features.

`test-setup.ps1` installs to a unique workspace directory with WORKSPACETEST=1;
this suppresses shortcuts and uninstall registry entries. It checks installed critical
file hashes, native launcher preflight, bundled runtime, and retention of save/settings
canaries after uninstall. Normal install behavior has standard Inno uninstall registration.

The setup is unsigned. A Windows trust prompt is possible. No signing certificate is available.

2026-10-27: dedicated self-update integration test passed: old running executable exited, helper replaced launcher binary, restarted replacement and preserved profile. Automatic check is attached to LauncherWindow.Shown and pre-launch. Channel intentionally unset until a real publishing endpoint is selected. Final installer SHA256 BED9206241A499D47064E242E260588436D9A135548DFA2B14D9401E1235EC29. Previous clean-install/preflight/runtime/hash checks and uninstall preserving saves/settings passed; final rebuild being rechecked.

FINAL QA 2026-10-27: exact final Setup.exe installed to qa-11b4ba9914f449d49e339385d094c5ca, critical file hashes matched, official Steam SDK hash matched, installed launcher --check passed, bundled Java ran, uninstaller succeeded and preserved saves/settings canaries. Full-game/Steam/lobby and updater tests above remain passed. Final installer SHA256 BED9206241A499D47064E242E260588436D9A135548DFA2B14D9401E1235EC29, 399382943 bytes. Live update hosting not configured; no public upload; cross-account Steam invitation acceptance and physical two-PC VPN session untested.
