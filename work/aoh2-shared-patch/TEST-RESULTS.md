# Test results — 2026-10-26

Pinned upstream: AoH2DE Multiplayer 2.2 / DE 2.01. Archive SHA256 DB144F0DDB7E62DE92AD971EB7BC0B9A66B57CABBFE70B9DCDA7D2A70204704C. Tested Windows with JDK 21.0.12.1, two hidden native game processes and disposable campaigns.

Passed:
- Full archive CRC verification, unmodified upstream fresh campaign boot.
- Unmodified upstream separate-country join, guest investment exactly once, native next-turn snapshot with matching tested gold.
- Patched shared-country selection, no optimistic guest investment spending, host confirmation and next turn.
- Shared recruitment: no early spending; complete transient army/order/movement sidecar comparison after snapshot; soldiers present next turn.
- Shared overlapping spending: initial treasury 150, host and guest requests of 100 in different provinces; native host clamped available funds, both copies converged to 0, next turn completed.
- Shared guest began with mode disabled and learned shared mode from host snapshot. After guest departure the host country remained human-controlled.
- Patched separate-country regression: selection, investment, next turn; both exit 0.
- Shared military scenario: guest declaration of war and one-soldier attack order against a defended neighboring foreign province; no early guest effects; pre-turn and post-turn armies/orders/movement, all country gold/alliance IDs, and all province controller/true-owner IDs matched. This does not cover every battle mechanic or prove full world equality.
- Internet relay: two clients on this computer connected through the author's EU Central relay in a private password-protected room, shared investment and next turn completed. Public listing was disabled. Both processes exited 0 and closed the room. This exercises the external relay path, not separate physical computers or every ISP/firewall combination.
- Native host form shared/separate toggle. Both states rendered at 1280x720 and screenshot visually inspected. Packaged agent/support jars loaded in the final UI probe.
- Installer created a separate play installation without original saves. Original game.jar hash still FF37D8EC67B9C3B45F3F39E7C839E367A6025006D950235B3D801E7C94CCA3C4.

Failures found and corrected: ASM initially used bootstrap class loading; fixed to use game ClassLoader. Shared snapshots needed transient army/order/movement restoration. A stale snapshot must not clear a newer pending request; processed-command counters now guard it. Turn readiness is blocked while the guest awaits approval. Native/JNI compatibility warning from upstream LWJGL occurred even in successful runs.

Limits: mostly one-turn scenarios, two players, selected-state comparisons. Long sessions, three or more peers, all events/diplomacy, sea/migration/plunder gameplay and reconnect recovery are not fully validated. Same-country commands transfer full snapshots and may pause the interface. This is a test release, not a guarantee of a complete error-free campaign.

Reproduction: build.ps1 needs the pinned upstream jar. test.ps1 uses marked disposable full game copies; choose -Shared and optionally -Army, -Race or -Battle. -Relay additionally needs a current author relay config file. Test requests must be trusted and test worlds disposable. The packaged portable wrapper was syntax checked; native runs used its original workspace-specific wrapper with the same Java probes. No game assets, vendor classes or decompiled sources are included in the patch archive.
