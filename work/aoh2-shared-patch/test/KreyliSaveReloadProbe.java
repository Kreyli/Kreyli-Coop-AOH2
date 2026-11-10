package age.of.civilizations2.jakowski.lukasz.mp;
import age.of.civilizations2.jakowski.lukasz.*;
import age.of.civilizations2.jakowski.lukasz.Save.SaveGameManager;
import age.of.civilizations2.jakowski.lukasz.Menus.Load.Menu_LoadSave;
import age.of.civilizations2.jakowski.lukasz.Menus.NewGame.Menu_CreateNewGame;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.*;
import aoc.kingdoms.lukasz.jakowski.AA_Game;
import java.nio.file.*;
import java.util.*;
import org.lwjgl.glfw.GLFW;
public final class KreyliSaveReloadProbe {
 static void require(boolean value,String why){if(!value)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  if(!Files.exists(Path.of(".coop-test-copy")))throw new IllegalStateException("Disposable only");
  boolean host=args[0].equals("host");Path channel=Path.of(args[1]);int port=Integer.parseInt(args[2]);
  var r=MP.class.getDeclaredField("remoteRequested");r.setAccessible(true);r.setBoolean(null,true);
  var watch=new Thread(()->{try{Thread.sleep(420000);}catch(Exception e){}Runtime.getRuntime().halt(8);});watch.setDaemon(true);watch.start();
  var config=new Lwjgl3ApplicationConfiguration();config.setWindowedMode(1000,700);config.setInitialVisible(false);config.disableAudio(true);config.setForegroundFPS(30);config.setIdleFPS(15);
  new Lwjgl3Application(new AA_Game(){int phase,frames,hostCiv,guestCiv,turn;long wait;String tag;boolean restored;
   void signal(String name,String value)throws Exception{Files.writeString(channel.resolve(name),value);}
   boolean exists(String name){return Files.exists(channel.resolve(name));}
   String read(String name)throws Exception{return Files.readString(channel.resolve(name));}
   boolean playable(){return CFG.menus.getInGameView()&&MP.inputOrders();}
   void settings(){System.setProperty("kreyli.nick","ReloadPlayer");MP.nick="ReloadPlayer";KreyliIdentity.apply();MP.password="reload-test";MP.port=port;MP.turnSeconds=0;MP.hostDirect=true;MP.listInLobby=false;}
   public void render(){try{super.render();frames++;
    if(frames%300==0)System.out.println("RELOAD "+(host?"host":"guest")+" phase="+phase+" turn="+GameCalendar.TURNID);
    if(phase==0&&frames>300&&CFG.core.getCivsSize()>2){
     settings();
     if(host){
      require(MP.startHost(),"Host");
      MP_UI.open(View.eMP_HOST);
      KreyliLobby.toggleReady();
      require(KreyliLobby.countdown()==5&&!KreyliLobby.gate.released,"Solo countdown starts at five");
      phase=-1;
     }else{MP.address="127.0.0.1:"+port;MP.join();phase=1;}
    }
    if(host){
     if(phase==-1&&KreyliLobby.gate.released){
      require(CFG.menus.getInGamesMenu(),"Solo countdown opens campaign selector");
      hostCiv=1;
      while(!MP.pickable(hostCiv))hostCiv++;
      CFG.core.getPlayer(0).setCivId(hostCiv);
      Menu_CreateNewGame.newGame();phase=1;
     }
     if(phase==1&&MP.started&&playable()){signal("host-ready",Integer.toString(hostCiv));phase=2;}
     if(phase==2&&exists("picked")&&MP.clients.size()==1&&MP.clients.get(0).civ>0){guestCiv=MP.clients.get(0).civ;MP_Save.saveToDisk();tag=SaveGameManager.saveTag;signal("saved",tag);phase=3;}
     if(phase==3&&MP.clients.isEmpty()){
      MP.stop();settings();require(MP.startHost(),"Restart host");
      String[] saves=Gdx.files.local("saves/games/"+CFG.map.getFileActiveMapPath()+"Age_of_Civilizations").readString().split(";");int index=Arrays.asList(saves).indexOf(tag);require(index>=0,"Saved game appears in list");
      Menu_LoadSave.loadNow(index);Menu_LoadSave.startLoadedGame("Reload test");phase=4;
     }
     if(phase==4&&MP.started&&playable()){
      require(CFG.core.getPlayer(0).getCivId()==hostCiv,"Host restored country");require(tag.equals(SaveGameManager.saveTag),"Actual save loaded");signal("reloaded",Integer.toString(guestCiv));phase=5;
     }
     if(phase==5&&exists("guest-ready")){if(wait==0){wait=System.currentTimeMillis();turn=GameCalendar.TURNID;}require(GameCalendar.TURNID==turn,"No turn before host vote");if(System.currentTimeMillis()-wait>=3000){MP.endTurnPressed();phase=6;}}
     if(phase==6&&GameCalendar.TURNID==turn+1&&playable()){signal("host-next",CFG.core.getCiv(guestCiv).getGold()+","+GameCalendar.TURNID);phase=7;}
     if(phase==7&&exists("pass")){MP.stop();System.out.println("RELOAD: PASS actual save load, host country and guest rejoin assignment, late readiness");Runtime.getRuntime().halt(0);}
    }else{
     if(phase==1&&MP.picking&&exists("host-ready")){hostCiv=Integer.parseInt(read("host-ready"));guestCiv=1;while(guestCiv==hostCiv||!MP.pickable(guestCiv))guestCiv++;KreyliCountrySeats.submit(guestCiv,1);phase=2;}
     if(phase==2&&MP.myCiv==guestCiv&&playable()){
      signal("picked",Integer.toString(guestCiv));
      if(exists("saved")){MP.stop();signal("left","ok");phase=3;}
     }
     if(phase==3&&exists("reloaded")){settings();MP.address="127.0.0.1:"+port;MP.join();phase=4;}
     if(phase==4&&MP.myCiv>0&&playable()){
      require(MP.nick.equals("ReloadPlayer (2)"),"Duplicate nickname negotiated and stable after rejoin");require(MP.myCiv==guestCiv,"Guest automatically assigned saved country without pick");require(!MP.picking,"No country picker after rejoin");turn=GameCalendar.TURNID;MP.endTurnPressed();require(MP.ready,"Ready before minimize");
      phase=5;var window=((Lwjgl3Graphics)Gdx.graphics).getWindow();window.setVisible(true);window.iconifyWindow();wait=System.currentTimeMillis();
      var resume=new Thread(()->{try{Thread.sleep(750);Gdx.app.postRunnable(()->{try{require(GLFW.glfwGetWindowAttrib(window.getWindowHandle(),GLFW.GLFW_ICONIFIED)==GLFW.GLFW_TRUE,"Real test window minimized");signal("guest-ready","ok");}catch(Throwable e){e.printStackTrace();Runtime.getRuntime().halt(3);}});Thread.sleep(8000);long deadline=System.currentTimeMillis()+30000;while(!exists("host-next")&&System.currentTimeMillis()<deadline)Thread.sleep(100);Gdx.app.postRunnable(()->{try{require(exists("host-next"),"Host processed turn before restore");require(GLFW.glfwGetWindowAttrib(window.getWindowHandle(),GLFW.GLFW_ICONIFIED)==GLFW.GLFW_TRUE,"Host advanced while guest iconified");signal("advanced-minimized","ok");window.restoreWindow();window.setVisible(false);restored=true;}catch(Throwable e){e.printStackTrace();Runtime.getRuntime().halt(3);}});}catch(Exception e){Runtime.getRuntime().halt(3);}},"test-restore");resume.setDaemon(true);resume.start();
     }
     if(phase==5){var window=((Lwjgl3Graphics)Gdx.graphics).getWindow();
      if(restored&&exists("host-next")&&playable()&&GameCalendar.TURNID==turn+1&&!MP.ready){String[] expected=read("host-next").split(",");require(CFG.core.getCiv(guestCiv).getGold()==Long.parseLong(expected[0]),"Post-turn gold");signal("pass","ok");MP.stop();System.out.println("RELOAD: PASS auto country restore and actual minimize/restore then next turn");Runtime.getRuntime().halt(0);}
     }
    }
   }catch(Throwable e){e.printStackTrace();Runtime.getRuntime().halt(3);}}
  },config);
 }
}




