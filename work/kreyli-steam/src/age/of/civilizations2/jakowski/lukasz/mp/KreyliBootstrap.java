package age.of.civilizations2.jakowski.lukasz.mp;

/** Initialize Steam before the game creates its OpenGL window. */
public final class KreyliBootstrap {
    public static void main(String[] args)throws Exception{
        KreyliSteam.initialize(args);
        Class.forName("aoc.kingdoms.lukasz.jakowski.desktop.DesktopLauncher").getMethod("main",String[].class).invoke(null,(Object)new String[0]);
    }
}
