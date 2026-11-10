package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.CFG;
import age.of.civilizations2.jakowski.lukasz.LangManager;
import java.lang.reflect.Field;
import java.util.Locale;

/** Use the base game's translations where a matching label exists. */
public final class KreyliText {
    private static final Field LOCALE=localeField();
    private KreyliText(){}
    private static Field localeField(){
        try{Field field=LangManager.class.getDeclaredField("locale");field.setAccessible(true);return field;}
        catch(ReflectiveOperationException e){return null;}
    }
    public static String t(String russian,String english){
        if(CFG.lang!=null&&LOCALE!=null)try{
            if(LOCALE.get(CFG.lang) instanceof Locale locale&&locale.getLanguage().equals("ru"))return russian;
        }catch(IllegalAccessException ignored){}
        return english;
    }
    public static String nativeLabel(String key,String fallback){
        if(CFG.lang==null)return fallback;
        String value=CFG.lang.get(key);
        return value==null||value.isBlank()||value.equals(key)?fallback:value;
    }
}
