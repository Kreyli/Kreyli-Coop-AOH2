package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.*;
import aoc.kingdoms.lukasz.jakowski.AA_Game;
import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.ScreenUtils;
import java.nio.file.*;
import java.nio.file.Files;
import java.net.ServerSocket;

/** Runs only against a marked disposable game copy; never initializes Steam or sends an invite. */
public final class KreyliUiProbe {
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void capture(String name){
        var pixmap=ScreenUtils.getFrameBufferPixmap(0,0,Gdx.graphics.getBackBufferWidth(),Gdx.graphics.getBackBufferHeight());
        PixmapIO.writePNG(Gdx.files.local(name),pixmap,6,true);pixmap.dispose();
    }
    public static void main(String[] args)throws Exception{
        if(!Files.exists(Path.of(".coop-test-copy")))throw new IllegalStateException("Test copy required");
        System.setProperty("kreyli.steam.disabled","true");
        var remote=MP.class.getDeclaredField("remoteRequested");remote.setAccessible(true);remote.setBoolean(null,true);
        int port;try(var socket=new ServerSocket(0)){port=socket.getLocalPort();}
        final int testPort=port;
        var config=new Lwjgl3ApplicationConfiguration();config.setWindowedMode(1280,720);config.setInitialVisible(false);config.disableAudio(true);config.setForegroundFPS(30);
        Thread timer=new Thread(()->{try{Thread.sleep(150000);}catch(InterruptedException e){Thread.currentThread().interrupt();}Runtime.getRuntime().halt(2);});timer.setDaemon(true);timer.start();
        new Lwjgl3Application(new AA_Game(){int frame;Menu players;
            @Override public void render(){try{
                super.render();frame++;
                if(frame==300){MP.listInLobby=false;MP.hostDirect=true;MP.port=testPort;MP.nick="Kreyli UI Test";SharedSupport.shared=true;MP_UI.open(View.eMP_HOST);}
                if(frame==330){capture("kreyli-host-shared.png");SharedSupport.shared=false;}
                if(frame==360){capture("kreyli-host-separate.png");SharedSupport.shared=true;
                    ((MP_Form)CFG.menus.getActiveMenu().get(0)).onButton(0);
                    require(MP.isHost(),"Host creation");
                    require(CFG.menus.getActiveMenu().get(0).getMenuElemsSize()==9,"Host room must replace setup with five rows and four buttons");
                }
                if(frame==390){capture("kreyli-room.png");
                    ((MP_Form)CFG.menus.getActiveMenu().get(0)).onButton(1);
                    require(MP.isHost(),"Disabled Steam invite must preserve LAN host");
                    players=MP_UI.playersMenu();players.setVisibleM(true);CFG.menus.getActiveMenu().set(0,players);
                    require(players.getMenuElem(players.getMenuElemsSize()-2).getTextE().contains("Steam"),"Player table invite button");
                    int count=players.getMenuElemsSize();KreyliMenus.appendInvite(players);require(count==players.getMenuElemsSize(),"No duplicate button");
                    require(!KreyliMenus.inviteAction(players,0),"Native table action remains native");
                }
                if(frame==420){capture("kreyli-players.png");players.actionEL(players.getMenuElemsSize()-2);require(MP.isHost(),"Player table invite preserves host");MP.stop();System.out.println("KREYLI_UI: PASS shared/separate forms, room, table, disabled-Steam invite actions");Runtime.getRuntime().halt(0);}
            }catch(Throwable t){t.printStackTrace();MP.stop();Runtime.getRuntime().halt(3);}}
        },config);
    }
}

