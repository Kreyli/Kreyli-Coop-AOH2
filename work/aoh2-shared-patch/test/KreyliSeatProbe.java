package age.of.civilizations2.jakowski.lukasz.mp;
import age.of.civilizations2.jakowski.lukasz.*;
import age.of.civilizations2.jakowski.lukasz.Menus.NewGame.Menu_CreateNewGame;
import aoc.kingdoms.lukasz.jakowski.AA_Game;
import com.badlogic.gdx.backends.lwjgl3.*;
import java.nio.file.*;
import java.net.*;
public final class KreyliSeatProbe {
 static void check(boolean yes,String why){if(!yes)throw new AssertionError(why);}
 static void started(boolean value)throws Exception{var f=MP.class.getDeclaredField("started");f.setAccessible(true);f.setBoolean(null,value);}
 public static void main(String[] args)throws Exception{
  if(!Files.exists(Path.of(".coop-test-copy")))throw new IllegalStateException("Disposable only");
  var r=MP.class.getDeclaredField("remoteRequested");r.setAccessible(true);r.setBoolean(null,true);
  var config=new Lwjgl3ApplicationConfiguration();config.setWindowedMode(1000,700);config.setInitialVisible(false);config.disableAudio(true);config.setForegroundFPS(30);
  var watchdog=new Thread(()->{try{Thread.sleep(180000);}catch(Exception e){}Runtime.getRuntime().halt(8);});watchdog.setDaemon(true);watchdog.start();
  new Lwjgl3Application(new AA_Game(){int frame,phase,country;
   public void render(){try{super.render();frame++;
    if(phase==0&&frame>=300){MP.nick="SeatHost";MP.hostDirect=true;MP.listInLobby=false;try(var ss=new ServerSocket(0)){MP.port=ss.getLocalPort();}check(MP.startHost(),"Host");country=1;while(CFG.core.getCiv(country).getNumOfProvs()==0)country++;CFG.core.getPlayer(0).setCivId(country);Menu_CreateNewGame.newGame();phase=1;}
    if(phase==1&&MP.started&&MP.inputOrders()){
     System.setProperty("kreyli.nick","  Игрок из лаунчера  ");MP.nick="";KreyliIdentity.apply();check(MP.nick.equals("Игрок из лаунчера"),"Startup nickname");System.clearProperty("kreyli.nick");MP.nick="";KreyliIdentity.apply();check(!MP.nick.isEmpty(),"Empty fallback");MP.nick="SeatHost";
     SharedSupport.shared=true;MP_Save.saveToDisk();KreyliSaveSeats.save();SharedSupport.shared=false;CFG.core.getPlayer(0).setCivId(0);started(false);KreyliSaveSeats.prepare();check(CFG.core.getPlayer(0).getCivId()==country,"Host country restored");check(SharedSupport.shared,"Shared mode restored");started(true);MP.stop();System.out.println("KREYLI_SEATS: PASS native startup nickname, nonempty fallback, persisted host country and shared mode restore");Runtime.getRuntime().halt(0);
    }
   }catch(Throwable e){e.printStackTrace();Runtime.getRuntime().halt(3);}}
  },config);
 }
}

