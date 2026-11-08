package age.of.civilizations2.jakowski.lukasz.mp;
import age.of.civilizations2.jakowski.lukasz.*;
import age.of.civilizations2.jakowski.lukasz.Menus.NewGame.Menu_CreateNewGame;
import aoc.kingdoms.lukasz.jakowski.AA_Game;
import com.badlogic.gdx.backends.lwjgl3.*;
import java.nio.file.*;
public final class KreyliVisualStateProbe {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  if(!Files.exists(Path.of(".coop-test-copy")))throw new IllegalStateException("Test copy required");
  var remote=MP.class.getDeclaredField("remoteRequested");remote.setAccessible(true);remote.setBoolean(null,true);
  var config=new Lwjgl3ApplicationConfiguration();config.setWindowedMode(1280,720);config.setInitialVisible(false);config.disableAudio(true);config.setForegroundFPS(30);
  var timer=new Thread(()->{try{Thread.sleep(180000);}catch(Exception e){}Runtime.getRuntime().halt(8);});timer.setDaemon(true);timer.start();
  new Lwjgl3Application(new AA_Game(){int frame,phase;
   public void render(){try{super.render();frame++;
    if(phase==0&&frame>300&&CFG.core.getCivsSize()>2){int own=1;while(CFG.core.getCiv(own).getNumOfProvs()==0)own++;CFG.core.getPlayer(0).setCivId(own);Menu_CreateNewGame.newGame();phase=1;}
    if(phase==1&&CFG.menus.getInGameView()&&MP.inputOrders()){
     int own=CFG.core.getPlayer(0).getCivId(),other=own==1?2:1;while(other==own||CFG.core.getCiv(other).getNumOfProvs()==0)other++;
     var civ=CFG.core.getCiv(other);int from=-1;var ownCiv=CFG.core.getCiv(own);for(int i=0;i<ownCiv.getNumOfProvs();i++)if(CFG.core.getProv(ownCiv.getProvID(i)).getNeighProvincesSize()>0){from=ownCiv.getProvID(i);break;}check(from>=0,"No adjacent province");int to=CFG.core.getProv(from).getNeighProvinces(0);
     civ.newMove(from,to,1,true);CFG.SPECTATOR_MODE=false;
     String snapshot=SharedState.capture();SharedState.restore(snapshot);
     var size=CFG.core.getClass().getDeclaredField("iMoveUnits_JustDraw_AnotherArmiesSize");size.setAccessible(true);
     check(size.getInt(CFG.core)>0,"Foreign movement drawing cache missing after restore");
     check(SharedState.capture().equals(snapshot),"Army state changed in restoration");
     CFG.map.getTouchMgr().setActionMap(true);Touch.sAMD=true;TouchManager.bSMD=true;
     KreyliInputRecovery.tick();check(!CFG.map.getTouchMgr().getActionMap()&&!Touch.sAMD&&!TouchManager.bSMD,"Lost mouse-up not released");
     System.out.println("KREYLI_VISUAL_STATE: PASS movement cache and input gesture recovery");Runtime.getRuntime().halt(0);
    }
   }catch(Throwable e){e.printStackTrace();Runtime.getRuntime().halt(3);}}
  },config);
 }
}


