package age.of.civilizations2.jakowski.lukasz.mp;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Packs existing CIM images together so cold loads do not read thousands of tiny files. */
public final class KreyliTextureCache {
    private static final Path GAME = Path.of("").toAbsolutePath().normalize();
    private static final int MAX_ENTRY = 512 * 1024;
    private static final long MAX_DIRECTORY = 40L * 1024 * 1024;
    private static final int MAX_FILES = 20_000;
    private static final Map<Path, ProvincePack> PACKS = new ConcurrentHashMap<>();
    private static final ExecutorService BUILDER = Executors.newSingleThreadExecutor(task -> {
        Thread worker = new Thread(task, "Kreyli-province-cache");
        worker.setDaemon(true);
        worker.setPriority(Thread.MIN_PRIORITY);
        return worker;
    });

    private KreyliTextureCache() {}

    public static Pixmap read(FileHandle source) {
        if (Boolean.getBoolean("kreyli.texture-cache.disabled")) return PixmapIO.readCIM(source);
        Path path = source.file().toPath().toAbsolutePath().normalize();
        if (!eligible(path)) return PixmapIO.readCIM(source);
        ProvincePack pack = PACKS.computeIfAbsent(path.getParent(), ProvincePack::new);
        byte[] cached = pack.lookup(path);
        if (cached != null) {
            try {
                return PixmapIO.readCIM(new FileHandle(source.file()) {
                    @Override public InputStream read() { return new ByteArrayInputStream(cached); }
                });
            } catch (RuntimeException corrupt) {
                pack.invalidate();
            }
        }
        return PixmapIO.readCIM(source);
    }

    /** Run after the game reaches the main menu. The first load always uses the real assets. */
    public static void finishStartup() {
        for (ProvincePack pack : PACKS.values()) {
            if (pack.beginBuild()) BUILDER.execute(pack::build);
        }
    }

    private static boolean eligible(Path source) {
        if (!source.startsWith(GAME)) return false;
        Path relative = GAME.relativize(source);
        return relative.getNameCount() == 7
                && relative.getName(0).toString().equals("map")
                && relative.getName(2).toString().equals("data")
                && relative.getName(3).toString().equals("scales")
                && relative.getName(4).toString().equals("provinces")
                && numeric(relative.getName(5)) && numeric(relative.getFileName());
    }

    private static boolean numeric(Path name) {
        String text = name.toString();
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) < '0' || text.charAt(i) > '9') return false;
        }
        return !text.isEmpty();
    }

    private static BasicFileAttributes attributes(Path source) throws IOException {
        return Files.readAttributes(source, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }

    private static String fingerprint(BasicFileAttributes source) {
        return source.size() + ":" + source.lastModifiedTime().toMillis() + ":" + source.creationTime().toMillis();
    }

    private static Path packPath(Path sourceDirectory) {
        try {
            byte[] key = MessageDigest.getInstance("SHA-256")
                    .digest(sourceDirectory.toString().getBytes(StandardCharsets.UTF_8));
            return Path.of(System.getProperty("user.home"), ".kreyli", "province-cache",
                    HexFormat.of().formatHex(key) + ".zip");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static final class ProvincePack {
        private final Path sourceDirectory;
        private final Path archive;
        private ZipFile zip;
        private boolean opened;
        private boolean needsBuild;
        private boolean building;

        ProvincePack(Path sourceDirectory) {
            this.sourceDirectory = sourceDirectory;
            this.archive = packPath(sourceDirectory);
        }

        synchronized byte[] lookup(Path source) {
            try {
                if (!opened) {
                    opened = true;
                    if (Files.isRegularFile(archive, LinkOption.NOFOLLOW_LINKS)) zip = new ZipFile(archive.toFile());
                }
                ZipEntry entry = zip == null ? null : zip.getEntry(source.getFileName().toString());
                if (entry != null) {
                    BasicFileAttributes current = attributes(source);
                    if (current.isRegularFile() && current.size() <= MAX_ENTRY
                            && current.size() == entry.getSize()
                            && fingerprint(current).equals(entry.getComment())) {
                        try (InputStream stream = zip.getInputStream(entry)) {
                            byte[] bytes = stream.readNBytes((int) entry.getSize() + 1);
                            CRC32 crc = new CRC32();
                            crc.update(bytes);
                            if (bytes.length == entry.getSize() && crc.getValue() == entry.getCrc()) return bytes;
                        }
                    }
                }
            } catch (IOException | SecurityException cacheUnavailable) {
                // The normal asset loader retains its original error handling and repair behavior.
            }
            needsBuild = true;
            return null;
        }

        synchronized void invalidate() { needsBuild = true; }

        synchronized boolean beginBuild() {
            if (!needsBuild || building) return false;
            building = true;
            return true;
        }

        void build() {
            Path temp = null;
            try {
                Files.createDirectories(archive.getParent());
                temp = Files.createTempFile(archive.getParent(), "province-", ".zip.tmp");
                write(temp);
                synchronized (this) {
                    if (zip != null) { zip.close(); zip = null; }
                    opened = false;
                    Files.move(temp, archive, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    needsBuild = false;
                }
            } catch (IOException | SecurityException cacheUnavailable) {
                // The cache is optional and may be unavailable on read-only installations.
                if (Boolean.getBoolean("kreyli.texture-cache.debug")) cacheUnavailable.printStackTrace();
            } finally {
                if (temp != null) {
                    try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
                }
                synchronized (this) { building = false; }
            }
        }

        private void write(Path temp) throws IOException {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(sourceDirectory);
                    ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(temp))) {
                int count = 0;
                long total = 0;
                for (Path file : files) {
                    if (!numeric(file.getFileName())) continue;
                    BasicFileAttributes before = attributes(file);
                    if (!before.isRegularFile() || before.size() <= 0 || before.size() > MAX_ENTRY) continue;
                    if (++count > MAX_FILES || (total += before.size()) > MAX_DIRECTORY) {
                        throw new IOException("Province cache exceeds the local size limit");
                    }
                    byte[] bytes;
                    try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                        bytes = input.readNBytes((int) before.size() + 1);
                    }
                    if (bytes.length != before.size() || !fingerprint(before).equals(fingerprint(attributes(file)))) continue;
                    CRC32 crc = new CRC32();
                    crc.update(bytes);
                    ZipEntry entry = new ZipEntry(file.getFileName().toString());
                    entry.setMethod(ZipEntry.STORED);
                    entry.setSize(bytes.length);
                    entry.setCrc(crc.getValue());
                    entry.setComment(fingerprint(before));
                    output.putNextEntry(entry);
                    output.write(bytes);
                    output.closeEntry();
                }
            }
        }
    }
}
