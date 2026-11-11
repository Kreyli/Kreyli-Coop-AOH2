package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.CFG;
import age.of.civilizations2.jakowski.lukasz.Save.SaveGameManager;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/** Host-local country reservations. Nicknames and unauthenticated Steam IDs grant no rights. */
public final class KreyliSaveSeats {
    private static final Map<String, Integer> seats = new LinkedHashMap<>();
    private static final Map<MP_Net.Conn, String> identities = new IdentityHashMap<>();
    private static final Field HOST = field("host");
    private static final Field NICK_CIV = field("nickCiv");
    private static final Method ASSIGN = method("assign", MP_Net.Conn.class, int.class);
    private static final Method CIV_FREE = method("civFree", int.class, MP_Net.Conn.class);

    private KreyliSaveSeats() {}

    private static Field field(String name) {
        try {
            Field result = MP.class.getDeclaredField(name);
            result.setAccessible(true);
            return result;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Method method(String name, Class<?>... types) {
        try {
            Method result = MP.class.getDeclaredMethod(name, types);
            result.setAccessible(true);
            return result;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The base game's nickname-based reconnect must never grant a country. */
    public static void beforeHello() {
        try {
            ((Map<?, ?>) NICK_CIV.get(null)).clear();
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void afterWelcome() {
        if (!MP.isClient()) return;
        try {
            MP_Net.Conn host = (MP_Net.Conn) HOST.get(null);
            if (host != null) host.send("kreyli_identity", KreyliReconnect.getOrCreateKey(), 0L);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean hostMessage(MP_Net.Conn connection, Object[] message) {
        if (message.length == 0 || !"kreyli_identity".equals(message[0])) return false;
        if (!MP.isHost() || connection.nick == null || !MP.clients.contains(connection)
                || message.length != 3 || !(message[1] instanceof String key)
                || !key.matches("[0-9a-f]{32}") || identities.containsKey(connection)) return true;
        identities.put(connection, "reconnect:" + KreyliReconnect.hashKey(key));
        return true;
    }

    static int unoccupiedReservations(int civ,MP_Net.Conn claimant){
        String claimantId=identities.get(claimant);
        int count=0;
        for(var seat:seats.entrySet()){
            String identity=seat.getKey();
            if(seat.getValue()==civ&&!identity.equals(claimantId)
                    &&MP.clients.stream().noneMatch(guest->guest.civ==civ&&identity.equals(identities.get(guest))))count++;
        }
        return count;
    }

    public static void assigned(MP_Net.Conn connection, int civ) {
        String identity = identities.get(connection);
        if (identity != null) seats.put(identity, civ);
    }

    public static void tick() {
        if (!MP.isHost()) return;
        identities.keySet().retainAll(MP.clients);
        restoreConnected();
    }

    public static void restoreConnected() {
        if (!MP.isHost() || !MP.started || !MP.inputOrders()) return;
        for (var connection : MP.clients) {
            if (connection.civ > 0) continue;
            Integer civ = seats.get(identities.get(connection));
            if (civ == null || !MP.pickable(civ)) continue;
            try {
                if (Boolean.TRUE.equals(CIV_FREE.invoke(null, civ, connection))) {
                    ASSIGN.invoke(null, connection, civ);
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static Path file() {
        String key = CFG.map.getActiveMapIDN() + ":" + SaveGameManager.saveTag;
        return Path.of("saves", "kreyli-seats", KreyliReconnect.hashKey(key) + ".properties");
    }

    public static void save() {
        if (!MP.isHost() || !MP.started || SaveGameManager.saveTag == null) return;
        Properties data = new Properties();
        data.setProperty("shared", Boolean.toString(SharedSupport.shared));
        data.setProperty("capacities",KreyliCountrySeats.encode());
        String marches=KreyliRealtimeMarch.storedRoutes();
        if(!marches.isEmpty())data.setProperty("realtimeMarches",marches);
        String recruits=KreyliRealtimeRecruitment.save();
        if(!recruits.isEmpty())data.setProperty("realtimeRecruits",recruits);
        data.setProperty("hostCiv", Integer.toString(CFG.core.getPlayer(0).getCivId()));
        for (var seat : seats.entrySet()) {
            if (MP.pickable(seat.getValue())) data.setProperty("seat." + seat.getKey(), seat.getValue().toString());
        }
        try {
            Path target = file();
            Files.createDirectories(target.getParent());
            Path temp = target.resolveSibling(target.getFileName() + ".tmp");
            try (var out = Files.newOutputStream(temp)) {
                data.store(out, "Kreyli save countries");
            }
            Files.move(temp, target, REPLACE_EXISTING);
        } catch (Exception e) {
            System.err.println("Kreyli seat metadata save: " + e.getClass().getSimpleName());
        }
    }

    /** Before native onEnterInGame, which subsequently builds JOIN snapshots. */
    public static void prepare() {
        if (!MP.isHost() || MP.started || SaveGameManager.saveTag == null) return;
        seats.clear();
        Properties data = new Properties();
        try {
            Path target = file();
            if (!Files.exists(target)) return;
            try (var in = Files.newInputStream(target)) {
                data.load(in);
            }
            // Older files describe the host by host.id or hostCiv. These fields belong to the
            // host's local save. Do not guess the host country from an arbitrary guest record.
            String hostKey = "seat." + data.getProperty("host.id", "host");
            int host = country(data.getProperty("hostCiv", data.getProperty(hostKey)));
            if (MP.pickable(host)) CFG.core.getPlayer(0).setCivId(host);
            SharedSupport.shared = Boolean.parseBoolean(data.getProperty("shared", "false"));
            KreyliCountrySeats.decode(data.getProperty("capacities",""));
            KreyliRealtimeMarch.restoreStoredRoutes(data.getProperty("realtimeMarches",""));
            KreyliRealtimeRecruitment.load(data.getProperty("realtimeRecruits",""));
            for (String key : data.stringPropertyNames()) {
                if (key.equals(hostKey) || !key.matches("seat\\.reconnect:[0-9a-f]{64}")) continue;
                int civ = country(data.getProperty(key));
                if (MP.pickable(civ)) seats.put(key.substring(5), civ);
            }
        } catch (Exception e) {
            System.err.println("Kreyli seat metadata load: " + e.getClass().getSimpleName());
        }
    }

    private static int country(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static void reset() {
        seats.clear();
        identities.clear();
    }
}
