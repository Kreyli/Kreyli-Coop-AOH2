package age.of.civilizations2.jakowski.lukasz.mp;

import com.codedisaster.steamworks.*;
import age.of.civilizations2.jakowski.lukasz.View;
import java.lang.reflect.Proxy;
import java.util.Locale;
import java.util.ArrayList;

/** Steam is a rendezvous/overlay layer; the game protocol remains authoritative. */
public final class KreyliSteam {
    static final String PROTOCOL="kreyli-aoh2-2.2-ready-3";
    private static boolean overlayVisible;
    public static boolean overlayVisible(){return overlayVisible;}
    private static SteamFriends friends;
    private static SteamMatchmaking matchmaking;
    private static SteamUtils utils;
    private static SteamUser user;
    private static SteamID lobby, requested, localSteamID;
    private static boolean initialized, hosting, creating, nativeJoin, invitePending;
    private static long startupLobby, lastRefresh;
    private static String status="Steam не запущен — доступна игра по IP / LAN";
    private KreyliSteam() {}
    interface Event { void accept(String name,Object[] args); }
    @SuppressWarnings("unchecked")
    static <T>T callback(Class<T> type,Event event){
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,a)->{
            if(m.getDeclaringClass()==Object.class) return switch(m.getName()){
                case "hashCode" -> System.identityHashCode(p); case "equals" -> p==a[0]; default -> type.getSimpleName();};
            event.accept(m.getName(),a==null?new Object[0]:a);return null;
        });
    }
    public static void initialize(String[] args){
        if(Boolean.getBoolean("kreyli.steam.disabled")){status="Steam отключён — доступна игра по IP / LAN";return;}
        startupLobby=parseLobbyArgument(args);
        try{
            SteamAPI.loadLibraries();
            if(!SteamAPI.init())return;
            initialized=true;
            utils=new SteamUtils(()->{status="Steam закрыт — подключение по IP остаётся доступным";});
            if(utils.getAppID()!=480)throw new IllegalStateException("Expected Steam AppID 480, got "+utils.getAppID());
            friends=new SteamFriends(callback(SteamFriendsCallback.class,(name,a)->{
                if(name.equals("onGameOverlayActivated"))overlayVisible=Boolean.TRUE.equals(a[0]);
                if(name.equals("onGameLobbyJoinRequested"))requestJoin((SteamID)a[0]);
                if(name.equals("onGameRichPresenceJoinRequested")&&a[1] instanceof String command){
                    long id=parseLobbyArgument(command.trim().split("\s+"));
                    if(id!=0)requestJoin(SteamID.createFromNativeHandle(id));
                }
            }));
            matchmaking=new SteamMatchmaking(callback(SteamMatchmakingCallback.class,KreyliSteam::matchEvent));
            user=new SteamUser(callback(SteamUserCallback.class,(n,a)->{}));
            localSteamID=user.getSteamID();
            status="Steam: Spacewar — Shift+Tab";
            Runtime.getRuntime().addShutdownHook(new Thread(KreyliSteam::shutdown,"Kreyli-Steam-shutdown"));
            System.out.println("KREYLI_STEAM: initialized AppID="+utils.getAppID());
        }catch(Throwable e){System.err.println("KREYLI_STEAM: unavailable: "+e);shutdown();status="Steam недоступен — доступна игра по IP / LAN";}
    }
    static long parseLobbyArgument(String[] args){
        for(int i=0;i+1<args.length;i++)if(args[i].equals("+connect_lobby"))try{
            long id=Long.parseLong(args[i+1]);return id>0?id:0;
        }catch(NumberFormatException ignored){}
        return 0;
    }
    static boolean validRoom(String s){return s!=null&&s.matches("[A-Za-z0-9]{6}");}
    static boolean validRelay(String s){
        if(s==null||s.length()>260||!s.matches("[A-Za-z0-9.-]+:[0-9]{1,5}"))return false;
        int port=Integer.parseInt(s.substring(s.lastIndexOf(':')+1));return port>0&&port<=65535;
    }
    public static String status(){return status;}
    public static String statusText(){return status();}
    public static boolean available(){return initialized&&matchmaking!=null;}
    public static boolean overlayEnabled(){return available()&&utils.isOverlayEnabled();}
    public static void tick(){
        if(!available())return;
        try{
            SteamAPI.runCallbacks();
            if(startupLobby!=0){long id=startupLobby;startupLobby=0;requestJoin(SteamID.createFromNativeHandle(id));}
            if(hosting&&lobby!=null&&System.currentTimeMillis()-lastRefresh>1000){
                lastRefresh=System.currentTimeMillis();
                matchmaking.setLobbyJoinable(lobby,MP.isHost()&&MP.clients.size()<1);
                if(MP.isHost()&&!MP.password.equals(matchmaking.getLobbyData(lobby,"password")))matchmaking.setLobbyData(lobby,"password",MP.password);
            }
        }catch(Throwable e){System.err.println("KREYLI_STEAM: callback error: "+e);status="Ошибка Steam — игра по IP доступна";}
    }
    public static void hostStarted(){
        if(!MP.isHost()||!available())return;
        leaveSession();hosting=true;
        if(!validRoom(MP.relayCode)){status="LAN: друг подключается по вашему IP; Steam-приглашения доступны в режиме кода комнаты";return;}
        if(creating){status="Ожидание создания Steam-лобби…";return;}
        creating=true;status="Создаётся лобби Steam (2 игрока)…";
        matchmaking.createLobby(SteamMatchmaking.LobbyType.FriendsOnly,2);
    }
    public static void inviteFriends(){
        if(!available()){notice(status);return;}
        if(!MP.isHost()){notice("Приглашать друзей может создатель комнаты");return;}
        if(!validRoom(MP.relayCode)){notice("Для Steam-приглашений создайте комнату по коду; в LAN используйте IP Radmin VPN / Hamachi");return;}
        if(lobby==null){if(!creating)hostStarted();invitePending=true;return;}
        if(!utils.isOverlayEnabled()){notice("Оверлей Steam ещё недоступен. Включите его в Steam и перезапустите игру со Steam");return;}
        friends.activateGameOverlayInviteDialog(lobby);
    }
    private static void matchEvent(String event,Object[] a){
        switch(event){
            case "onLobbyCreated" -> {
                SteamID id=(SteamID)a[1];creating=false;
                if(a[0]!=SteamResult.OK){status="Не удалось создать Steam-лобби: "+a[0];invitePending=false;return;}
                if(!hosting||!MP.isHost()||!validRoom(MP.relayCode)){matchmaking.leaveLobby(id);return;}
                lobby=id;matchmaking.setLobbyMemberLimit(id,2);
                matchmaking.setLobbyData(id,"kreyli_protocol",PROTOCOL);
                matchmaking.setLobbyData(id,"name","Kreyli Coop AOH2");
                matchmaking.setLobbyData(id,"room",MP.relayCode.toUpperCase(Locale.ROOT));
                matchmaking.setLobbyData(id,"relay",MP.selectedRegion()[0]);
                matchmaking.setLobbyData(id,"password",MP.password);
                friends.setRichPresence("connect","+connect_lobby "+SteamNativeHandle.getNativeHandle(lobby));
                status="Steam-лобби готово — максимум 2 игрока";
                if(invitePending){invitePending=false;inviteFriends();}
            }
            case "onLobbyEnter" -> {
                SteamID id=(SteamID)a[0];
                if(requested==null||!requested.equals(id))return;
                if(a[3]!=SteamMatchmaking.ChatRoomEnterResponse.Success){requested=null;notice("Не удалось войти в Steam-лобби: "+a[3]);return;}
                lobby=id;consumeRoom();
            }
            case "onLobbyDataUpdate" -> {if(lobby!=null&&lobby.equals(a[0])&&Boolean.TRUE.equals(a[2]))consumeRoom();}
            case "onLobbyKicked" -> {if(lobby!=null&&lobby.equals(a[0]))leaveSession();}
        }
    }
    private static void requestJoin(SteamID id){
        if(!available()||id==null||!id.isValid())return;
        if(MP.isHost()||MP.isClient()){notice("Сначала выйдите из текущей сетевой игры, затем примите приглашение");return;}
        leaveSession();requested=new SteamID(id);status="Подключение к Steam-лобби…";matchmaking.joinLobby(id);
    }
    private static void consumeRoom(){
        if(hosting||requested==null||lobby==null)return;
        String protocol=matchmaking.getLobbyData(lobby,"kreyli_protocol"),room=matchmaking.getLobbyData(lobby,"room");
        if(!PROTOCOL.equals(protocol)||!validRoom(room)){leaveSession();notice("Приглашение не относится к совместимой комнате Kreyli Coop AOH2");return;}
        String password=matchmaking.getLobbyData(lobby,"password"),relay=matchmaking.getLobbyData(lobby,"relay");
        if(password.length()>256||!validRelay(relay)){leaveSession();notice("Некорректные данные лобби");return;}
        requested=null;nativeJoin=true;
        try{
            // A startup invitation can arrive before the remote region list loads.
            // Resolve the room on the host's region, preserving normal regions for later browsing.
            var regions=new ArrayList<String[]>();regions.add(new String[]{relay,"Steam room"});
            for(String[] region:MP.regions)if(!region[0].equals(relay))regions.add(region);
            MP.regions=regions;
            MP.address=room;MP.password=password;MP.join();MP_UI.open(View.eMP_JOINING);status="Подключение к Kreyli Coop AOH2…";
        }
        finally{nativeJoin=false;}
    }
    public static void leaveSession(){
        if(nativeJoin)return;
        if(hosting&&friends!=null)friends.setRichPresence("connect",null);
        hosting=false;requested=null;invitePending=false;
        if(lobby!=null&&matchmaking!=null){matchmaking.leaveLobby(lobby);lobby=null;}
        if(available())status="Steam: Spacewar — Shift+Tab";
    }
    public static long getSteamID64(){return available()&&localSteamID!=null&&localSteamID.isValid()?SteamNativeHandle.getNativeHandle(localSteamID):0;}
    public static String profileName(){
        try{return getSteamID64()==0?"":friends.getPersonaName();}
        catch(RuntimeException|LinkageError e){return "";}
    }
    public static byte[] profileAvatar(){
        try{
            if(getSteamID64()==0)return null;
            int image=friends.getSmallFriendAvatar(localSteamID);
            int[] size=new int[2];
            if(image<=0||!utils.getImageSize(image,size)||size[0]!=32||size[1]!=32)return null;
            var pixels=java.nio.ByteBuffer.allocateDirect(32*32*4);
            if(!utils.getImageRGBA(image,pixels))return null;
            byte[] result=new byte[32*32*4];
            pixels.rewind();pixels.get(result);return result;
        }catch(Exception|LinkageError e){return null;}
    }
    private static void notice(String text){status=text;try{MP.toast(text,false);}catch(Throwable ignored){System.out.println("KREYLI_STEAM: "+text);}}
    public static synchronized void shutdown(){
        if(!initialized)return;
        try{leaveSession();if(friends!=null)friends.dispose();if(matchmaking!=null)matchmaking.dispose();if(utils!=null)utils.dispose();if(user!=null)user.dispose();SteamAPI.shutdown();}
        catch(Throwable ignored){}finally{initialized=false;friends=null;matchmaking=null;utils=null;user=null;localSteamID=null;}
    }
}



