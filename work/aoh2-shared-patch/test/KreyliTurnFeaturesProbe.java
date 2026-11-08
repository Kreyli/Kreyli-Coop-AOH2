package age.of.civilizations2.jakowski.lukasz.mp;
import age.of.civilizations2.jakowski.lukasz.*;
import age.of.civilizations2.jakowski.lukasz.Menus.NewGame.Menu_CreateNewGame;
import age.of.civilizations2.jakowski.lukasz.Messages.Gift.Message_Gift;
import aoc.kingdoms.lukasz.jakowski.AA_Game;
import com.badlogic.gdx.backends.lwjgl3.*;
import java.nio.file.*;

/** Native diplomacy acceptance while ready, actual ready UI, and unanimous skip. */
public final class KreyliTurnFeaturesProbe {
 static void require(boolean b,String message){if(!b)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  if(!Files.exists(Path.of(".coop-test-copy")))throw new IllegalStateException("Disposable copy required");
  boolean host=args[0].equals("host");Path channel=Path.of(args[1]);int port=Integer.parseInt(args[2]);
  var remote=MP.class.getDeclaredField("remoteRequested");remote.setAccessible(true);remote.setBoolean(null,true);
  new Thread(()->{try{Thread.sleep(360000);}catch(Exception ignored){}Runtime.getRuntime().halt(8);},"probe-watchdog").start();
  var config=new Lwjgl3ApplicationConfiguration();config.setWindowedMode(1280,720);config.setInitialVisible(false);config.disableAudio(true);config.setForegroundFPS(30);
  new Lwjgl3Application(new AA_Game(){int frames,phase,civ,hostCiv,startTurn,guestCiv;long guestGold,senderGold,waitStart;
   boolean exists(String name){return Files.exists(channel.resolve(name));}
   void signal(String name,String value)throws Exception{Files.writeString(channel.resolve(name),value);}
   String read(String name)throws Exception{return Files.readString(channel.resolve(name));}
   boolean playable(){return CFG.menus.getInGameView()&&MP.inputOrders();}
   public void render(){try{super.render();frames++;
    if(frames%300==0)System.out.println("TURN_FEATURES "+(host?"host":"client")+" phase="+phase+" turn="+GameCalendar.TURNID);
    if(phase==0&&frames>300&&CFG.core.getCivsSize()>2){
     MP.nick=host?"SkipHost":"SkipGuest";MP.password="test-only";MP.port=port;MP.turnSeconds=0;MP.listInLobby=false;MP.hostDirect=true;
     CFG.settingsGD.CONFIRM_NO_ORDERS=true;CFG.settingsGD.CONFIRM_END_TURN=false;
     if(host){require(MP.startHost(),"Host start");civ=1;while(CFG.core.getCiv(civ).getNumOfProvs()==0)civ++;hostCiv=civ;CFG.core.getPlayer(0).setCivId(civ);Menu_CreateNewGame.newGame();}
     else{MP.address="127.0.0.1:"+port;MP.join();}
     phase=1;
    }
    if(host){
     if(phase==1&&MP.started&&playable()){signal("ready",Integer.toString(civ));phase=2;}
     if(phase==2&&exists("guest-ready")){
      guestCiv=MP.clients.get(0).civ;guestGold=CFG.core.getCiv(guestCiv).getGold();senderGold=CFG.core.getCiv(hostCiv).getGold();
      GameManager.sendGift(guestCiv,hostCiv,10);require(CFG.core.getCiv(hostCiv).getGold()==senderGold-10,"Gift debited sender");MP.requestSync();signal("gift-sent",Long.toString(guestGold));phase=3;
     }
     if(phase==3&&exists("accepted")&&CFG.core.getCiv(guestCiv).getGold()==guestGold+10){signal("gift-applied","ok");KreyliTurns.chooseSkip();KreyliTurns.proposeSkip();startTurn=GameCalendar.TURNID;phase=4;}
     if(phase==4&&exists("cancelled")&&KreyliTurns.vote.id==0){require(GameCalendar.TURNID==startTurn,"No turn with one vote");KreyliTurns.proposeSkip();signal("second-proposal","ok");phase=5;}
     if(phase==5&&GameCalendar.TURNID==startTurn+2&&playable()&&KreyliTurns.vote.id==0){signal("host-final",CFG.core.getCiv(guestCiv).getGold()+","+GameCalendar.TURNID);MP.endTurnPressed();phase=6;waitStart=0;}
     if(phase==6&&exists("stale-sent")){if(waitStart==0)waitStart=System.currentTimeMillis();if(System.currentTimeMillis()-waitStart>2000){require(GameCalendar.TURNID==startTurn+2&&!MP.clients.get(0).ready,"Stale prior-turn ready did not advance ready host");MP.endTurnPressed();signal("stale-rejected","ok");phase=7;}}
     if(phase==7&&exists("client-pass")){MP.stop();System.out.println("TURN_FEATURES PASS host gift settlement, vote reset, stale ready rejected and two unanimous turns");Runtime.getRuntime().halt(0);}
    }else{
     if(phase==1&&MP.picking&&exists("ready")&&!MP.pickPending()){hostCiv=Integer.parseInt(read("ready"));civ=1;while(civ==hostCiv||CFG.core.getCiv(civ).getNumOfProvs()==0)civ++;MP.pick(civ);phase=2;}
     if(phase==2&&MP.myCiv==civ&&!MP.picking&&playable()){
      startTurn=GameCalendar.TURNID;CFG.core.getPlayer(CFG.PLAYER_TURN_ID).setNoOrders(true);CFG.menus.getInGameProvInfo().actionEL(0);require(MP.ready,"Actual ready button bypasses no-orders prompt");phase=20;
     }
     if(phase==20){require(CFG.menus.getInGameProvInfo().getMenuElem(0).getIsClickable(),"Ready button stays clickable for cancellation");CFG.menus.getInGameProvInfo().actionEL(0);require(!MP.ready,"Actual button cancels readiness");CFG.menus.getInGameProvInfo().actionEL(0);require(MP.ready,"Actual button re-readies");signal("guest-ready","ok");phase=3;}
     if(phase==3&&exists("gift-sent")){
      var box=CFG.core.getCiv(civ).getCivDiploGD().messageBox;for(int i=0;i<box.getMessagesSize();i++)if(box.getMessage(i) instanceof Message_Gift gift){require(MP.ready,"Recipient still ready when gift arrives");gift.onAccept(civ);require(!MP.ready,"Gift acceptance withdraws readiness");signal("accepted","ok");phase=4;break;}
     }
     if(phase==4&&exists("gift-applied")&&KreyliTurns.skipStatus().startsWith("Принять")){if(waitStart==0)waitStart=System.currentTimeMillis();require(GameCalendar.TURNID==startTurn,"No skip without guest consent");if(System.currentTimeMillis()-waitStart>3000){KreyliTurns.cancelSkip();signal("cancelled","ok");phase=5;}}
     if(phase==5&&exists("second-proposal")&&KreyliTurns.skipStatus().startsWith("Принять")){KreyliTurns.acceptSkip();phase=6;}
     if(phase==6&&exists("host-final")&&playable()){String[] parts=read("host-final").split(",");if(GameCalendar.TURNID==Integer.parseInt(parts[1])){
      require(CFG.core.getCiv(civ).getGold()==Long.parseLong(parts[0]),"Final money agrees");
      var hostField=MP.class.getDeclaredField("host");hostField.setAccessible(true);var count=MP.class.getDeclaredField("sentCmds");count.setAccessible(true);
      ((MP_Net.Conn)hostField.get(null)).send("kreyli_ready",startTurn,count.getInt(null),999999L,true);signal("stale-sent","ok");phase=7;
     }}
     if(phase==7&&exists("stale-rejected")){signal("client-pass","ok");MP.stop();System.out.println("TURN_FEATURES PASS actual ready/cancel UI, ready gift acceptance, stale ready and unanimous skip");Runtime.getRuntime().halt(0);}
    }
   }catch(Throwable error){error.printStackTrace();Runtime.getRuntime().halt(3);}}
  },config);
 }
}
