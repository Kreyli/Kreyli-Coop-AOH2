package age.of.civilizations2.jakowski.lukasz.mp;

import com.badlogic.gdx.Graphics.DisplayMode;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

public final class KreyliWindow {
    private KreyliWindow(){}

    public static void icon(Lwjgl3ApplicationConfiguration config,com.badlogic.gdx.Files.FileType type,String[] paths){
        String custom=System.getProperty("kreyli.window.icon");
        if(custom!=null&&java.nio.file.Files.isRegularFile(java.nio.file.Path.of(custom)))
            config.setWindowIcon(com.badlogic.gdx.Files.FileType.Absolute,custom);
        else config.setWindowIcon(type,paths);
    }
    public static void borderless(Lwjgl3ApplicationConfiguration config,DisplayMode mode){
        var monitor=Lwjgl3ApplicationConfiguration.getPrimaryMonitor();
        config.setWindowedMode(mode.width,mode.height);
        config.setDecorated(false);
        config.setWindowPosition(monitor.virtualX,monitor.virtualY);
    }
}
