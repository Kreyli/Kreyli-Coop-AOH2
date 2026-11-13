package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.CFG;
import java.lang.reflect.Field;

/** Assertions called inside the native turn probe, on its render thread. */
public final class KreyliChooserAnimationProbe {
    private static long cityTime;
    private static int source;
    private static boolean captured;
    private static Field field(String name)throws Exception{
        Field field=CFG.core.getClass().getDeclaredField(name);field.setAccessible(true);return field;
    }
    public static void beforeTurn()throws Exception{
        if(!CFG.chooseProvinceMode)throw new AssertionError("Movement chooser must be open");
        source=CFG.core.getActiveProvID();
        cityTime=field("lTIME_HIGHLIGHTED_CITIES").getLong(CFG.core);
        captured=true;
    }
    public static void afterTurn()throws Exception{
        if(!captured||!CFG.chooseProvinceMode||CFG.core.getActiveProvID()!=source)
            throw new AssertionError("Movement chooser changed during background turn");
        if(field("lTIME_HIGHLIGHTED_CITIES").getLong(CFG.core)!=cityTime)
            throw new AssertionError("Movement destination city fade restarted at background turn");
        if(field("HIGHLIGHTED_CITIES_DISABLE_ANIMATION").getBoolean(CFG.core))
            throw new AssertionError("Movement destinations are fading out after background turn");
        captured=false;
        System.out.println("KREYLI_CHOOSER_ANIMATION: PASS preserved selection and city fade epoch");
    }
}
