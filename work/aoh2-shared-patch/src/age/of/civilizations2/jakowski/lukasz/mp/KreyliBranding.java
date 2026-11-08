package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.CFG;
import age.of.civilizations2.jakowski.lukasz.Menu;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


/** Presentation belongs to Kreyli; relay discovery accepts endpoint data only. */
public final class KreyliBranding {
    public static final String LABEL="Kreyli-Coop AOH2 · "+System.getProperty("kreyli.version","1.1.4");

    private static boolean requested;
    private static volatile List<String[]> arrived;
    private KreyliBranding(){}

    public static void mainMenu(Menu menu){
        if(menu.menuElem.size()>1){
            var singlePlayer=menu.menuElem.get(1);
            singlePlayer.setVisibleE(true);
            singlePlayer.setTextE("Одиночная игра");
            menu.updateMenuElements_IsInView();
        }
        // The website link is the last of the two upstream linkButton entries.
        if(menu.menuElem.size()>0)menu.menuElem.get(menu.menuElem.size()-1).setVisibleE(false);
    }

    public static boolean openSinglePlayer(int index){
        if(index!=1)return false;
        MP.stop();
        CFG.menus.setMenuIDWithoutAnim(age.of.civilizations2.jakowski.lukasz.View.eGAMES);
        return true;
    }

    public static List<String[]> filterRegions(List<String[]> input){
        List<String[]> result=new ArrayList<>();
        for(String[] region:input){
            String label=region[1].trim().toLowerCase(Locale.ROOT);
            if(label.equals("ru")||label.contains("russia")||label.contains("росси"))continue;
            result.add(region);
        }
        return result;
    }

    public static void discoverRegions(){
        List<String[]> next=arrived;
        if(next!=null){
            arrived=null;
            if(!next.isEmpty())MP.regions=next;
        }
        if(requested)return;
        requested=true;
        if(Boolean.getBoolean("kreyli.discovery.disabled"))return;
        MP_Net.daemon(()->{
            HttpURLConnection connection=null;
            try{
                connection=(HttpURLConnection)URI.create("https://aoh2mp.web.app/config/definitive.txt").toURL().openConnection();
                connection.setConnectTimeout(8000);connection.setReadTimeout(8000);connection.setUseCaches(false);
                try(var input=connection.getInputStream()){
                    byte[] body=input.readNBytes(16385);
                    if(body.length>16384)return;
                    // Intentionally never process upstream version, announcement or URL lines.
                    String endpoints=new String(body,StandardCharsets.UTF_8).split("\\r?\\n",2)[0];
                    List<String[]> parsed=MP.parseRegions(endpoints);
                    if(!parsed.isEmpty())arrived=parsed;
                }
            }catch(Exception ignored){
                // Direct LAN remains available when discovery is unreachable.
            }finally{if(connection!=null)connection.disconnect();}
        },"Kreyli-relay-discovery").start();
    }

    public static void drawFooter(SpriteBatch batch){
        KreyliPings.draw(batch);
        CFG.drawTextDefaultWithShadow(batch,LABEL,Math.max(6,CFG.PADD),Math.max(0,CFG.GAMEHEIGHT-CFG.TEXT_HEIGHT_DEFAULT-Math.max(6,CFG.PADD)),Color.WHITE);
    }
}



