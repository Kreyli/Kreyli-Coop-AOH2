package age.of.civilizations2.jakowski.lukasz.mp;
import age.of.civilizations2.jakowski.lukasz.*;
import age.of.civilizations2.jakowski.lukasz.Menus.NewGame.Menu_CreateNewGame;
import age.of.civilizations2.jakowski.lukasz.Menus.Civilization.Menu_InGame_Civ;
import age.of.civilizations2.jakowski.lukasz.GameValues.GameValues;
import aoc.kingdoms.lukasz.jakowski.AA_Game;
import com.badlogic.gdx.backends.lwjgl3.*;
import java.nio.file.*;
public final class KreyliOrderNetworkProbe {
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static long received()throws Exception{var f=KreyliOrderSync.class.getDeclaredField("received");f.setAccessible(true);return f.getLong(null);}
 static void pressAlt(int key,int x){
  CFG.menus.setActiveMenuElemeID(-1);
  var original=com.badlogic.gdx.Gdx.input;
  com.badlogic.gdx.Gdx.input=(com.badlogic.gdx.Input)java.lang.reflect.Proxy.newProxyInstance(
   com.badlogic.gdx.Input.class.getClassLoader(),new Class<?>[]{com.badlogic.gdx.Input.class},(proxy,method,args)->{
    if(method.getName().equals("isKeyJustPressed"))return args[0].equals(key);
    if(method.getName().equals("getX"))return x;
    if(method.getName().equals("getY"))return 300;
    return method.invoke(original,args);
   });
  try{KreyliPings.tick();}finally{com.badlogic.gdx.Gdx.input=original;}
 }
 static void ping(){pressAlt(com.badlogic.gdx.Input.Keys.ALT_LEFT,640);}
 static String readUnchecked(Path path){try{return Files.readString(path);}catch(Exception e){throw new IllegalStateException(e);}}
 static boolean marked(int civ){return KreyliPings.marks.stream().anyMatch(p->p.civ()==civ);}
 public static void main(String[] args)throws Exception{
  if(!Files.exists(Path.of(".coop-test-copy")))throw new IllegalStateException("Test copy required");
  boolean host=args[0].equals("host"),shared=Boolean.getBoolean("coop.shared");Path channel=Path.of(args[1]);int port=Integer.parseInt(args[2]);
  var remote=MP.class.getDeclaredField("remoteRequested");remote.setAccessible(true);remote.setBoolean(null,true);
  new Thread(()->{try{Thread.sleep(480000);}catch(InterruptedException e){return;}Runtime.getRuntime().halt(8);}).start();
  var config=new Lwjgl3ApplicationConfiguration();config.setWindowedMode(1280,720);config.setInitialVisible(false);config.disableAudio(true);config.setForegroundFPS(30);
  new Lwjgl3Application(new AA_Game(){int frame,phase,civ,hostCiv,guestCiv,from,to;long began,snapshots,orderSequence;
   boolean has(String key){return Files.exists(channel.resolve(key));}void put(String key,String value)throws Exception{Files.writeString(channel.resolve(key),value);}String get(String key)throws Exception{return Files.readString(channel.resolve(key));}
   boolean playable(){return CFG.menus.getInGameView()&&MP.inputOrders();}
   boolean openRemoteProfile()throws Exception{
    if(System.currentTimeMillis()-Menu_InGame_Civ.lTime<GameValues.gvInGame.MENUS_ANIMATION_TIME)return false;
    var field=KreyliProfileUi.class.getDeclaredField("countryPortraits");field.setAccessible(true);
    for(var item:((java.util.Map<?,?>)field.get(null)).entrySet()){
     if(!"OrdersHost".equals(item.getValue()))continue;
     var box=item.getKey();
     var x=box.getClass().getDeclaredMethod("x");x.setAccessible(true);
     var y=box.getClass().getDeclaredMethod("y");y.setAccessible(true);
     int px=(Integer)x.invoke(box)+CFG.PADD,py=(Integer)y.invoke(box)+CFG.PADD;
     Menu sidebar=CFG.menus.getInGameMenu(CFG.menus.INGAME_CIV_INFO_STATS);
     Menu diplomacy=CFG.menus.getInGame_Civ_Info_Diplomacy();
     int bottom=sidebar.getPosY()+sidebar.getHeightM();
     check(px>=sidebar.getPosX()&&px<sidebar.getPosX()+sidebar.getWidthM(),"Player card stays inside country sidebar width");
     check(py>=sidebar.getPosY()&&py<bottom&&bottom<=diplomacy.getPosY(),"Player section is inside country stats, before diplomacy");
     KreyliUiProbe.capture("kreyli-country-player-section.png");
     Touch touch=new Touch();
     touch.actionDown(px,py,0,0);
     touch.actionUp(px,py,0,0);
     var viewed=KreyliProfileUi.class.getDeclaredField("viewedPlayer");viewed.setAccessible(true);
     check("OrdersHost".equals(viewed.get(null)),"Country portrait opens the host profile");
     check(KreyliProfile.stats("OrdersHost").earned()>=17,"Host lifetime statistics available to guest");
     return true;
    }
    return false;
   }
   public void render(){try{super.render();frame++;
    if(phase==0&&frame>300&&CFG.core.getCivsSize()>2&&(host||has("ready"))){MP.nick=host?"OrdersHost":"OrdersGuest";MP.password="test";MP.hostDirect=true;MP.listInLobby=false;MP.port=port;MP.turnSeconds=0;
     if(host){check(MP.startHost(),"Host start");civ=1;while(CFG.core.getCiv(civ).getNumOfProvs()==0)civ++;hostCiv=civ;CFG.core.getPlayer(0).setCivId(civ);KreyliCountrySeats.configureHost(civ,shared?2:1);Menu_CreateNewGame.newGame();}
     else{MP.address="127.0.0.1:"+port;MP.join();}phase=1;
    }
    if(host){
     if(phase==1&&MP.started&&playable()){put("ready",Integer.toString(civ));phase=2;}
     if(phase==2&&has("guest-in")){
      guestCiv=MP.clients.get(0).civ;
      var treasury=CFG.core.getCiv(hostCiv);
      long balance=treasury.getGold(), earned=KreyliProfile.earned();
      treasury.setGold(balance+17);treasury.setGold(balance);
      check(KreyliProfile.earned()==earned+17,"Treasury credit increments earnings without counting expenditure");
      CFG.core.getCiv(hostCiv).setAlliance(0);CFG.core.getCiv(guestCiv).setAlliance(0);
      MP.requestSync();put("nonallied","ok");phase=3;
     }
     if(phase==3&&has("nonallied-synced")){KreyliPings.reset();ping();began=System.nanoTime();put("nonallied-ping","ok");phase=4;}
     if(phase==4&&has("nonallied-pass")){CFG.core.addAlliance("Probe alliance");int alliance=CFG.core.getAlliancesSize()-1;CFG.core.getCiv(hostCiv).setAlliance(alliance);CFG.core.getCiv(guestCiv).setAlliance(alliance);MP.requestSync();put("allied",Integer.toString(alliance));phase=5;}
     if(phase==5&&has("allied-synced")){KreyliPings.reset();pressAlt(com.badlogic.gdx.Input.Keys.ALT_RIGHT,640);check(KreyliPings.marks.isEmpty(),"Right Alt ignored");ping();var first=KreyliPings.marks.getFirst();pressAlt(com.badlogic.gdx.Input.Keys.ALT_LEFT,680);var last=KreyliPings.marks.getLast();check(KreyliPings.marks.size()==1&&last.x()!=first.x()&&last.until()>first.until(),"New Alt mark replaces old and renews lifetime");put("allied-ping",Integer.toString(last.x()));phase=6;}
     if(phase==6&&has("allied-pass")&&KreyliPings.marks.stream().anyMatch(p->!p.fromHost())){
      check(KreyliPings.marks.size()==2,"Players retain their own latest mark even with a shared country");
      var own=CFG.core.getCiv(hostCiv);from=to=-1;for(int i=0;i<own.getNumOfProvs()&&from<0;i++){int p=own.getProvID(i);for(int j=0;j<CFG.core.getProv(p).getNeighProvincesSize();j++){int q=CFG.core.getProv(p).getNeighProvinces(j);if(!CFG.core.getProv(p).getSeaProv()&&!CFG.core.getProv(q).getSeaProv()&&CFG.core.getProv(q).getCivId()==hostCiv){from=p;to=q;break;}}}check(from>=0,"Adjacent friendly movement fixture");CFG.core.getProv(from).updateArmy4(hostCiv,50);MP.requestSync();put("fixture",from+","+to);phase=7;
     }
     if(phase==7&&has("fixture-ready")){CFG.gameAction.moveArmyAction(from,to,10,hostCiv,false,true);put("moved","ok");phase=8;}
     if(phase==8&&has("client-pass")){MP.stop();System.out.println("ORDER_NETWORK PASS host lightweight move and bidirectional allied ping shared="+shared);Runtime.getRuntime().halt(0);}
    }else{
     if(phase==1&&MP.picking&&has("ready")&&!MP.pickPending()){hostCiv=Integer.parseInt(get("ready"));civ=shared?hostCiv:1;while(!shared&&(civ==hostCiv||CFG.core.getCiv(civ).getNumOfProvs()==0))civ++;KreyliCountrySeats.submit(civ,1);phase=2;}
     if(phase==2&&MP.myCiv==civ&&!MP.picking&&playable()){put("guest-in","ok");phase=3;}
     if(phase==3&&has("nonallied")&&CFG.core.getCiv(hostCiv).getAlliance()==0&&CFG.core.getCiv(civ).getAlliance()==0){KreyliPings.reset();put("nonallied-synced","ok");phase=4;}
     if(phase==4&&has("nonallied-ping")){if(began==0)began=System.nanoTime();if(System.nanoTime()-began>1_000_000_000L){if(!shared)check(!marked(hostCiv),"Nonallied ping leaked");put("nonallied-pass","ok");phase=5;}}
     if(phase==5&&has("allied")&&CFG.core.getCiv(civ).getAlliance()==Integer.parseInt(get("allied"))&&CFG.core.getCiv(hostCiv).getAlliance()==Integer.parseInt(get("allied"))){KreyliPings.reset();put("allied-synced","ok");phase=6;}
     if(phase==6&&has("allied-ping")&&KreyliPings.marks.stream().anyMatch(p->p.fromHost()&&p.x()==Integer.parseInt(readUnchecked(channel.resolve("allied-ping"))))){ping();check(!CFG.map.getTouchMgr().getActionMap(),"Alt ping started map drag");put("allied-pass","ok");KreyliUiProbe.capture("kreyli-allied-ping.png");snapshots=KreyliInputRecovery.snapshotApplications;phase=7;}
     if(phase==7&&has("fixture")){String[] pair=get("fixture").split(",");from=Integer.parseInt(pair[0]);to=Integer.parseInt(pair[1]);if(CFG.core.getProv(from).getArmyCivID1(hostCiv)==50&&KreyliInputRecovery.snapshotApplications>snapshots){snapshots=KreyliInputRecovery.snapshotApplications;orderSequence=received();put("fixture-ready","ok");phase=8;}}
     if(phase==8&&has("moved")&&received()>orderSequence){var own=CFG.core.getCiv(hostCiv);boolean found=false;for(int i=0;i<own.moveUnitsSize();i++)if(own.getMoveUnits(i).getFromProviID()==from&&own.getMoveUnits(i).getToProvID()==to)found=true;if(found){check(KreyliInputRecovery.snapshotApplications==snapshots,"Light movement reloaded full save");MP_UI.togglePlayers();Menu table=CFG.menus.getInGameMenu(CFG.menus.INGAME_MP_PLAYERS);check(table.getVisibleM(),"F10 opens player table");for(int i=1;i<5;i++)check(table.getMenuElem(i).getWidthE()>=MP_UI.textWidth(table.getMenuElem(i).getTextE())+CFG.PADD*2,"F10 header overflow column "+i);phase=9;return;}}
     if(phase==9&&(!shared||KreyliProfile.earned()>=17)&&KreyliProfile.stats("OrdersHost")!=null&&KreyliProfile.stats("OrdersHost").earned()>=17){
      if(shared)check(KreyliProfile.player(civ).size()==2,"Both shared-country profiles visible");
      KreyliUiProbe.capture("kreyli-f10-columns.png");
      MP_UI.togglePlayers();
      CFG.setActiveCivInfoId(hostCiv);
      CFG.menus.setVisible_InGame_CivInfo(true);
      phase=10;return;
     }
     if(phase==10&&openRemoteProfile()){phase=11;return;}
     if(phase==11){
      KreyliUiProbe.capture("kreyli-remote-profile.png");
      put("client-pass","ok");System.out.println("ORDER_NETWORK PASS client no save reload, movement queues, left Alt ping, F10 headers and shared country profiles");MP.stop();Runtime.getRuntime().halt(0);
     }
    }
   }catch(Throwable e){e.printStackTrace();Runtime.getRuntime().halt(3);}}
  },config);
 }
}






