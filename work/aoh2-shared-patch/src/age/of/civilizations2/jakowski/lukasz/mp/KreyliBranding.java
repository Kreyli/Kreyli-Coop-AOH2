package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.CFG;
import age.of.civilizations2.jakowski.lukasz.Menu;
import age.of.civilizations2.jakowski.lukasz.AoCGame;
import age.of.civilizations2.jakowski.lukasz.Button.MenuElemUI;
import age.of.civilizations2.jakowski.lukasz.Button.NewGame.Button_InGameBox;
import age.of.civilizations2.jakowski.lukasz.TextB.Texts.TextScale;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
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
    public static final String LABEL="Kreyli-Coop AOH2 · "+System.getProperty("kreyli.version","1.1.28");
    private static final Color FOOTER_COLOR=new Color(1,1,1,.52f);

    private static final int[] creditsClicks=new int[2];
    private static final Music[] creditsMusic=new Music[2];
    private static boolean requested;
    private static volatile List<String[]> arrived;
    private KreyliBranding(){}

    public static void browserBar(Menu menu){
        MenuElemUI nickname=menu.getMenuElem(0);
        nickname.setVisibleE(false);
        nickname.setClickable(false);
        menu.menuElem.remove(5);
        menu.iMenuElemsSize=menu.menuElem.size();
        MP.browseAll=true;
        int gap=CFG.PADD;
        int contentWidth=menu.getWidthM()-gap*5;
        for(int index=1;index<=4;index++){
            MenuElemUI button=menu.getMenuElem(index);
            int left=contentWidth*(index-1)/4;
            button.setPosX(gap*index+left);
            button.setWidthE(contentWidth*index/4-left);
        }
        menu.updateMenuElements_IsInView();
        menu.updateMenuElements_IsInView_X();
    }

    public static int addCredits(List<MenuElemUI> items,int y){
        int x=AoCGame.LEFT+CFG.BUTTON_W/2;
        y+=CFG.PADD*6;
        String[] lines={"Kreyli Coop AOH2",KreyliText.t("Создатель мода: Kreyli","Mod creator: Kreyli"),
                KreyliText.t("Тестеры:","Testers:"),"-lockdown (Pin)","SpaceBodik",
                KreyliText.t("Спасибо, что играете!","Thanks for playing!")};
        for(int i=0;i<lines.length;i++){
            MenuElemUI line;
            boolean tester=i==3||i==4;
            if(tester){
                int sound=i-3;
                line=new TextScale(lines[i],x,y,.9f){
                    private final Color plainText=new Color(.78f,.78f,.78f,.7f);
                    @Override public Color getColor(boolean selected){return plainText;}
                    @Override public void actionElem(int index){creditsClicked(sound);}
                };

            }else line=new TextScale(lines[i],x,y,i==0?1f:.9f);
            line.setClickable(tester);
            items.add(line);
            y+=line.getHeightE()+CFG.PADD*2;
        }
        MenuElemUI discord=new Button_InGameBox(KreyliText.t("Discord проекта","Project Discord"),-1,x,y,CFG.BUTTON_W*2,true){
            @Override public void actionElem(int index){KreyliLobby.discord();}
        };
        items.add(discord);
        return y+discord.getHeightE()+CFG.PADD*2;
    }

    private static void creditsClicked(int sound){
        if(++creditsClicks[sound]<10)return;
        try{
            creditsClicks[sound]=0;
            stopCreditsAudio();
            if(creditsMusic[sound]==null){
                String asset=sound==0?"kreyli-credits.wav":"kreyli-spacebodik.ogg";
                creditsMusic[sound]=Gdx.audio.newMusic(Gdx.files.classpath(asset));
            }
            creditsMusic[sound].setVolume(1f);
            creditsMusic[sound].play();
        }catch(RuntimeException error){
            Gdx.app.error("Kreyli Coop AOH2","Could not play credits audio",error);
        }
    }

    public static void closeCredits(){
        java.util.Arrays.fill(creditsClicks,0);
        stopCreditsAudio();
    }
    private static void stopCreditsAudio(){
        for(Music music:creditsMusic)if(music!=null&&music.isPlaying())music.stop();
    }

    public static void mainMenu(Menu menu){
        if(menu.menuElem.size()>1){
            var singlePlayer=menu.menuElem.get(1);
            singlePlayer.setVisibleE(true);
            singlePlayer.setTextE(KreyliText.t("Одиночная игра","Single player"));
            menu.updateMenuElements_IsInView();
        }
        KreyliQuickSearch.installMainButton(menu);
        KreyliMainMenu.layout(menu);
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
        KreyliRealtimeUi.finishAutomaticTurn();
        KreyliQuickGame.draw(batch);
        if(CFG.menus!=null&&CFG.menus.getIn_MainMenu())closeCredits();

        if(CFG.menus!=null&&CFG.menus.getIn_MainMenu())KreyliTextureCache.finishStartup();
        KreyliQuickSearch.draw(batch);
        KreyliPings.draw(batch);
        CFG.drawTextDefaultWithShadow(batch,LABEL,Math.max(6,CFG.PADD),Math.max(0,CFG.GAMEHEIGHT-CFG.TEXT_HEIGHT_DEFAULT-Math.max(6,CFG.PADD)),FOOTER_COLOR);
    }
}



