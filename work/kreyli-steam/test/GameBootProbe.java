package age.of.civilizations2.jakowski.lukasz.mp;
import age.of.civilizations2.jakowski.lukasz.*;
import aoc.kingdoms.lukasz.jakowski.AA_Game;
import com.badlogic.gdx.backends.lwjgl3.*;
import java.nio.file.*;
/** Full assets/runtime/Steam boot check; no messages or invites are sent. */
public final class GameBootProbe {
 public static void main(String[] args)throws Exception {
  if(!Files.exists(Path.of(".kreyli-install")))throw new IllegalStateException("Isolated package required");
  KreyliSteam.initialize(new String[0]);
  var remote=MP.class.getDeclaredField("remoteRequested");remote.setAccessible(true);remote.setBoolean(null,true);
  var config=new Lwjgl3ApplicationConfiguration();config.setTitle("Kreyli Coop AOH2 — проверка");config.setWindowedMode(1280,720);config.disableAudio(true);config.setForegroundFPS(30);
  Thread timer=new Thread(()->{try{Thread.sleep(180000);}catch(Exception ignored){}Runtime.getRuntime().halt(4);});timer.setDaemon(true);timer.start();
  new Lwjgl3Application(new AA_Game(){int frame;
   @Override public void render(){try{super.render();frame++;if(frame%30==0)System.out.println("BOOT_FRAME="+frame);
    if(frame==300){
     if(Boolean.getBoolean("kreyli.test.invite")){
      MP.listInLobby=false;MP.hostDirect=false;MP.password="private-probe";
      MP.regions=new java.util.ArrayList<>();MP.regions.add(new String[]{"188.34.198.119:8766","EU Central"});MP.regionName="EU Central";
      if(!MP.startHost())throw new AssertionError("Relay host failed");MP_UI.open(View.eMP_HOST);
     }else MP_UI.open(View.eMP);
    }
    if(frame==450&&Boolean.getBoolean("kreyli.test.invite")){
     var field=KreyliSteam.class.getDeclaredField("lobby");field.setAccessible(true);var id=(com.codedisaster.steamworks.SteamID)field.get(null);
     if(id==null)throw new AssertionError("Steam lobby not created: "+KreyliSteam.status());
     var mmField=KreyliSteam.class.getDeclaredField("matchmaking");mmField.setAccessible(true);var mm=(com.codedisaster.steamworks.SteamMatchmaking)mmField.get(null);
     if(mm.getLobbyMemberLimit(id)!=2)throw new AssertionError("Steam lobby limit");
     KreyliSteam.inviteFriends();
    }
    if(frame>=480&&Boolean.getBoolean("kreyli.test.invite")&&KreyliSteam.overlayVisible()){
     System.out.println("KREYLI_STEAM_INVITE: PASS real Steam lobby limit2, invite overlay activated; no invitation sent");MP.stop();KreyliSteam.shutdown();Runtime.getRuntime().halt(0);
    }
    if(frame==450&&!Boolean.getBoolean("kreyli.test.invite")){
     System.out.println("KREYLI_BOOT: PASS full game rendered, Steam="+KreyliSteam.available()+", overlay="+KreyliSteam.overlayEnabled()+", nick="+MP.nick);
     var pix=com.badlogic.gdx.utils.ScreenUtils.getFrameBufferPixmap(0,0,1280,720);
     com.badlogic.gdx.graphics.PixmapIO.writePNG(com.badlogic.gdx.Gdx.files.local("kreyli-boot.png"),pix,6,true);pix.dispose();
     KreyliSteam.shutdown();Runtime.getRuntime().halt(0);
    }
   }catch(Throwable error){error.printStackTrace();KreyliSteam.shutdown();Runtime.getRuntime().halt(3);}}
  },config);
 }
}
