package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.CFG;
import age.of.civilizations2.jakowski.lukasz.View;
import aoc.kingdoms.lukasz.jakowski.AA_Game;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import java.io.DataInputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;

public final class KreyliQuickGameProbe {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        require(Files.exists(Path.of(".coop-test-copy")),"Disposable test only");
        int port=Integer.parseInt(args[0]);
        var remote=MP.class.getDeclaredField("remoteRequested");
        remote.setAccessible(true);
        remote.setBoolean(null,true);
        var version=MP.class.getDeclaredField("VERSION");
        version.setAccessible(true);
        var deadline=KreyliQuickGame.class.getDeclaredField("matchDeadline");
        deadline.setAccessible(true);
        var watchdog=new Thread(()->{try{Thread.sleep(240_000);}catch(InterruptedException ignored){return;}Runtime.getRuntime().halt(8);});
        watchdog.setDaemon(true);
        watchdog.start();
        var config=new Lwjgl3ApplicationConfiguration();
        config.setWindowedMode(1280,720);
        config.setInitialVisible(false);
        config.disableAudio(true);
        new Lwjgl3Application(new AA_Game(){
            int phase;
            @Override public void render(){
                try{
                    super.render();
                    if(phase==0&&CFG.menus.getIn_MainMenu()){
                        MP.nick="QuickHost";MP.port=port;MP.password="quick-probe";MP.hostDirect=true;MP.listInLobby=false;MP.turnSeconds=20;
                        require(MP.startHost(),"Start local host");
                        KreyliQuickGame.armHost(3);
                        MP_UI.open(View.eMP_HOST);
                        for(int i=1;i<=2;i++){
                            String nick="QuickGuest"+i;
                            var worker=new Thread(()->{
                                try(var socket=new Socket("127.0.0.1",port)){
                                    KreyliLobbyProbe.send(socket,"hello",version.get(null),nick,"quick-probe");
                                    var input=new DataInputStream(socket.getInputStream());
                                    require("welcome".equals(KreyliLobbyProbe.packet(input)[0]),"Guest handshake");
                                    while(true)KreyliLobbyProbe.packet(input);
                                }catch(java.io.EOFException ignored){}
                                catch(Throwable error){error.printStackTrace();Runtime.getRuntime().halt(4);}
                            });
                            worker.setDaemon(true);worker.start();
                        }
                        phase=1;
                    }
                    if(phase==1&&KreyliQuickGame.running()){
                        var countries=new HashSet<Integer>();
                        countries.add(CFG.core.getPlayer(0).getCivId());
                        for(var guest:MP.clients){require(guest.civ>0,"Guest assigned automatically");countries.add(guest.civ);}
                        require(countries.size()==3,"Three distinct countries");
                        var continents=new HashSet<Integer>();
                        for(int civ:countries)continents.add(CFG.core.getProv(CFG.core.getCiv(civ).getCapitalProvID()).getContinent());
                        require(continents.size()==1,"All countries on same continent");
                        deadline.setLong(null,System.nanoTime());
                        phase=2;
                    }
                    if(phase==2&&KreyliQuickGame.finished()){
                        KreyliUiProbe.capture("kreyli-quick-results.png");
                        System.out.println("KREYLI_QUICK_GAME: PASS three players, scenario load, unique countries on one continent, timed results");
                        Runtime.getRuntime().halt(0);
                    }
                }catch(Throwable error){error.printStackTrace();Runtime.getRuntime().halt(3);}
            }
        },config);
    }
}
