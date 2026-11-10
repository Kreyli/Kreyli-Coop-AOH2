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
    static void verifyProfile()throws Exception{
        MP_UI.open(View.eMAINMENU);
        KreyliProfile.resetNickname();
        String name=KreyliProfile.nick();
        require(!KreyliProfile.steamLinked()&&!name.isBlank()&&name.equals(KreyliProfile.nick()),
                "Steam-disabled profile has a stable local nickname");
        InputProcessor previous=Gdx.input.getInputProcessor();
        Touch touch=new Touch();
        int x=CFG.PADD+3,y=CFG.PADD+3;
        touch.actionDown(x,y,0,Input.Buttons.LEFT);
        touch.actionUp(x,y,0,Input.Buttons.LEFT);
        InputProcessor profile=Gdx.input.getInputProcessor();
        require(profile!=null&&profile!=previous,"Profile badge opens the profile overlay");
        int row=Math.max(CFG.BUTTON_H/2,CFG.TEXT_HEIGHT_DEFAULT_SMALL+CFG.PADD*2);
        int width=Math.min(CFG.GAMEWIDTH-CFG.PADD*4,Math.max(460,CFG.BUTTON_W*4));
        int height=row*9+CFG.PADD*4,gap=CFG.PADD*2;
        int buttonWidth=(width-gap*4)/3;
        int buttonY=Math.max(CFG.PADD,(CFG.GAMEHEIGHT-height)/2)+height-row/2-gap;
        int buttonX=(CFG.GAMEWIDTH-width)/2+gap+buttonWidth/2;
        touch.actionDown(buttonX,buttonY,0,Input.Buttons.LEFT);
        touch.actionUp(buttonX,buttonY,0,Input.Buttons.LEFT);
        for(int i=0;i<name.length();i++)profile.keyTyped('\b');
        for(char c:"LocalEditorTest".toCharArray())profile.keyTyped(c);
        require(profile.keyUp(Input.Keys.ENTER)&&KreyliProfile.nick().equals("LocalEditorTest"),
                "Native profile input saves nickname in game");
        touch.actionDown(buttonX,buttonY,0,Input.Buttons.LEFT);
        touch.actionUp(buttonX,buttonY,0,Input.Buttons.LEFT);
        touch.actionDown(buttonX+buttonWidth+gap,buttonY,0,Input.Buttons.LEFT);
        touch.actionUp(buttonX+buttonWidth+gap,buttonY,0,Input.Buttons.LEFT);
        require(KreyliProfile.nick().equals(name),"Nickname default button restores the generated offline name");
        touch.actionDown(buttonX+buttonWidth+gap,buttonY,0,Input.Buttons.LEFT);
        touch.actionUp(buttonX+buttonWidth+gap,buttonY,0,Input.Buttons.LEFT);
        require(profile.keyUp(Input.Keys.ESCAPE)&&Gdx.input.getInputProcessor()==profile,
                "Avatar chooser returns to profile before closing");
        Path image=Files.createTempFile(Path.of("."),"kreyli-avatar-test-",".png");
        try{
            var source=new java.awt.image.BufferedImage(64,32,java.awt.image.BufferedImage.TYPE_INT_ARGB);
            source.setRGB(32,16,0xffafde12);
            javax.imageio.ImageIO.write(source,"PNG",image.toFile());
            byte[] pixels=KreyliProfileUi.decodeAvatar(image.toFile());
            require(pixels.length==128*128*4,"Avatar is normalized to bounded RGBA");
            int index=KreyliProfile.addAvatar(pixels);
            require(KreyliProfile.addAvatar(pixels)==index,"Repeated upload keeps one history entry");
            KreyliProfile.selectAvatar(index);
            require(KreyliProfile.selectedAvatar()==index&&KreyliProfile.avatar()!=null&&KreyliProfile.avatar().getRegionWidth()==128,"Uploaded avatar retains portrait resolution");
            KreyliProfile.selectAvatar(-1);
            require(KreyliProfile.selectedAvatar()==-1&&KreyliProfile.avatar()==null,"Default restores offline question mark");
        }finally{Files.deleteIfExists(image);}
        require(profile.keyUp(Input.Keys.ESCAPE),"Profile handles Escape");
        require(Gdx.input.getInputProcessor()==previous&&CFG.menus.getIn_MainMenu(),
                "Closing the profile restores the native input and keeps the original view");
    }
    static void verifyBranding(){
        MP_UI.open(View.eMP);
        Menu entry=CFG.menus.getActiveMenu().get(0);
        require(entry.getMenuElemsSize()==5,"Multiplayer menu contains only actions");
        MP_UI.open(View.eMP_BROWSER);
        var bar=CFG.menus.getActiveMenu().stream()
                .filter(menu->menu.getClass().getName().endsWith("MP_Browser$Bar")).findFirst().orElseThrow();
        require(!bar.getMenuElem(0).getVisibleE()&&!bar.getMenuElem(0).getIsClickable(),"Server browser nickname editor disabled");
        MP_UI.open(View.eMAINMENU);
        CFG.menus.getActiveMenu().get(0).actionEL(6);
        var texts=CFG.menus.getActiveMenu().stream().flatMap(menu->menu.menuElem.stream()).map(item->item.getTextE()).toList();
        require(texts.contains(KreyliText.t("Создатель мода: Kreyli","Mod creator: Kreyli"))
                &&texts.contains(KreyliText.t("Тестеры: -lockdown (Pin) и SpaceBodik","Testers: -lockdown (Pin) and SpaceBodik"))
                &&texts.contains(KreyliText.t("Спасибо, что играете!","Thanks for playing!"))
                &&texts.contains(KreyliText.t("Discord проекта","Project Discord")),"Main logo opens mod credits and Discord");
    }
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

