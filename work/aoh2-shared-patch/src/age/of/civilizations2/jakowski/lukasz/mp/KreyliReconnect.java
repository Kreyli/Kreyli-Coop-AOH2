package age.of.civilizations2.jakowski.lukasz.mp;

import java.nio.file.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Client-local persistent reconnect key and SHA-256 hashing for LAN/Radmin/Hamachi. */
public final class KreyliReconnect {
    private static String reconnectKey;
    private KreyliReconnect() {}
    public static synchronized String getOrCreateKey() {
        String testKey = System.getProperty("kreyli.test.reconnect");
        if (testKey != null) return testKey;
        if (reconnectKey != null && !reconnectKey.isEmpty()) return reconnectKey;
        try {
            Path path = Path.of("saves", "kreyli-reconnect.properties");
            Properties p = new Properties();
            if (Files.exists(path)) {
                try (var in = Files.newInputStream(path)) {
                    p.load(in);
                }
                reconnectKey = p.getProperty("key");
            }
            if (reconnectKey == null || !reconnectKey.matches("[0-9a-f]{32}")) {
                reconnectKey = UUID.randomUUID().toString().replace("-", "");
                p.setProperty("key", reconnectKey);
                Files.createDirectories(path.getParent());
                try (var out = Files.newOutputStream(path)) {
                    p.store(out, "Kreyli reconnect key");
                }
            }
        } catch (Exception e) {
            if (reconnectKey == null || !reconnectKey.matches("[0-9a-f]{32}")) {
                reconnectKey = UUID.randomUUID().toString().replace("-", "");
            }
        }
        return reconnectKey;
    }

    public static String hashKey(String key) {
        if (key == null) return "";
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot hash reconnect key", e);
        }
    }
}
