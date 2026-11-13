package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.CFG;
import age.of.civilizations2.jakowski.lukasz.GameCalendar;
import age.of.civilizations2.jakowski.lukasz.Game.GameUpdate;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;

/** Host-owned treasury and loan settlement on a clock independent of army orders. */
public final class KreyliRealtimeEconomy {
    private static final String MESSAGE = "kreyli_economy";
    private static final long INTERVAL = 30_000_000_000L;
    private static final long FIRST_PAYOUT = 2_500_000_000L;
    private static final int MAX_BYTES = 1_048_576;
    private static final Field SENT_COMMANDS = sentCommandsField();
    private static long deadline;
    private static long sequence;
    private static long received;
    private static int receivedTurn, receivedCommands;
    private static byte[] latestBalances;

    private KreyliRealtimeEconomy() {}

    private static boolean hostEnabled() {
        return MP.isHost() && MP.started && KreyliLobby.realTime();
    }

    public static void nativePayout(GameUpdate economy) {
        if (!hostEnabled()) economy.updateCivs_Money();
    }

    public static void tick() {
        if (!hostEnabled()) return;
        long now = KreyliRealtimePause.clock();
        if (deadline == 0) deadline = now + FIRST_PAYOUT;
        if (now < deadline || !MP.inputOrders()) return;
        deadline = now + INTERVAL;

        boolean loans = false;
        int count = CFG.core.getCivsSize();
        for (int id = 1; id < count && !loans; id++) {
            var civ = CFG.core.getCiv(id);
            loans = civ.getNumOfProvs() > 0 && (civ.getLoansSize() > 0 || civ.getLoansFromCivSize() > 0);
        }
        CFG.gameUpdate.updateCivs_Money();
        long id = ++sequence;
        if (!MP.clients.isEmpty()) {
            if (count > MAX_BYTES / Long.BYTES) {
                MP.requestSync();
            } else {
                ByteBuffer packet = ByteBuffer.allocate(count * Long.BYTES);
                for (int civ = 0; civ < count; civ++) packet.putLong(CFG.core.getCiv(civ).getGold());
                byte[] balances = packet.array();
                for (var guest : MP.clients)
                    if (guest.civ > 0) guest.send(MESSAGE, GameCalendar.TURNID, id, guest.cmds, balances);
                if (loans) MP.requestSync();
            }
        }
        refreshMoney(CFG.core.getPlayer(0).getCivId());
    }

    public static boolean receive(Object[] message) {
        if (message.length == 0 || !MESSAGE.equals(message[0])) return false;
        if (!MP.isClient() || !MP.playing() || !KreyliLobby.realTime() || message.length != 5
                || !(message[1] instanceof Integer turn) || turn < GameCalendar.TURNID
                || !(message[2] instanceof Long id) || id <= received
                || !(message[3] instanceof Integer commands) || commands < sentCommands()
                || !(message[4] instanceof byte[] balances) || balances.length > MAX_BYTES
                || balances.length % Long.BYTES != 0) return true;
        if (turn == GameCalendar.TURNID && !matchesWorld(balances)) return true;
        received = id;
        receivedTurn = turn;
        receivedCommands = commands;
        latestBalances = balances;
        if (MP.inputOrders() && turn == GameCalendar.TURNID) applyBalances();
        return true;
    }

    public static long snapshotSequence() {
        return sequence;
    }

    public static void afterSnapshot(Object value) {
        if (!MP.isClient() || !KreyliLobby.realTime() || !(value instanceof Long snapshot)) return;
        if (snapshot >= received) {
            received = snapshot;
            latestBalances = null;
        } else if (receivedTurn == GameCalendar.TURNID && receivedCommands >= sentCommands()
                && latestBalances != null && matchesWorld(latestBalances)) {
            applyBalances();
        } else if (receivedTurn < GameCalendar.TURNID) {
            latestBalances = null;
        }
    }

    private static boolean matchesWorld(byte[] balances) {
        return balances.length / Long.BYTES == CFG.core.getCivsSize();
    }

    private static void applyBalances() {
        ByteBuffer packet = ByteBuffer.wrap(latestBalances);
        boolean applying = MP.applying;
        MP.applying = true;
        try {
            for (int civ = 0; packet.hasRemaining(); civ++) {
                long gold = packet.getLong();
                if (civ > 0) CFG.core.getCiv(civ).setGold(gold);
            }
        } finally {
            MP.applying = applying;
        }
        refreshMoney(MP.myCiv);
    }

    private static void refreshMoney(int civ) {
        if (civ > 0 && CFG.menus != null && CFG.menus.getInGameView()) {
            CFG.menus.updateInGameTopMoney(civ);
            CFG.setRenderO(true);
        }
    }

    private static Field sentCommandsField() {
        try {
            Field field = MP.class.getDeclaredField("sentCmds");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int sentCommands() {
        try {
            return SENT_COMMANDS.getInt(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void reset() {
        deadline = sequence = received = 0;
        receivedTurn = -1;
        receivedCommands = 0;
        latestBalances = null;
    }
}
