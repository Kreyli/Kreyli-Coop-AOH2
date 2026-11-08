package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.CFG;
import age.of.civilizations2.jakowski.lukasz.Menu;
import age.of.civilizations2.jakowski.lukasz.View;
import com.badlogic.gdx.Gdx;

/** The launcher owns the nickname; the game presents it without a second editor. */
public final class KreyliIdentity {
    public static final String DISCORD="https://discord.gg/zGdxgSvW4";
    private static String sessionBase="",sessionAlias="";
    private KreyliIdentity(){}
    public static String cleanNickname(String value){
        if(value==null)return "";
        value=value.replaceAll("[\\p{Cntrl};]","").trim();
        return value.length()>24?value.substring(0,24):value;
    }
    public static void apply(){
        String fromLauncher=cleanNickname(System.getProperty("kreyli.nick"));
        if(!fromLauncher.isEmpty())MP.nick=fromLauncher.equals(sessionBase)&&!sessionAlias.isEmpty()?sessionAlias:fromLauncher;
        else MP.nick=cleanNickname(MP.nick);
        if(MP.nick.isEmpty())MP.nick="Player";
    }
    /** Resolve identical display names only after validating native credentials and ban list. */
    public static void beforeHello(MP_Net.Conn conn,Object[] message){
        if(message.length!=4||!(message[1] instanceof String version)||!(message[2] instanceof String raw)||!(message[3] instanceof String password))return;
        try{
            var v=MP.class.getDeclaredField("VERSION");v.setAccessible(true);
            var k=MP.class.getDeclaredField("kicked");k.setAccessible(true);
            String base=MP.clean(raw,24);
            String expected=String.valueOf(v.get(null));
            if(!(version.equals(expected)||version.equals(expected+"|kreyli-coop-3"))||!MP.password.equals(password)||((java.util.Set<?>)k.get(null)).contains(base.toLowerCase()))return;
            if(base.isEmpty())base="Player";
            String chosen=base;
            for(int i=2;taken(chosen);i++){
                String suffix=" ("+i+")";chosen=base.substring(0,Math.min(base.length(),24-suffix.length()))+suffix;
                if(i>100)return;
            }
            if(!chosen.equals(raw)){message[2]=chosen;conn.send("kreyli_assigned_nick",chosen);}
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static boolean taken(String name){
        if(name.equalsIgnoreCase(MP.nick))return true;
        for(var c:MP.clients)if(c.nick!=null&&name.equalsIgnoreCase(c.nick))return true;
        return false;
    }
    public static boolean clientMessage(Object[] message){
        if(message.length==0||!"kreyli_assigned_nick".equals(message[0]))return false;
        if(!MP.isClient()||message.length!=2||!(message[1] instanceof String value)||value.isEmpty()||!value.equals(cleanNickname(value)))return true;
        sessionBase=cleanNickname(System.getProperty("kreyli.nick"));sessionAlias=value;MP.nick=value;
        MP.toast("Имя уже занято: для этой сессии используется "+value,false);return true;
    }
    public static Menu entry(){
        MP.loadSettings();apply();
        if(MP.nick.isEmpty())MP.nick="Player";
        return new MP_Form("Kreyli Coop AOH2",new String[]{MP.nick},new String[]{"Подключиться","Создать лобби","Список серверов","Discord проекта","Назад"},Math.min(CFG.GAMEWIDTH-CFG.PADD*8,Math.max(520,CFG.CIV_INFO_MENU_WIDTH*2))){
            String rowText(int row,String value){return "Игрок: "+MP.nick;}
            void onRow(int row){}
            void onButton(int button){
                if(button==4){MP_UI.open(View.eMAINMENU);return;}
                if(button==3){Gdx.net.openURI(DISCORD);return;}
                apply();MP.saveSettings();
                if(button==1){SharedHostForm.openRoom();return;}
                if(button==2)MP.refreshServers();
                MP_UI.open(button==0?View.eMP_JOIN:View.eMP_BROWSER);
            }
        };
    }
}





