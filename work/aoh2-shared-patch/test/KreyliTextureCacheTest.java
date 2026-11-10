package age.of.civilizations2.jakowski.lukasz.mp;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.GdxNativesLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Map;

/** Run from an empty disposable working directory, with user.home set to that fixture. */
public final class KreyliTextureCacheTest {
    public static void main(String[] args) throws Exception {
        Path root = Path.of("").toAbsolutePath().normalize();
        if (!Path.of(System.getProperty("user.home")).toAbsolutePath().normalize().equals(root)
                || Files.exists(root.resolve("game.jar")) || Files.exists(root.resolve("map"))) {
            throw new IllegalStateException("Empty disposable texture-cache fixture required");
        }
        GdxNativesLoader.load();
        Path source = root.resolve("map/test/data/scales/provinces/1/42");
        Files.createDirectories(source.getParent());
        FileHandle handle = new FileHandle(source.toFile());
        write(handle, 0xff0000ff);
        requirePixel(handle, 0xff0000ff);
        KreyliTextureCache.finishStartup();

        var packs = KreyliTextureCache.class.getDeclaredField("PACKS");
        packs.setAccessible(true);
        Object pack = ((Map<?, ?>) packs.get(null)).get(source.getParent());
        var lookup = pack.getClass().getDeclaredMethod("lookup", Path.class);
        lookup.setAccessible(true);
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (lookup.invoke(pack, source) == null) {
            if (System.nanoTime() > deadline) throw new AssertionError("Province pack did not become readable");
            Thread.sleep(25);
        }
        requirePixel(handle, 0xff0000ff);

        write(handle, 0x00ff00ff);
        Files.setLastModifiedTime(source, FileTime.fromMillis(System.currentTimeMillis() + 2000));
        if (lookup.invoke(pack, source) != null) throw new AssertionError("Outdated texture was accepted");
        requirePixel(handle, 0x00ff00ff);
        System.out.println("KREYLI_TEXTURE_CACHE: PASS native read, persistent pack, stale-source fallback");
    }

    private static void write(FileHandle file, int rgba) {
        Pixmap pixmap = new Pixmap(2, 2, Pixmap.Format.RGBA8888);
        try {
            pixmap.setColor(rgba);
            pixmap.fill();
            PixmapIO.writeCIM(file, pixmap);
        } finally {
            pixmap.dispose();
        }
    }

    private static void requirePixel(FileHandle file, int expected) {
        Pixmap loaded = KreyliTextureCache.read(file);
        try {
            if (loaded.getPixel(0, 0) != expected) throw new AssertionError("Incorrect texture pixel");
        } finally {
            loaded.dispose();
        }
    }
}
