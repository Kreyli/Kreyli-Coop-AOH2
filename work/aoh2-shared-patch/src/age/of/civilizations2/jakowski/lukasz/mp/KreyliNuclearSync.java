package age.of.civilizations2.jakowski.lukasz.mp;

/** Nuclear construction changes the authoritative country stock/production queue. */
public final class KreyliNuclearSync {
    private KreyliNuclearSync() {}

    public static void beforeCommand(String operation) {
        if ("bnuke".equals(operation)) {
            // Native buildNuke already forwards and validates the request. Unlike dnuke,
            // bnuke has no native sync category, leaving other seats' queues stale.
            MP_Cmds.SYNC.put("bnuke", MP_Cmds.Sync.ALL);
        }
    }
}
