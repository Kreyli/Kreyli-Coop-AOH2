package age.of.civilizations2.jakowski.lukasz.mp;
import age.of.civilizations2.jakowski.lukasz.*;
import age.of.civilizations2.jakowski.lukasz.Save.SaveGameManager;
import java.nio.file.*;
import java.util.*;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Host-local save metadata. Primary binding via hashed reconnect key; secondary via SteamID64. Nickname is display only. */
public final class KreyliSaveSeats {
    private static final Map<String, Integer> savedSeats = new LinkedHashMap<>();
    private static final Map<String, String> savedNicks = new HashMap<>();
    private static final Map<MP_Net.Conn, String> clientPrimaryIds = new IdentityHashMap<>();
    private static final Map<MP_Net.Conn, String> clientSteamIds = new IdentityHashMap<>();
    private static boolean sentId;

    public static void registerClient(MP_Net.Conn conn, String reconnectKey, long steamId) {
        if (conn == null) return;
        if (reconnectKey != null && !reconnectKey.isEmpty()) {
            String hash = KreyliReconnect.hashKey(reconnectKey);
            clientPrimaryIds.put(conn, "reconnect:" + hash);
        }
        if (steamId > 0) {
            clientSteamIds.put(conn, "steam:" + steamId);
        }
    }

    public static boolean hostMessage(MP_Net.Conn connection, Object[] message) {
        if (message.length >= 2 && "kreyli_identity".equals(message[0])) {
            String reconnectKey = message[1] instanceof String s ? s : "";
            long steamId = message.length >= 3 && message[2] instanceof Number n ? n.longValue() : 0;
            registerClient(connection, reconnectKey, steamId);
            return true;
        }
        return false;
    }

    public static void tick() {
        if (MP.isClient()) {
            if (!sentId) {
                try {
                    var f = MP.class.getDeclaredField("host");
                    f.setAccessible(true);
                    MP_Net.Conn host = (MP_Net.Conn) f.get(null);
                    if (host != null) {
                        String reconnectKey = KreyliReconnect.getOrCreateKey();
                        String testSteam = System.getProperty("kreyli.test.steamid");
                        long steamId = testSteam != null ? Long.parseLong(testSteam) : (KreyliSteam.available() ? KreyliSteam.getSteamID64() : 0);
                        host.send("kreyli_identity", reconnectKey, steamId);
                        sentId = true;
                    }
                } catch (Exception ignored) {}
            }
        } else if (MP.isHost()) {
            sentId = false;
        }
    }

    private static Path file() {
        try {
            String key = CFG.map.getActiveMapIDN() + ":" + SaveGameManager.saveTag;
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));
            return Path.of("saves", "kreyli-seats", digest + ".properties");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String getHostIdentifier() {
        String testSteam = System.getProperty("kreyli.test.steamid");
        long steamId = testSteam != null ? Long.parseLong(testSteam) : (KreyliSteam.available() ? KreyliSteam.getSteamID64() : 0);
        if (steamId > 0) return "steam:" + steamId;
        return "reconnect:" + KreyliReconnect.hashKey(KreyliReconnect.getOrCreateKey());
    }

    public static void restoreConnected() {
        if (!MP.isHost() || !MP.started) return;
        try {
            var method = MP.class.getDeclaredMethod("assign", MP_Net.Conn.class, int.class);
            method.setAccessible(true);
            var free = MP.class.getDeclaredMethod("civFree", int.class, MP_Net.Conn.class);
            free.setAccessible(true);

            String hostId = getHostIdentifier();
            Integer hostCiv = savedSeats.get(hostId);
            if (hostCiv == null) hostCiv = savedSeats.get("host");
            if (hostCiv != null && MP.pickable(hostCiv)) {
                CFG.core.getPlayer(0).setCivId(hostCiv);
            }

            for (var c : MP.clients) {
                if (c.civ > 0) continue;
                String primaryId = clientPrimaryIds.get(c);
                String steamIdKey = clientSteamIds.get(c);
                Integer civ = null;
                
                if (steamIdKey != null && savedSeats.containsKey(steamIdKey)) {
                    civ = savedSeats.get(steamIdKey);
                } else if (primaryId != null && savedSeats.containsKey(primaryId)) {
                    civ = savedSeats.get(primaryId);
                }
                // Nickname is display only and does not grant country rights (old seat.<nickname> saves are ignored).

                if (civ != null && MP.pickable(civ) && Boolean.TRUE.equals(free.invoke(null, civ, c))) {
                    method.invoke(null, c, civ);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static void save() {
        if (!MP.isHost() || !MP.started || SaveGameManager.saveTag == null) return;
        Properties p = new Properties();
        p.setProperty("shared", Boolean.toString(SharedSupport.shared));
        String hostId = getHostIdentifier();
        p.setProperty("host.id", hostId);
        p.setProperty("seat." + hostId, Integer.toString(CFG.core.getPlayer(0).getCivId()));
        p.setProperty("nick." + hostId, MP.nick);

        try {
            var f = MP.class.getDeclaredField("nickCiv");
            f.setAccessible(true);
            for (var entry : ((Map<String, Integer>) f.get(null)).entrySet()) {
                if (entry.getKey() != null && entry.getValue() > 0) {
                    p.setProperty("seat." + entry.getKey(), entry.getValue().toString());
                }
            }
        } catch (Exception ignored) {}

        for (var c : MP.clients) {
            if (c.civ > 0) {
                String clientId = clientSteamIds.get(c);
                if (clientId == null) clientId = clientPrimaryIds.get(c);
                if (clientId != null) {
                    p.setProperty("seat." + clientId, Integer.toString(c.civ));
                    if (c.nick != null) p.setProperty("nick." + clientId, c.nick);
                }
            }
        }

        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Path temp = f.resolveSibling(f.getFileName() + ".tmp");
            try (var out = Files.newOutputStream(temp)) {
                p.store(out, "Kreyli save countries");
            }
            Files.move(temp, f, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            System.err.println("Kreyli seat metadata save: " + e.getClass().getSimpleName());
        }
    }

    /** Before native onEnterInGame, which subsequently builds JOIN snapshots. */
    @SuppressWarnings("unchecked")
    public static void prepare() {
        if (!MP.isHost() || MP.started || SaveGameManager.saveTag == null) return;
        savedSeats.clear();
        savedNicks.clear();
        Properties p = new Properties();
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            try (var in = Files.newInputStream(f)) {
                p.load(in);
            }
            String hostId = p.getProperty("host.id");
            int host = Integer.parseInt(p.getProperty("hostCiv", "0"));
            if (hostId != null && savedSeats.containsKey(hostId)) {
                host = savedSeats.get(hostId);
            }
            if (host == 0) {
                for (var entry : p.stringPropertyNames()) {
                    if (entry.startsWith("seat.")) {
                        int c = Integer.parseInt(p.getProperty(entry));
                        if (c > 0) { host = c; break; }
                    }
                }
            }
            if (MP.pickable(host)) {
                CFG.core.getPlayer(0).setCivId(host);
                SharedSupport.shared = Boolean.parseBoolean(p.getProperty("shared", "false"));
            }

            for (String key : p.stringPropertyNames()) {
                if (key.startsWith("seat.")) {
                    int civ = Integer.parseInt(p.getProperty(key));
                    String seatKey = key.substring(5);
                    // Only load host or secure identifiers (reconnect:<hash>, steam:<id>); ignore old seat.<nickname>
                    if (seatKey.equals("host") || seatKey.contains(":")) {
                        if (MP.pickable(civ) || civ == host) {
                            savedSeats.put(seatKey, civ);
                        }
                    }
                } else if (key.startsWith("nick.")) {
                    savedNicks.put(key.substring(5), p.getProperty(key));
                }
            }
            var field = MP.class.getDeclaredField("nickCiv");
            field.setAccessible(true);
            ((Map<String, Integer>) field.get(null)).putAll(savedSeats);
        } catch (Exception e) {
            System.err.println("Kreyli seat metadata load: "+e.getClass().getSimpleName());
        }
    }
}
