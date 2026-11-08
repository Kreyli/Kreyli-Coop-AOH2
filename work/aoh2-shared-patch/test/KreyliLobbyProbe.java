package age.of.civilizations2.jakowski.lukasz.mp;
import age.of.civilizations2.jakowski.lukasz.*;
import aoc.kingdoms.lukasz.jakowski.AA_Game;
import com.badlogic.gdx.backends.lwjgl3.*;
import java.nio.file.*;

/** Two disposable native clients exercise pregame readiness, transition and disconnect. */
public final class KreyliLobbyProbe {
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
        var watchdog=new Thread(()->{try{Thread.sleep(180000);}catch(Exception ignored){}Runtime.getRuntime().halt(8);});watchdog.setDaemon(true);watchdog.start();
        new Lwjgl3Application(new AA_Game(){int frame,phase; boolean initialShared;
            @Override public void render(){try{
                super.render();frame++;
                if(frame==299&&host){
                    MP_UI.open(View.eMAINMENU);
                    Menu main=CFG.menus.getActiveMenu().get(0);
                    require(main.getMenuElem(1).getVisibleE(),"Single player button visible");
                    main.actionEL(1);require(CFG.menus.getInGamesMenu()&&!MP.isOn(),"Single player selector");
                }
                if(frame==300){
                    MP.nick=host?"KreyliHost":"KreyliGuest";MP.port=port;MP.password="test-only";MP.listInLobby=false;MP.hostDirect=true;
                    if(host){MP_UI.open(View.eMP);MP.port=port;MP.hostDirect=true;MP.listInLobby=false;MP.turnSeconds=0;click(2);require(MP.isHost()&&CFG.menus.getActiveMenu().get(0) instanceof KreyliLobbyRoom,"Host opens room directly");Files.writeString(channel.resolve("ready"),"ok");}
                    else{MP.password="";MP.address="127.0.0.1:"+port;MP.join();MP_UI.open(View.eMP_JOINING);}
                    phase=1;
                }
                if(host){
                    if(phase==1&&MP.clients.size()==1&&KreyliLobby.gate.guest!=null){
                        require(!KreyliLobby.gate.released,"No implicit start");initialShared=SharedSupport.shared;
                        Menu room=CFG.menus.getActiveMenu().get(0);
                        require(room instanceof KreyliLobbyRoom&&room.getMenuElemsSize()==17,"Settings inside room");
                        click(KreyliLobbyRoom.MODE);
                        click(KreyliLobbyRoom.TIMER);
                        room.getMenuElem(KreyliLobbyRoom.PASSWORD).setTextE("changed");
                        click(KreyliLobbyRoom.APPLY);
                        phase=5;return;
                    }
                    if(phase==5&&CFG.menus.getActiveMenu().get(0) instanceof KreyliLobbyRoom){
                        require(SharedSupport.shared!=initialShared&&MP.turnSeconds==60&&"changed".equals(MP.password)&&MP.clients.size()==1,"Settings apply with guest connected");
                        CFG.menus.getActiveMenu().get(0).actionEL(KreyliLobbyRoom.READY);
                        require(!KreyliLobby.gate.released,"Host ready alone does not start");Files.writeString(channel.resolve("host-ready"),"ok");phase=2;
                    }
                    if(phase==2&&KreyliLobby.gate.released){require(CFG.menus.getInGamesMenu(),"Both-ready opens campaign selector");KreyliUiProbe.capture("kreyli-game-selector.png");Files.writeString(channel.resolve("released"),"ok");phase=3;}
                    if(phase==3&&MP.clients.isEmpty()&&KreyliLobby.gate.guest==null){
                        require(!KreyliLobby.gate.released&&!KreyliLobby.gate.hostReady&&!KreyliLobby.gate.guestReady,"Departure reset");
                        require(MP_UI.inView(View.eMP_HOST),"Departure returns host to lobby");
                        MP.stop();System.out.println("KREYLI_LOBBY_NATIVE: PASS host ready barrier, selector and departure reset");Runtime.getRuntime().halt(0);
                    }
                }else{
                    if(phase==1&&state("active")&&Files.exists(channel.resolve("host-ready"))){
                        require(MP_UI.inView(View.eMP_JOINING),"Guest lobby visible");
                        require(CFG.menus.getActiveMenu().get(0).getMenuElemsSize()==9,"Guest room layout");require("60 с".equals(KreyliLobby.timerLabel()),"Timer synchronized");KreyliUiProbe.capture("kreyli-lobby-guest.png");CFG.menus.getActiveMenu().get(0).actionEL(KreyliLobbyRoom.READY);phase=2;
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




