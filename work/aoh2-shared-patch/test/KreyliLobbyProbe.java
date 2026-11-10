package age.of.civilizations2.jakowski.lukasz.mp;
import age.of.civilizations2.jakowski.lukasz.*;
import aoc.kingdoms.lukasz.jakowski.AA_Game;
import com.badlogic.gdx.backends.lwjgl3.*;
import java.nio.file.*;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;

/** Two disposable native clients exercise pregame readiness, transition and disconnect. */
public final class KreyliLobbyProbe {
    static volatile boolean capacityPassed;
    static void verifyCapacity(int port)throws ReflectiveOperationException{
        var versionField=MP.class.getDeclaredField("VERSION");
        versionField.setAccessible(true);
        String version=(String)versionField.get(null), password=MP.password;
        var worker=new Thread(()->{
            var sockets=new ArrayList<Socket>();
            try{
                for(int i=1;i<=KreyliSteam.MAX_PLAYERS;i++){
                    var socket=new Socket("127.0.0.1",port);
                    sockets.add(socket);socket.setSoTimeout(15000);
                    byte[] hello=MP_Net.encode("hello",version,"Seat"+i,password);
                    var out=new DataOutputStream(socket.getOutputStream());
                    out.writeInt(hello.length);out.write(hello);out.flush();
                    var in=new DataInputStream(socket.getInputStream());
                    int size=in.readInt();
                    require(size>0&&size<65536,"Valid handshake frame");
                    Object[] reply=MP_Net.decode(in.readNBytes(size));
                    if(i<KreyliSteam.MAX_PLAYERS)require("welcome".equals(reply[0]),"Host accepts guest "+i);
                    else require("reject".equals(reply[0])&&"Kreyli_LobbyFull".equals(reply[1]),"Eleventh player rejected");
                }
                capacityPassed=true;
            }catch(Throwable e){e.printStackTrace();Runtime.getRuntime().halt(9);}
            finally{for(var socket:sockets)try{socket.close();}catch(IOException ignored){}}
        },"lobby-capacity-probe");
        worker.setDaemon(true);worker.start();
    }
    static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static boolean state(String name)throws Exception{var field=KreyliLobby.class.getDeclaredField(name);field.setAccessible(true);return field.getBoolean(null);}
    static void click(int index){
        Menu menu=CFG.menus.getActiveMenu().get(0);var button=menu.getMenuElem(index);
        int x=menu.getPosX()+button.getPosXE()+button.getWidthE()/2;
        int y=menu.getPosY()+button.getPosY()+button.getHeightE()/2;
        Touch touch=new Touch();touch.actionMove_Hover(x,y);touch.actionDown(x,y,0,0);touch.actionUp(x,y,0,0);
    }
    public static void main(String[] args)throws Exception{
        if(!Files.exists(Path.of(".coop-test-copy")))throw new IllegalStateException("Disposable copy required");
        boolean host=args[0].equals("host");Path channel=Path.of(args[1]);int port=Integer.parseInt(args[2]);
        var remote=MP.class.getDeclaredField("remoteRequested");remote.setAccessible(true);remote.setBoolean(null,true);
        var config=new Lwjgl3ApplicationConfiguration();config.setWindowedMode(1280,720);config.setInitialVisible(false);config.disableAudio(true);config.setForegroundFPS(30);
        var watchdog=new Thread(()->{try{Thread.sleep(480000);}catch(InterruptedException ignored){return;}Runtime.getRuntime().halt(8);});watchdog.setDaemon(true);watchdog.start();
        new Lwjgl3Application(new AA_Game(){int frame,phase; boolean initialShared;
            @Override public void render(){try{
                super.render();frame++;
                if(frame==299&&host){
                    KreyliUiProbe.verifyProfile();
                    KreyliUiProbe.verifyBranding();
                    MP_UI.open(View.eMAINMENU);
                    Menu main=CFG.menus.getActiveMenu().get(0);
                    require(main.getMenuElem(1).getVisibleE(),"Single player button visible");
                    main.actionEL(1);require(CFG.menus.getInGamesMenu()&&!MP.isOn(),"Single player selector");
                }
                if(phase==0&&frame>=300&&(host||Files.exists(channel.resolve("ready")))){
                    require(!KreyliProfile.steamLinked()&&KreyliProfile.nick().length()>=2,"Offline profile available without Steam");
                    for(int i=0;i<5&&KreyliProfile.avatar()==null;i++)KreyliProfile.cycleAvatar();
                    require(KreyliProfile.avatar()!=null,"Custom local avatar generated");
                    MP.nick=host?"KreyliHost":"KreyliGuest";MP.port=port;MP.password="test-only";MP.listInLobby=false;MP.hostDirect=true;
                    if(host){MP_UI.open(View.eMP);MP.port=port;MP.hostDirect=true;MP.listInLobby=false;KreyliTurns.saveWaitSetting(30);click(1);require(MP.isHost()&&CFG.menus.getActiveMenu().get(0) instanceof KreyliLobbyRoom,"Host opens room directly");Files.writeString(channel.resolve("ready"),"ok");}
                    else{MP.password="";MP.address="127.0.0.1:"+port;MP.join();MP_UI.open(View.eMP_JOINING);}
                    phase=1;
                }
                if(host){
                    if(phase==1&&MP.clients.size()==1&&KreyliLobby.gate.members.size()==1){
                        require(!KreyliLobby.gate.released,"No implicit start");initialShared=SharedSupport.shared;
                        Menu room=CFG.menus.getActiveMenu().get(0);
                        require(room instanceof KreyliLobbyRoom&&room.getMenuElemsSize()==15,"Settings inside room without Apply or Region");
                        click(KreyliLobbyRoom.MODE);
                        require(SharedSupport.shared!=initialShared,"Country mode applies immediately");
                        click(KreyliLobbyRoom.TIMER);
                        require(MP.turnSeconds==45,"Timer applies immediately");
                        click(KreyliLobbyRoom.PASSWORD);
                        CFG.keybMess="changed";
                        CFG.keyboardSave.action();
                        phase=5;return;
                    }
                    if(phase==5&&CFG.menus.getActiveMenu().get(0) instanceof KreyliLobbyRoom){
                        require(SharedSupport.shared!=initialShared&&MP.turnSeconds==45&&"changed".equals(MP.password)&&MP.clients.size()==1,"Settings apply with guest connected");
                        CFG.menus.getActiveMenu().get(0).actionEL(KreyliLobbyRoom.READY);
                        require(!KreyliLobby.gate.released,"Host ready alone does not start");Files.writeString(channel.resolve("host-ready"),"ok");phase=2;
                    }
                    if(phase==2&&KreyliLobby.gate.released){
                        require(CFG.menus.getInGamesMenu(),"Both-ready opens campaign selector");
                        require(MP.clients.get(0).nick.equals("RenamedGuest")&&KreyliProfile.avatar("RenamedGuest")!=null,
                                "Guest nickname and avatar propagated to host");
                        KreyliUiProbe.capture("kreyli-game-selector.png");Files.writeString(channel.resolve("released"),"ok");phase=3;
                    }
                    if(phase==3&&MP.clients.isEmpty()&&KreyliLobby.gate.members.isEmpty()){
                        require(!KreyliLobby.gate.released&&!KreyliLobby.gate.hostReady&&KreyliLobby.gate.ready.isEmpty(),"Departure reset");
                        require(MP_UI.inView(View.eMP_HOST),"Departure returns host to lobby");
                        verifyCapacity(port);phase=6;
                    }
                    if(phase==6&&capacityPassed){MP.stop();System.out.println("KREYLI_LOBBY_NATIVE: PASS readiness, departure reset and ten-player capacity");Runtime.getRuntime().halt(0);}
                }else{
                    if(phase==1&&state("active")&&Files.exists(channel.resolve("host-ready"))){
                        require(MP_UI.inView(View.eMP_JOINING),"Guest lobby visible");
                        require(CFG.menus.getActiveMenu().get(0).getMenuElemsSize()==11,"Guest room layout");
                        require(KreyliLobby.timerLabel(45).equals(KreyliLobby.timerLabel()),"Timer synchronized");
                        KreyliProfile.setNickname(KreyliLobby.hostDisplay());phase=4;return;
                    }
                    if(phase==4&&frame%60==0){
                        require(!MP.nick.equalsIgnoreCase(KreyliLobby.hostDisplay()),"Duplicate nickname rejected");
                        KreyliProfile.setNickname("RenamedGuest");phase=5;
                    }
                    if(phase==5&&MP.nick.equals("RenamedGuest")&&KreyliProfile.avatar(KreyliLobby.hostDisplay())!=null){
                        KreyliUiProbe.capture("kreyli-lobby-guest.png");
                        CFG.menus.getActiveMenu().get(0).actionEL(KreyliLobbyRoom.READY);phase=2;
                    }
                    if(phase==2&&state("choosing")&&Files.exists(channel.resolve("released"))){
                        require(state("hostReady")&&state("guestReady"),"Guest receives both ready");
                        click(KreyliLobbyRoom.LEAVE);phase=3;
                    }
                    if(phase==3&&!MP.isClient()){
                        require(MP_UI.inView(View.eMP),"Exit button returns to multiplayer menu");
                        Files.writeString(channel.resolve("client-pass"),"ok");System.out.println("KREYLI_LOBBY_NATIVE: PASS guest roster, ready, released state and exit button");Runtime.getRuntime().halt(0);
                    }
                }
            }catch(Throwable error){error.printStackTrace();Runtime.getRuntime().halt(3);}}
        },config);
    }
}




