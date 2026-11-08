package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.*;
import age.of.civilizations2.jakowski.lukasz.Menus.NewGame.Menu_CreateNewGame;
import aoc.kingdoms.lukasz.jakowski.AA_Game;
import com.badlogic.gdx.backends.lwjgl3.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Two-process upstream baseline. Coordination files contain only disposable test data. */
public class VendorNetworkProbe {
  static boolean capStarted;
  static volatile boolean capPassed;
  static void checkCapacity(int port){
    capStarted=true;
    Thread worker=new Thread(()->{
      try(var socket=new java.net.Socket("127.0.0.1",port)){
        socket.setSoTimeout(15000);
        var out=new java.io.DataOutputStream(socket.getOutputStream());
        byte[] data=MP_Net.encode("hello","AoH2DE-MP-2.2|2.01 Definitive Edition|kreyli-coop-3","ThirdBot",MP.password);
        out.writeInt(data.length);out.write(data);out.flush();
        var in=new java.io.DataInputStream(socket.getInputStream());int size=in.readInt();
        if(size<1||size>65536)throw new AssertionError("Bad rejection frame");
        Object[] reply=MP_Net.decode(in.readNBytes(size));
        if(reply.length<2||!"reject".equals(reply[0])||!"Kreyli_LobbyFull".equals(reply[1]))throw new AssertionError("Third player accepted: "+Arrays.toString(reply));
        capPassed=true;System.out.println("KREYLI_CAPACITY: PASS third player rejected by native host");
      }catch(Throwable error){error.printStackTrace();Runtime.getRuntime().halt(9);}
    },"capacity-probe");worker.setDaemon(true);worker.start();
  }
  static Path channel; static boolean host;
  static void signal(String name,String data)throws Exception{Files.writeString(channel.resolve(name),data);}
  static boolean exists(String name){return Files.exists(channel.resolve(name));}
  static String read(String name)throws Exception{return Files.readString(channel.resolve(name));}
  static String militaryState(){
    var rows=new ArrayList<>(Arrays.asList(SharedState.capture().split("\n")));
    for(int c=1;c<CFG.core.getCivsSize();c++)rows.add("country,"+c+","+CFG.core.getCiv(c).getGold()+","+CFG.core.getCiv(c).getAlliance());
    for(int p=0;p<CFG.core.getProvinSize();p++)rows.add("owner,"+p+","+CFG.core.getProv(p).getCivId()+","+CFG.core.getProv(p).getTrueOwnerOfProv());
    Collections.sort(rows);return String.join("\n",rows);
  }
  public static void main(String[] args)throws Exception{
    host=args[0].equals("host");channel=Path.of(args[1]);int port=Integer.parseInt(args[2]);
    if(!Files.exists(Path.of(".coop-test-copy")))throw new IllegalStateException("Marker missing");
    String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of("game.jar"))));
    if(!hash.equals("db144f0ddb7e62de92ad971eb7bc0b9a66b57cabbfe70b9dcda7d2a70204704c"))throw new IllegalStateException("Archive mismatch");
    var remote=MP.class.getDeclaredField("remoteRequested");remote.setAccessible(true);remote.setBoolean(null,true);
    if(!host&&Boolean.getBoolean("coop.patch"))SharedSupport.shared=false;
    Thread timeout=new Thread(()->{try{Thread.sleep(420000);}catch(Exception e){}System.err.println("VENDOR_NET: FAIL timeout");Runtime.getRuntime().halt(2);});timeout.setDaemon(true);timeout.start();
    var config=new Lwjgl3ApplicationConfiguration();config.setTitle("Isolated upstream network test");config.setWindowedMode(1280,720);config.setInitialVisible(false);config.disableAudio(true);config.setForegroundFPS(30);
    new Lwjgl3Application(new AA_Game(){
      int frames,phase,turn,country,province,amount,recruitProvince=-1,recruitBefore,battleFrom=-1,battleTo=-1,enemy=-1;long gold,readyWaitStarted;boolean raced;
      boolean playable(){return CFG.menus.getInGameView()&&MP.inputOrders();}
      public void render(){try{
        super.render();frames++;
        if(frames%300==0)System.out.println("VENDOR_NET: "+(host?"host":"client")+" phase="+phase+" view="+CFG.menus.getViewID()+" turn="+GameCalendar.TURNID);
        if(phase==0&&frames>300&&CFG.core.getCivsSize()>2){
          MP.nick=host?"BotHost":"BotGuest";MP.password="bot-"+channel.getFileName();MP.hostDirect=true;MP.listInLobby=false;MP.port=port;MP.turnSeconds=0;
          if(Boolean.getBoolean("coop.relay")){
            MP.regions=MP.parseRegions(Files.readAllLines(channel.resolve("relay-config.txt")).get(0).split(";")[0]);
            MP.regionName=MP.regions.get(0)[1];MP.hostDirect=false;
          }
          if(host&&Boolean.getBoolean("coop.patch")){
            var method=MP_UI.class.getDeclaredMethod("host");method.setAccessible(true);MP_Form form=(MP_Form)method.invoke(null);
            boolean original=SharedSupport.shared;form.onRow(0);if(SharedSupport.shared==original)throw new AssertionError("Mode toggle failed");form.onRow(0);
            System.out.println("VENDOR_NET: PASS native host form mode toggle");
          }
          if(host){
            if(!MP.startHost())throw new IllegalStateException("Host failed");
            country=1;while(CFG.core.getCiv(country).getNumOfProvs()==0)country++;
            CFG.core.getPlayer(0).setCivId(country);Menu_CreateNewGame.newGame();
          }else{MP.address=Boolean.getBoolean("coop.relay")?read("relay-room"):"127.0.0.1:"+port;MP.join();}
          phase=1;
        }
        if(host){
          if(Boolean.getBoolean("coop.patch")&&!Boolean.getBoolean("coop.relay")&&!capStarted&&MP.clients.size()==1)checkCapacity(port);
          if(phase==1&&MP.started&&playable()){
            if(Boolean.getBoolean("coop.race"))CFG.core.getCiv(country).setGold(150);
            if(Boolean.getBoolean("coop.battle")){
              var civ=CFG.core.getCiv(country);
              for(int i=0;i<civ.getNumOfProvs()&&battleFrom<0;i++){
                int p=civ.getProvID(i);var from=CFG.core.getProv(p);if(from.getArmyCivID1(country)<2||from.getSeaProv())continue;
                for(int j=0;j<from.getNeighProvincesSize();j++){
                  int q=from.getNeighProvinces(j);var to=CFG.core.getProv(q);int target=to.getCivId();
                  if(target>0&&target!=country&&!to.getSeaProv()&&to.getArmyCivID1(target)>0&&!CFG.core.getCivsAtWar(country,target)){
                    battleFrom=p;battleTo=q;enemy=target;break;
                  }
                }
              }
              if(battleFrom<0)throw new AssertionError("No defended foreign border fixture");
              signal("battle-fixture",battleFrom+","+battleTo+","+enemy);
            }
            if(Boolean.getBoolean("coop.relay"))signal("relay-room",MP.relayCode);
            signal("host-ready",Integer.toString(country));phase=2;
          }
          if(phase==2&&exists("investment")){
            String[] v=read("investment").split(",");country=Integer.parseInt(v[0]);province=Integer.parseInt(v[1]);amount=Integer.parseInt(v[2]);gold=Long.parseLong(v[3]);turn=Integer.parseInt(v[4]);
            if(Boolean.getBoolean("coop.race")&&!raced){
              raced=true;int other=-1;var civ=CFG.core.getCiv(country);
              for(int i=0;i<civ.getNumOfProvs();i++){int p=civ.getProvID(i);if(p!=province&&GameManager.invest_MaxEconomy_Gold(p,country)>0&&GameManager.invest_EconomyByGold(p,50)>0){other=p;break;}}
              if(other<0||!GameManager.invest(other,country,100))throw new AssertionError("Host overlapping investment failed");
            }
            if(!Boolean.getBoolean("coop.race")&&CFG.core.getCiv(country).getGold()<gold-amount)throw new AssertionError("Unexpected host gold");
            if(CFG.core.getCiv(country).getGold()==(Boolean.getBoolean("coop.race")?0:gold-amount)){
              if(CFG.core.getPlayerIDbyCivID(country)!=(Boolean.getBoolean("coop.shared")?0:1))throw new AssertionError("Native player mapping wrong");
              System.out.println("VENDOR_NET: PASS investment settlement; overlapping="+Boolean.getBoolean("coop.race"));signal("investment-ok",Long.toString(CFG.core.getCiv(country).getGold()));phase=3;
            }
          }
          if(phase==3&&exists("army-request")){
            int p=Integer.parseInt(read("army-request"));var civ=CFG.core.getCiv(country);
            for(int i=0;i<civ.getRecruitArmySize();i++)if(civ.getRecruitArmy(i).getProvinceID()==p&&civ.getRecruitArmy(i).getArmy()==10){
              signal("army-ok",age.of.civilizations2.jakowski.lukasz.mp.SharedState.capture());break;
            }
          }
          if(phase==3&&Boolean.getBoolean("coop.battle")){
            if(CFG.core.getCivsAtWar(country,enemy))signal("war-ok","ok");
            var civ=CFG.core.getCiv(country);
            for(int i=0;i<civ.moveUnitsSize();i++)if(civ.getMoveUnits(i).getFromProviID()==battleFrom&&civ.getMoveUnits(i).getToProvID()==battleTo)signal("battle-move-ok",militaryState());
          }
          if(phase==3&&exists("guest-ready")){if(readyWaitStarted==0)readyWaitStarted=System.currentTimeMillis(); if(GameCalendar.TURNID!=turn)throw new AssertionError("Turn advanced with host not ready"); if(System.currentTimeMillis()-readyWaitStarted>=8000){MP.endTurnPressed();phase=4;}}
          if(phase==4&&GameCalendar.TURNID==turn+1&&playable()){
            if(Boolean.getBoolean("coop.battle"))signal("host-military-next",militaryState());
            signal("host-next",CFG.core.getCiv(country).getGold()+","+GameCalendar.TURNID);phase=5;
          }
          if(phase==5&&exists("client-pass")&&(!Boolean.getBoolean("coop.shared")||MP.clients.isEmpty())){
            if(Boolean.getBoolean("coop.patch")&&!Boolean.getBoolean("coop.relay")&&!capPassed)return;
            if(Boolean.getBoolean("coop.shared")&&!CFG.core.getCiv(country).getIsPlayer())throw new AssertionError("Guest departure disabled host country");
            System.out.println("VENDOR_NET: PASS host two-process investment and native turn; departing guest preserves host control");Runtime.getRuntime().halt(0);
          }
        }else{
          if(phase==1&&MP.picking&&CFG.core.getCivsSize()>2&&!MP.pickPending()){
            country=1;int hostCiv=Integer.parseInt(read("host-ready"));while(country==hostCiv||CFG.core.getCiv(country).getNumOfProvs()==0)country++;
            if(Boolean.getBoolean("coop.shared"))country=hostCiv;
            if(Boolean.getBoolean("coop.patch")&&SharedSupport.shared!=Boolean.getBoolean("coop.shared"))throw new AssertionError("Host mode not received");
            MP.pick(country);phase=2;
          }
          if(phase==2&&MP.myCiv==country&&playable()&&!MP.picking){
            turn=GameCalendar.TURNID;gold=CFG.core.getCiv(country).getGold();province=-1;
            for(int i=0;i<CFG.core.getCiv(country).getNumOfProvs();i++){
              int p=CFG.core.getCiv(country).getProvID(i);int a=Math.min(100,GameManager.invest_MaxEconomy_Gold(p,country));
              if(a>0&&GameManager.invest_EconomyByGold(p,a)>0){province=p;amount=a;break;}
            }
            if(province<0)throw new IllegalStateException("No investment candidate");
            if(Boolean.getBoolean("coop.patch")){MP.endTurnPressed();if(!MP.ready)throw new AssertionError("Initial readiness missing");}
            boolean accepted=GameManager.invest(province,country,amount);
            if(Boolean.getBoolean("coop.patch")&&MP.ready)throw new AssertionError("Order failed to withdraw readiness");
            if(!accepted&&!Boolean.getBoolean("coop.shared"))throw new IllegalStateException("Native investment rejected");
            if(CFG.core.getCiv(country).getGold()!=(Boolean.getBoolean("coop.shared")?gold:gold-amount))throw new AssertionError("Client early charge mismatch");
            signal("investment",country+","+province+","+amount+","+gold+","+turn);phase=3;
          }
          if(phase==3&&exists("investment-ok")&&CFG.core.getCiv(country).getGold()==Long.parseLong(read("investment-ok"))&&(!Boolean.getBoolean("coop.patch")||!SharedSupport.waitBeforeReady())){
            if(Boolean.getBoolean("coop.battle")){
              String[] f=read("battle-fixture").split(",");battleFrom=Integer.parseInt(f[0]);battleTo=Integer.parseInt(f[1]);enemy=Integer.parseInt(f[2]);
              CFG.core.declareWar(country,enemy,false);
              if(CFG.core.getCivsAtWar(country,enemy))throw new AssertionError("War executed before approval");phase=7;
            }else if(Boolean.getBoolean("coop.army")){
              var civ=CFG.core.getCiv(country);
              for(int i=0;i<civ.getNumOfProvs();i++){int p=civ.getProvID(i);if(CFG.core.getProv(p).getCivId()==country&&CFG.core.getProv(p).getTrueOwnerOfProv()==country&&CFG.gameAction.gMARY(p,country)>=10){recruitProvince=p;break;}}
              if(recruitProvince<0)throw new AssertionError("No recruitment province");
              recruitBefore=CFG.core.getProv(recruitProvince).getArmyCivID1(country);
              long before=civ.getGold();civ.recruitArmy(recruitProvince,10);
              if(civ.getGold()!=before)throw new AssertionError("Recruitment spent before approval");
              signal("army-request",Integer.toString(recruitProvince));phase=6;
            }else{MP.endTurnPressed();if(!MP.ready)throw new AssertionError("Guest readiness not set");MP.endTurnPressed();if(MP.ready)throw new AssertionError("Guest cancellation failed");MP.endTurnPressed();if(!MP.ready)throw new AssertionError("Guest re-readiness failed");signal("guest-ready","ok");phase=4;}
          }
          if(phase==7&&exists("war-ok")&&CFG.core.getCivsAtWar(country,enemy)&&!SharedSupport.waitBeforeReady()){
            int before=CFG.core.getProv(battleFrom).getArmyCivID1(country);
            CFG.gameAction.moveArmyAction(battleFrom,battleTo,1,country,false,true);
            if(CFG.core.getProv(battleFrom).getArmyCivID1(country)!=before)throw new AssertionError("Attack executed before approval");phase=8;
          }
          if(phase==8&&exists("battle-move-ok")&&!SharedSupport.waitBeforeReady()){
            if(!militaryState().equals(read("battle-move-ok")))throw new AssertionError("Military state differs before turn");
            System.out.println("VENDOR_NET: PASS war declaration and attack order synchronized");MP.endTurnPressed();signal("guest-ready","ok");phase=4;
          }
          if(phase==6&&exists("army-ok")){
            var civ=CFG.core.getCiv(country);boolean found=false;
            for(int i=0;i<civ.getRecruitArmySize();i++)if(civ.getRecruitArmy(i).getProvinceID()==recruitProvince&&civ.getRecruitArmy(i).getArmy()==10)found=true;
            if(found){
              var local=new ArrayList<>(Arrays.asList(SharedState.capture().split("\n")));var remoteRows=new ArrayList<>(Arrays.asList(read("army-ok").split("\n")));Collections.sort(local);Collections.sort(remoteRows);
              if(!local.equals(remoteRows))throw new AssertionError("Shared army state differs after command snapshot");
              System.out.println("VENDOR_NET: PASS recruitment snapshot preserves armies, queues and movement balances");
              MP.endTurnPressed();signal("guest-ready","ok");phase=4;
            }
          }
          if(phase==4&&exists("host-next")&&GameCalendar.TURNID==turn+1&&playable()&&!MP.ready){
            String[] v=read("host-next").split(",");if(CFG.core.getCiv(country).getGold()!=Long.parseLong(v[0]))throw new AssertionError("Post-turn gold mismatch");
            if(recruitProvince>=0&&CFG.core.getProv(recruitProvince).getArmyCivID1(country)<recruitBefore+10)throw new AssertionError("Recruited soldiers missing");
            if(Boolean.getBoolean("coop.battle")){
              if(!militaryState().equals(read("host-military-next")))throw new AssertionError("Post-battle military state differs");
              System.out.println("VENDOR_NET: PASS post-turn army, orders, country gold/alliance and province ownership match");
            }
            signal("client-pass","ok");System.out.println("VENDOR_NET: PASS client joined, selected country, invested and restored next turn; shared="+Boolean.getBoolean("coop.shared"));Runtime.getRuntime().halt(0);
          }
        }
      }catch(Throwable t){t.printStackTrace();Runtime.getRuntime().halt(3);}}
    },config);
  }
}




