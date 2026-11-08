# Kreyli discovery and nickname integration

`KreyliBrowser` isolates the relay browser namespace using `[KC2]` only on outgoing `I` info frames. The actual player nickname never changes. Incoming seven-column room rows must have that prefix; compatible row copies have it stripped before the native browser appends region labels. The base-mod and old-patch entries are omitted. This marker is discovery metadata, not authentication; the Kreyli-specific native protocol handshake enforces compatibility when joining.

`KreyliIdentity` applies the launcher `kreyli.nick` property after every native settings load. The entry screen displays it read-only and offers join, host, Kreyli browser, project Discord, and back. Nickname normalization matches upstream: control characters and semicolons removed, trimmed, capped at 24 UTF-16 code units. The launcher should apply that same validation. If launched without the property, existing MP nick is preserved for test/legacy launches.

Tests run: Java 21 compilation and FilterTest passed: info-frame-only mutation, base/old-room exclusion, source row immutability, marker-containing nickname round trip, Cyrillic nickname preservation, normalization, 24-character bound. Actual relay listing marker round trip needs the integrated relay test; not claimed by the pure tests. Native root ASM integration also needs verification.

Hook contract:
- MP_Net.writeLine(OutputStream,String) entry: replace arg1 with KreyliBrowser.advertise(String).
- MP_Net.listServers(String,int) every ARETURN: KreyliBrowser.filter(List).
- MP.loadSettings() every RETURN: KreyliIdentity.apply().
- Replace MP_UI.entry() body with KreyliIdentity.entry().

Decompiled inspection remains private under local/; do not include it in releases.

Integrated native check (2026-10-26): NativeFilterProbe PASSED with transformed MP_Net (2 hooks) and MP (19 hooks). A loopback mock relay verified actual native wire tagging and native list parsing/filtering. Native settings-load tests verified both existing properties and missing-file early return preserve launcher nickname. Initial local socket failure was sandbox network permission; rerun after granting network passed. This does not establish that the external author's relay preserves the marker; external listing is still untested and no room was published.
