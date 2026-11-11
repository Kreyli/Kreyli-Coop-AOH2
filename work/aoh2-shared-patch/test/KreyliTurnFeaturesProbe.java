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
 static int[] hudControl(String action)throws Exception{
  var field=KreyliTurnHud.class.getDeclaredField("hits");
  field.setAccessible(true);
  for(var entry:((java.util.Map<?,?>)field.get(null)).entrySet()){
   if(!entry.getKey().toString().equals(action))continue;
   Object box=entry.getValue();
   int[] bounds=new int[4];
   String[] names={"x","y","width","height"};
   for(int i=0;i<bounds.length;i++){
    var accessor=box.getClass().getDeclaredMethod(names[i]);
    accessor.setAccessible(true);
    bounds[i]=(Integer)accessor.invoke(box);
   }
   return bounds;
  }
  return null;
 }
 static void pressHud(String action)throws Exception{
  int[] bounds=hudControl(action);
  require(bounds!=null,"Missing HUD button: "+action);
  int x=bounds[0]+bounds[2]/2,y=bounds[1]+bounds[3]/2;
  require(KreyliTurnHud.touchDown(x,y,0,0)&&KreyliTurnHud.touchUp(x,y,0,0),"HUD consumes click: "+action);
 }

 public static void main(String[] args)throws Exception{
  if(!Files.exists(Path.of(".coop-test-copy")))throw new IllegalStateException("Disposable copy required");
  boolean host=args[0].equals("host");Path channel=Path.of(args[1]);int port=Integer.parseInt(args[2]);
  var remote=MP.class.getDeclaredField("remoteRequested");remote.setAccessible(true);remote.setBoolean(null,true);
  new Thread(()->{try{Thread.sleep(480000);}catch(InterruptedException ignored){return;}Runtime.getRuntime().halt(8);},"probe-watchdog").start();
  var config=new Lwjgl3ApplicationConfiguration();config.setWindowedMode(1280,720);config.setInitialVisible(false);config.disableAudio(true);config.setForegroundFPS(30);
  new Lwjgl3Application(new AA_Game(){int frames,phase,civ,hostCiv,startTurn,guestCiv,autoCountry,autoSettledFrames;long guestGold,senderGold,waitStart;
   boolean exists(String name){return Files.exists(channel.resolve(name));}
   void signal(String name,String value)throws Exception{Files.writeString(channel.resolve(name),value);}
   String read(String name)throws Exception{return Files.readString(channel.resolve(name));}
   boolean playable(){return CFG.menus.getInGameView()&&MP.inputOrders();}
   int marchFrom=-1,marchTo=-1,marchSource,marchDestination,selectedProvince,recruitProvince,recruitArmyBefore;
   long marchIssued;
   void startMarch(){
    var country=CFG.core.getCiv(hostCiv);
    for(int i=0;i<country.getNumOfProvs()&&marchFrom<0;i++){
     int from=country.getProvID(i);
     var source=CFG.core.getProv(from);
     if(source.getSeaProv())continue;
     for(int j=0;j<source.getNeighProvincesSize();j++){
      int to=source.getNeighProvinces(j);
      var target=CFG.core.getProv(to);
      if(!target.getSeaProv()&&target.getCivId()==hostCiv){marchFrom=from;marchTo=to;break;}
     }
    }
    require(marchFrom>=0,"Find an adjacent friendly land route");
    CFG.core.getProv(marchFrom).updateArmy4(hostCiv,50);
    marchDestination=CFG.core.getProv(marchTo).getArmyCivID1(hostCiv);
    require(CFG.gameAction.moveArmyAction(marchFrom,marchTo,10,hostCiv,false,true),"Native order queues the march");
    require(CFG.core.getProv(marchFrom).getArmyCivID1(hostCiv)==40,"Order reserves troops at departure");
    marchIssued=System.nanoTime();
   }
   void checkRealtimeBattle()throws Exception{
    var country=CFG.core.getCiv(hostCiv);
    for(int i=0;i<country.getNumOfProvs();i++){
     int from=country.getProvID(i);
     var origin=CFG.core.getProv(from);
     if(origin.getSeaProv())continue;
     for(int j=0;j<origin.getNeighProvincesSize();j++){
      int to=origin.getNeighProvinces(j);
      var target=CFG.core.getProv(to);
      int defender=target.getCivId();
      if(target.getSeaProv()||defender<=0||defender==hostCiv||defender==guestCiv
              ||CFG.core.getCivsInAlliance(hostCiv,defender)||CFG.core.getMilitaryAccess(hostCiv,defender)>0
              ||country.getPuppetOfCiv()==defender||CFG.core.getCiv(defender).getPuppetOfCiv()==hostCiv)continue;
      if(!CFG.core.getCivsAtWar(hostCiv,defender))CFG.core.addWarData(hostCiv,defender);
      int troops=Math.max(10_000,CFG.MIN_ARMY_REQUIRED_TO_ATTACK);
      origin.updateArmy4(hostCiv,troops+10);
      target.updateArmy4(defender,1);
      require(CFG.gameAction.moveArmyAction(from,to,troops,hostCiv,false,true),"Host can order a real-time attack");
      int turn=GameCalendar.TURNID;
      require(KreyliRealtimeCombat.resolve(hostCiv,country.moveUnitsSize()-1),"Host resolves the independent attack");
      require(GameCalendar.TURNID==turn&&target.getCivId()==hostCiv&&origin.getArmyCivID1(hostCiv)==10,
              "Capture and troop reservation settle without advancing the native turn");
      signal("battle-result",to+","+hostCiv+","+turn);
      return;
     }
    }
    throw new AssertionError("No land border for combat fixture");
   }
   int marchIndex(){
    var country=CFG.core.getCiv(hostCiv);
    for(int i=0;i<country.moveUnitsSize();i++){
     var order=country.getMoveUnits(i);
     if(order.getFromProviID()==marchFrom&&order.getToProvID()==marchTo)return i;
    }
    return -1;
   }
   boolean marchQueued(){return marchIndex()>=0;}
   void checkSavedMarch(){
    var country=CFG.core.getCiv(hostCiv);
    var route=KreyliRealtimeMarch.storedRoutes();
    country.removeMove(marchIndex()); // The native save restores the armies but omits transient orders.
    int otherRoutes=country.moveUnitsSize();
    KreyliRealtimeMarch.restoreStoredRoutes(route);
    KreyliRealtimeMarch.restoreStoredRoutes(route);
    require(marchQueued()&&country.moveUnitsSize()==otherRoutes+1,"Saved march restores once");
    require(KreyliRealtimeMarch.elapsed(country.getMoveUnits(marchIndex()))>0,"Saved march resumes at its elapsed time");
   }
   public void render(){try{super.render();frames++;
    if(frames%300==0)System.out.println("TURN_FEATURES "+(host?"host":"client")+" phase="+phase+" turn="+GameCalendar.TURNID);
    if(phase==0&&frames>300&&CFG.core.getCivsSize()>2&&(host||exists("ready"))){
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
     if(phase==3&&exists("accepted")&&CFG.core.getCiv(guestCiv).getGold()==guestGold+10&&hudControl("OPEN")!=null){
      int[] arrow=hudControl("OPEN");
      var button=CFG.menus.getInGameProvInfo().getMenuElem(0);
      require(arrow[0]==CFG.menus.getInGameProvInfo().getMenuPosX()+button.getPosXE(),"Arrow stays inside Ready bounds");
      require(arrow[3]==button.getHeightE()&&arrow[2]<button.getWidthE(),"Arrow is narrower and as high as native Ready");
      MP.relayCode="KRY114";pressHud("OPEN");phase=30;
     }
     if(phase==30&&hudControl("MORE")!=null){
      KreyliUiProbe.capture("kreyli-skip-chooser.png");
      CFG.menus.setVisible_InGame_Options(true);
      phase=31;return;
     }
     if(phase==31){
      require(CFG.menus.getInGameView_Options()&&hudControl("OPEN")==null&&hudControl("MORE")==null,"Pause hides skip overlay");
      KreyliUiProbe.capture("kreyli-skip-paused.png");
      CFG.menus.setVisible_InGame_Options(false);
      phase=32;return;
     }
     if(phase==32&&hudControl("OPEN")!=null){
      require(hudControl("MORE")==null,"Closing pause does not reopen chooser");
      pressHud("OPEN");phase=33;return;
     }
     if(phase==33&&hudControl("MORE")!=null){
      MP.relayCode=null;
      pressHud("MORE");require(KreyliTurns.selectedSkipCount()==2,"Chooser changes the selected count");
      pressHud("PROPOSE");signal("gift-applied","ok");startTurn=GameCalendar.TURNID;phase=4;
     }
     if(phase==4&&exists("cancelled")&&KreyliTurns.vote.id==0){require(GameCalendar.TURNID==startTurn,"No turn with one vote");KreyliTurns.proposeSkip();signal("second-proposal","ok");phase=5;}
     if(phase==5&&GameCalendar.TURNID==startTurn+2&&playable()&&KreyliTurns.vote.id==0){signal("host-final",CFG.core.getCiv(guestCiv).getGold()+","+GameCalendar.TURNID);MP.endTurnPressed();phase=6;waitStart=0;}
     if(phase==6&&exists("stale-sent")){if(waitStart==0)waitStart=System.currentTimeMillis();if(System.currentTimeMillis()-waitStart>2000){require(GameCalendar.TURNID==startTurn+2&&!MP.clients.get(0).ready,"Stale prior-turn ready did not advance ready host");MP.endTurnPressed();signal("stale-rejected","ok");phase=7;}}
     if(phase==7&&exists("stale-rejected")){MP.turnSeconds=2;waitStart=System.nanoTime();phase=8;}
     if(phase==8&&System.nanoTime()-waitStart>2_500_000_000L){
      require(GameCalendar.TURNID==startTurn+2,"No timeout before first readiness");
      MP.endTurnPressed();phase=9;
     }
     if(phase==9&&GameCalendar.TURNID==startTurn+3&&playable()){
      require(exists("saw-countdown"),"Guest saw persistent readiness/countdown before timeout");
      MP.turnSeconds=90;MP.endTurnPressed();signal("early-ready","ok");phase=10;
     }
     if(phase==10&&GameCalendar.TURNID==startTurn+4&&playable()){signal("early-advanced","ok");phase=11;}
     if(phase==11&&exists("realtime-client-ready")){
      KreyliLobby.readMode(true);
      selectedProvince=CFG.core.getCiv(hostCiv).getProvID(0);
      CFG.core.setActiveProvID(selectedProvince);
      CFG.setActiveCivInfoId(hostCiv);
      CFG.menus.setVisible_InGame_CivInfo(true);
      require(CFG.menus.getVisible_InGame_CivInfo(),"Country details opened before auto-turn");
      autoCountry=CFG.getActiveCivInfoId();
      require(KreyliTurns.marchMillis(600)==5000,"Real-time movement interpolates over the turn interval");
      startTurn=GameCalendar.TURNID;
      waitStart=System.nanoTime();
      phase=12;
     }
     if(phase==12&&marchIssued==0&&System.nanoTime()-waitStart>2_000_000_000L){
      require(!CFG.menus.getInGameProvInfo().getMenuElem(0).getVisibleE(),"Real-time hides Auto turn");
      require(!CFG.menus.getInGameMenu().getMenuElem(5).getVisibleE(),"Real-time hides turn and ready counters");
      MP.endTurnPressed();
      require(GameCalendar.TURNID==startTurn&&MP.inputOrders(),"Ready shortcut cannot advance real-time mode");
      startMarch();
      var country=CFG.core.getCiv(hostCiv);
      recruitProvince=java.util.stream.IntStream.range(0,country.getNumOfProvs()).map(country::getProvID)
              .filter(p->p!=marchFrom&&p!=marchTo&&CFG.gameAction.gMARY(p)>=10).findFirst().orElseThrow();
      require(country.recruitArmy(recruitProvince,10),"Native recruitment order accepted");
      selectedProvince=marchFrom;
      CFG.core.setActiveProvID(selectedProvince);
      CFG.map.getMpS().setCurrScale(1.2f);
      CFG.core.chooseProvinceMode();
      require(CFG.chooseProvinceMode,"Army selection active at close zoom");
      age.of.civilizations2.jakowski.lukasz.Save.SaveGameManager.gameSaved=true;
     }
     if(phase==12&&KreyliRealtimeEconomy.snapshotSequence()>0&&!exists("economy-payout")){
      require(GameCalendar.TURNID==startTurn,"Host treasury settles before the next internal turn");
      signal("economy-payout",Long.toString(CFG.core.getCiv(guestCiv).getGold()));
     }
     if(phase==12&&GameCalendar.TURNID>startTurn&&playable()&&++autoSettledFrames>=3){
      require(exists("guest-saw-economy"),"Guest received matching gold without a native turn");
      require(marchIssued>0&&marchQueued()&&CFG.core.getProv(marchTo).getArmyCivID1(hostCiv)==marchDestination,
              "March crosses a turn boundary before arriving");
      require(CFG.menus.getVisible_InGame_CivInfo()&&CFG.getActiveCivInfoId()==autoCountry,"Country details survive the automatic turn: visible="+CFG.menus.getVisible_InGame_CivInfo()+" country="+CFG.getActiveCivInfoId()+" expected="+autoCountry);
      require(CFG.core.getActiveProvID()==selectedProvince,"Selected province survives automatic update");
      require(CFG.menus.getInGameMenu().getVisibleM()&&!CFG.HIDE_UI,"The native HUD stays visible at close zoom");
      require(!CFG.menus.getInGameMenu(CFG.menus.INGAME_SAVED_GAME).getVisibleM(),"Automatic update does not show the saved-game notice");
      require(!CFG.menus.getInGameProvInfo().getMenuElem(0).getVisibleE(),"Auto turn stays hidden after automatic update");
      marchSource=CFG.core.getProv(marchFrom).getArmyCivID1(hostCiv);
      marchDestination=CFG.core.getProv(marchTo).getArmyCivID1(hostCiv);
      var country=CFG.core.getCiv(hostCiv);
      require(country.getRecruitArmy_BasedOnProvinceID(recruitProvince)==10,"Recruitment remains queued across the native update");
      String recruits=KreyliRealtimeRecruitment.save();
      country.clearRecruitArmy();
      KreyliRealtimeRecruitment.load(recruits);
      KreyliRealtimeRecruitment.load(recruits);
      require(country.getRecruitArmySize()==1,"Recruitment queue restores once");
      recruitArmyBefore=CFG.core.getProv(recruitProvince).getArmyCivID1(hostCiv);
      checkSavedMarch();
      phase=13;
     }
     if(phase==13&&playable()&&!marchQueued()&&CFG.core.getCiv(hostCiv).getRecruitArmySize()==0){
      long travel=System.nanoTime()-marchIssued;
      require(travel>=4_950_000_000L&&travel<8_500_000_000L,"March arrival follows the order clock");
      require(CFG.core.getProv(marchFrom).getArmyCivID1(hostCiv)==marchSource
              &&CFG.core.getProv(marchTo).getArmyCivID1(hostCiv)==marchDestination+10,
              "Exactly ten troops are transferred once");
      require(CFG.core.getProv(recruitProvince).getArmyCivID1(hostCiv)==recruitArmyBefore+10,"Timed recruitment adds troops exactly once");
      signal("recruitment-result",recruitProvince+","+hostCiv+","+(recruitArmyBefore+10)+","+GameCalendar.TURNID);
      checkRealtimeBattle();
      phase=14;
     }
     if(phase==14&&exists("client-pass")){
      MP.stop();
      for(Menu top:new Menu[]{
        new age.of.civilizations2.jakowski.lukasz.Menus.ZRest.Menu_InGame_1(),
        new age.of.civilizations2.jakowski.lukasz.Menus.ZRest.Menu_InGame_2()
      }){
       int hidden=0;
       for(var item:top.menuElem){
        String type=item.getClass().getName();
        if(type.endsWith("$8")||type.endsWith("$14")){
         require(item.getVisibleE()==RTS.isEnabled(),"Solo speed control follows native RTS");
         hidden++;
        }
       }
       require(hidden==2,"Both solo speed controls restored");
      }
      System.out.println("TURN_FEATURES PASS gift, stale ready, unanimous skip, wait timeout, early advance and realtime march");Runtime.getRuntime().halt(0);}
    }else{
     if(phase==1&&MP.picking&&exists("ready")&&!MP.pickPending()){hostCiv=Integer.parseInt(read("ready"));civ=1;while(civ==hostCiv||CFG.core.getCiv(civ).getNumOfProvs()==0)civ++;var previous=com.badlogic.gdx.Gdx.input.getInputProcessor();
      MP.pick(civ);
      var chooser=com.badlogic.gdx.Gdx.input.getInputProcessor();
      require(chooser!=previous,"Country confirmation dialog opens");
      chooser.keyDown(com.badlogic.gdx.Input.Keys.ENTER);
      require(MP.pickPending()&&com.badlogic.gdx.Gdx.input.getInputProcessor()==previous,"Country confirmation sends the pick");
      phase=2;}
     if(phase==2&&MP.myCiv==civ&&!MP.picking&&playable()){
      startTurn=GameCalendar.TURNID;CFG.core.getPlayer(CFG.PLAYER_TURN_ID).setNoOrders(true);CFG.menus.getInGameProvInfo().actionEL(0);require(MP.ready,"Actual ready button bypasses no-orders prompt");phase=20;
     }
     if(phase==20){require(CFG.menus.getInGameProvInfo().getMenuElem(0).getIsClickable(),"Ready button stays clickable for cancellation");CFG.menus.getInGameProvInfo().actionEL(0);require(!MP.ready,"Actual button cancels readiness");CFG.menus.getInGameProvInfo().actionEL(0);require(MP.ready,"Actual button re-readies");signal("guest-ready","ok");phase=3;}
     if(phase==3&&exists("gift-sent")){
      var box=CFG.core.getCiv(civ).getCivDiploGD().messageBox;for(int i=0;i<box.getMessagesSize();i++)if(box.getMessage(i) instanceof Message_Gift gift){require(MP.ready,"Recipient still ready when gift arrives");gift.onAccept(civ);require(!MP.ready,"Gift acceptance withdraws readiness");signal("accepted","ok");phase=4;break;}
     }
     if(phase==4&&exists("gift-applied")&&KreyliTurns.incomingSkipStatus().contains("SkipHost")){if(waitStart==0)waitStart=System.currentTimeMillis();require(GameCalendar.TURNID==startTurn,"No skip without guest consent");if(System.currentTimeMillis()-waitStart>3000){KreyliUiProbe.capture("kreyli-skip-notice.png");pressHud("DECLINE");signal("cancelled","ok");phase=5;}}
     if(phase==5&&exists("second-proposal")&&KreyliTurns.skipCanRespond()&&hudControl("ACCEPT")!=null){pressHud("ACCEPT");phase=6;}
     if(phase==6&&exists("host-final")&&playable()){String[] parts=read("host-final").split(",");if(GameCalendar.TURNID==Integer.parseInt(parts[1])){
      require(CFG.core.getCiv(civ).getGold()==Long.parseLong(parts[0]),"Final money agrees");
      var hostField=MP.class.getDeclaredField("host");hostField.setAccessible(true);var count=MP.class.getDeclaredField("sentCmds");count.setAccessible(true);
      ((MP_Net.Conn)hostField.get(null)).send("kreyli_ready",startTurn,count.getInt(null),999999L,true);signal("stale-sent","ok");phase=7;
     }}
     if(phase==7&&exists("stale-rejected")&&KreyliTurns.readinessStatus().contains("SkipHost")&&KreyliTurns.readinessStatus().endsWith(KreyliText.t(" с"," s"))){signal("saw-countdown","ok");phase=8;}
     if(phase==8&&exists("early-ready")&&GameCalendar.TURNID==startTurn+3&&playable()){MP.endTurnPressed();phase=9;}
     if(phase==9&&exists("early-advanced")&&GameCalendar.TURNID==startTurn+4&&playable()){
      KreyliLobby.readMode(true);
      selectedProvince=CFG.core.getCiv(civ).getProvID(0);
      CFG.core.setActiveProvID(selectedProvince);
      startTurn=GameCalendar.TURNID;
      signal("realtime-client-ready","ok");
      phase=10;
     }
     if(phase==10&&GameCalendar.TURNID==startTurn&&exists("economy-payout")
              &&CFG.core.getCiv(civ).getGold()==Long.parseLong(read("economy-payout"))&&!exists("guest-saw-economy"))
      signal("guest-saw-economy","ok");
     if(phase==10&&GameCalendar.TURNID>startTurn&&playable()){
      require(CFG.core.getActiveProvID()==selectedProvince,"Guest province survives host update");
      require(!CFG.menus.getInGameProvInfo().getMenuElem(0).getVisibleE(),"Guest does not see Auto turn");
      phase=11;
     }
     if(phase==11&&exists("recruitment-result")&&playable()){
      String[] expected=read("recruitment-result").split(",");
      int province=Integer.parseInt(expected[0]),country=Integer.parseInt(expected[1]);
      if(CFG.core.getProv(province).getArmyCivID1(country)!=Integer.parseInt(expected[2]))return;
      require(GameCalendar.TURNID==Integer.parseInt(expected[3]),"Guest sees trained troops before another native turn");
      if(!exists("battle-result"))return;
      String[] battle=read("battle-result").split(",");
      if(CFG.core.getProv(Integer.parseInt(battle[0])).getCivId()!=Integer.parseInt(battle[1]))return;
      require(GameCalendar.TURNID==Integer.parseInt(battle[2]),"Guest sees the captured province in the same native turn");
      signal("client-pass","ok");
      MP.stop();
      System.out.println("TURN_FEATURES PASS guest selection and controls across a real-time snapshot");
      Runtime.getRuntime().halt(0);
     }
    }
   }catch(Throwable error){error.printStackTrace();Runtime.getRuntime().halt(3);}}
  },config);
 }
}
