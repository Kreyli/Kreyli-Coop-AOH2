# Shared-country patch for AoH2DE Multiplayer 2.2 — test release

This package adds a shared/separate-country choice to the native host creation form. Shared guest commands execute on the host, then authoritative snapshots return confirmed state. Separate-country mode keeps upstream behavior. Both peers require this patch and the same pinned upstream mod.

Start with ИНСТРУКЦИЯ.md (Russian). TEST-RESULTS.md records actual tests and limitations. Do not assume all game mechanics or long campaigns have been verified.

Requirements: own DE 2.01 game resources, JDK/Java 21, and author AoH2DE-MP.jar SHA256 DB144F0DDB7E62DE92AD971EB7BC0B9A66B57CABBFE70B9DCDA7D2A70204704C. The installer creates a new separate game folder and saves; it never overwrites an existing installation. No automatic Java download, firewall change or router configuration.

Files shared-agent.jar and shared-support.jar contain only this patch's original code. They modify loaded classes in memory and do not rewrite the upstream jar. The protocol suffix prevents incompatible stock peers from joining. No game assets, upstream binaries or decompiled game source are included.

Implementation includes transient army/recruitment/movement/migration/plunder state restoration; per-snapshot acknowledgements; pending request counters; shared-mode country selection; and preserving host control when a guest departs. Full snapshots can pause the interface. Marine operations, event choices, longer sessions and recovery need more testing; see the test report.

Build: ./build.ps1 -GameJar '<pinned upstream jar>'. Tests: ./test.ps1 -HostInstall '<marked disposable copy>' -ClientInstall '<marked disposable copy>' -Shared, optionally -Army, -Race or -Battle. The public test wrapper is syntax checked; native verification used the same Java probes via a workspace-specific launcher. Test code intentionally modifies disposable campaigns.

Author mod: https://aoh2mp.web.app/ (canonical https://ageofhistory.mp/). Playbook: https://github.com/cranebeaker/coop-anything . The author's mod remains a separate dependency downloaded by each owner.
