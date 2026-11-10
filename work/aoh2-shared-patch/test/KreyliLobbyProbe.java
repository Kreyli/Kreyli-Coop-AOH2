package age.of.civilizations2.jakowski.lukasz.mp;
import age.of.civilizations2.jakowski.lukasz.*;
import aoc.kingdoms.lukasz.jakowski.AA_Game;
import com.badlogic.gdx.backends.lwjgl3.*;
import java.nio.file.*;
import java.io.*;
import java.net.Socket;
import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import age.of.civilizations2.jakowski.lukasz.Menus.NewGame.Menu_CreateNewGame;

/** Two disposable native clients exercise pregame readiness, transition and disconnect. */
public final class KreyliLobbyProbe {
    static volatile boolean capacityPassed, listingPassed;
    static CountDownLatch delayedListing(int port)throws IOException{
        var relay=new ServerSocket(0,1,java.net.InetAddress.getLoopbackAddress());
        MP.regionName="Local test relay";
        MP.regions=List.<String[]>of(new String[]{"127.0.0.1:"+relay.getLocalPort(),MP.regionName});
        var hostReturned=new CountDownLatch(1);
        var worker=new Thread(()->{
            try(relay;var peer=relay.accept()){
                peer.setSoTimeout(15000);
                require(("R "+port).equals(MP_Net.readLine(peer.getInputStream())),"Native direct listing handshake");
                require(hostReturned.await(5,TimeUnit.SECONDS),"Starting LAN room must not wait for relay registration");
                MP_Net.writeLine(peer.getOutputStream(),"OK");
                require(MP_Net.readLine(peer.getInputStream()).startsWith("I\t1\t"),"Completed async relay is adopted by host");
                listingPassed=true;
                peer.setSoTimeout(0);
                while(peer.getInputStream().read()>=0){}
            }catch(Throwable error){error.printStackTrace();Runtime.getRuntime().halt(10);}
        },"delayed-listing-probe");
        worker.setDaemon(true);worker.start();
        return hostReturned;
    }
    static final ConcurrentHashMap<String,Integer> assigned=new ConcurrentHashMap<>();
    static final Set<String> denied=ConcurrentHashMap.newKeySet();

    static Object[] packet(DataInputStream in)throws IOException{
        int size=in.readInt();
        require(size>0&&size<=64*1024*1024,"Valid frame");
        return MP_Net.decode(in.readNBytes(size));
    }
    static void send(Socket socket,Object... values)throws IOException{
        byte[] frame=MP_Net.encode(values);
        var out=new DataOutputStream(socket.getOutputStream());
        out.writeInt(frame.length);out.write(frame);out.flush();
    }
    static void await(BooleanSupplier done,String message)throws InterruptedException{
        long deadline=System.nanoTime()+20_000_000_000L;
        while(!done.getAsBoolean()&&System.nanoTime()<deadline)Thread.sleep(20);
        require(done.getAsBoolean(),message);
    }
    static void verifyCapacity(int port,int[] civs)throws ReflectiveOperationException{
        var versionField=MP.class.getDeclaredField("VERSION");
        versionField.setAccessible(true);
        String version=(String)versionField.get(null), password=MP.password;
        var worker=new Thread(()->{
            var sockets=new ArrayList<Socket>();
            try{
                for(int i=1;i<=KreyliSteam.MAX_PLAYERS;i++){
                    String name="Seat"+i;
                    var socket=new Socket("127.0.0.1",port);
                    sockets.add(socket);socket.setSoTimeout(15000);
                    send(socket,"hello",version,name,password);
                    var in=new DataInputStream(socket.getInputStream());
                    Object[] reply=packet(in);
                    if(i<KreyliSteam.MAX_PLAYERS){
                        require("welcome".equals(reply[0]),"Host accepts guest "+i);
                        socket.setSoTimeout(0);
                        Thread reader=new Thread(()->{
                            try{while(true)if("kreyli_country_denied".equals(packet(in)[0]))denied.add(name);}
                            catch(IOException closed){}
                        },"seat-reader-"+i);
                        reader.setDaemon(true);reader.start();
                    }else require("reject".equals(reply[0])&&"Kreyli_LobbyFull".equals(reply[1]),"Eleventh player rejected");
                }
                int[] countries={civs[0],civs[0],civs[1],civs[1],civs[1],civs[1],civs[2],civs[3],civs[3]};
                int[] limits={3,3,4,4,4,4,1,2,2};
                for(int i=0;i<countries.length;i++){
                    String name="Seat"+(i+1);
                    if(i==2||i==7){
                        send(sockets.get(i),"kreyli_country_pick",civs[i==2?0:2],10);
                        await(()->denied.contains(name),"Full or solo country rejects "+name);
                    }
                    int civ=countries[i];
                    send(sockets.get(i),"kreyli_country_pick",civ,limits[i]);
                    await(()->assigned.getOrDefault(name,-1)==civ,"Seat assigned: "+name);
                }
                capacityPassed=true;
                Thread.sleep(30000);
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
        new Lwjgl3Application(new AA_Game(){int frame,phase;int[] testCountries;
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
                    var source=new java.awt.image.BufferedImage(360,240,java.awt.image.BufferedImage.TYPE_INT_RGB);
                    source.setRGB(180,120,host?0xffa000:0x1144ff);
                    var bytes=new ByteArrayOutputStream();
                    javax.imageio.ImageIO.write(source,"PNG",bytes);
                    KreyliProfile.selectAvatar(KreyliProfile.addAvatar(KreyliProfile.originalAvatar(bytes.toByteArray())));
                    require(KreyliProfile.avatar()!=null,"Custom local avatar generated");
                    MP.nick=host?"KreyliHost":"KreyliGuest";MP.port=port;MP.password="test-only";MP.listInLobby=false;MP.hostDirect=true;
                    if(host){
                        MP_UI.open(View.eMP);MP.port=port;MP.hostDirect=true;MP.listInLobby=true;
                        KreyliTurns.saveWaitSetting(30);
                        CountDownLatch hostReturned=delayedListing(port);
                        click(1);
                        require(MP.isHost()&&CFG.menus.getActiveMenu().get(0) instanceof KreyliLobbyRoom,"Host opens room directly");
                        hostReturned.countDown();
                        Files.writeString(channel.resolve("ready"),"ok");
                    }
                    else{MP.password="";MP.address="127.0.0.1:"+port;MP.join();MP_UI.open(View.eMP_JOINING);}
                    phase=1;
                }
                if(host){
                    if(phase==1&&MP.clients.size()==1&&KreyliLobby.gate.members.size()==1){
                        require(!KreyliLobby.gate.released,"No implicit start");
                        Menu room=CFG.menus.getActiveMenu().get(0);
                        require(room instanceof KreyliLobbyRoom&&room.getMenuElemsSize()==14,"Settings inside room without Apply or Region");
                        require(room.getMenuElem(KreyliLobbyRoom.PORT).getVisibleE(),"LAN port visible in LAN room");
                        MP.hostDirect=false;
                        require(!room.getMenuElem(KreyliLobbyRoom.PORT).getVisibleE(),"LAN port hidden in Steam room");
                        MP.relayCode="TEST42";
                        require(room.getMenuElem(13).getIsClickable(),"Room code can be clicked");
                        MP.relayCode=null;
                        MP.hostDirect=true;
                        click(KreyliLobbyRoom.TIMER);
                        require(MP.turnSeconds==45,"Timer applies immediately");
                        click(KreyliLobbyRoom.PASSWORD);
                        CFG.keybMess="changed";
                        CFG.keyboardSave.action();
                        phase=5;return;
                    }
                    if(phase==5&&CFG.menus.getActiveMenu().get(0) instanceof KreyliLobbyRoom){
                        require(MP.turnSeconds==45&&"changed".equals(MP.password)&&MP.clients.size()==1,"Settings apply with guest connected");
                        CFG.menus.getActiveMenu().get(0).actionEL(KreyliLobbyRoom.READY);
                        require(!KreyliLobby.gate.released,"Host ready alone does not start");Files.writeString(channel.resolve("host-ready"),"ok");phase=2;
                    }
                    if(phase==2&&KreyliLobby.gate.released){
                        require(CFG.menus.getInGamesMenu(),"Both-ready opens campaign selector");
                        require(MP.clients.get(0).nick.equals("RenamedGuest")&&KreyliProfile.avatar("RenamedGuest")!=null&&KreyliProfile.avatar("RenamedGuest").getRegionWidth()==360&&KreyliProfile.avatar("RenamedGuest").getRegionHeight()==240,
                                "Guest nickname and avatar propagated to host");
                        KreyliUiProbe.capture("kreyli-game-selector.png");Files.writeString(channel.resolve("released"),"ok");phase=3;
                    }
                    if(phase==3&&MP.clients.isEmpty()&&KreyliLobby.gate.members.isEmpty()){
                        require(!KreyliLobby.gate.released&&!KreyliLobby.gate.hostReady&&KreyliLobby.gate.ready.isEmpty(),"Departure reset");
                        require(MP_UI.inView(View.eMP_HOST),"Departure returns host to lobby");
                        testCountries=new int[4];
                        int next=0;
                        for(int civ=1;civ<CFG.core.getCivsSize()&&next<testCountries.length;civ++)
                            if(CFG.core.getCiv(civ).getNumOfProvs()>0)testCountries[next++]=civ;
                        require(next==4,"Four playable test countries");
                        CFG.core.getPlayer(0).setCivId(testCountries[0]);
                        KreyliCountrySeats.configureHost(testCountries[0],3);
                        Menu_CreateNewGame.newGame();
                        phase=6;
                    }
                    if(phase==6&&MP.started&&MP.inputOrders()){
                        verifyCapacity(port,testCountries);phase=7;
                    }
                    if(phase==7){
                        for(var guest:MP.clients)if(guest.nick!=null)assigned.put(guest.nick,guest.civ);
                        if(capacityPassed&&listingPassed){
                            require(MP.clients.size()==9,"Ten connected participants");
                            MP.stop();System.out.println("KREYLI_LOBBY_NATIVE: PASS asynchronous LAN listing, nine connected guests, mixed country capacities 3/4/1/2, overflow rejected");
                            Runtime.getRuntime().halt(0);
                        }
                    }
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
                    if(phase==5&&MP.nick.equals("RenamedGuest")&&KreyliProfile.avatar(KreyliLobby.hostDisplay())!=null&&KreyliProfile.stats(KreyliLobby.hostDisplay())!=null){
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




