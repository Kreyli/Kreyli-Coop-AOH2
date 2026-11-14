package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.Map;
import java.util.concurrent.Future;

/** Experimental host-authoritative shared control. Requires the pinned 2.2 archive. */
public final class SharedSupport {
    public static final String BRAND="Kreyli Coop AOH2";
    public static boolean rejectExtraPlayer(MP_Net.Conn connection){
        if(MP.clients.size()<KreyliSteam.MAX_PLAYERS-1)return false;
        connection.sendAndClose("reject","Kreyli_LobbyFull",BRAND+": максимум "+KreyliSteam.MAX_PLAYERS+" игроков"," ");
        return true;
    }
    public static String brandedText(String text,String key,String fallback,Object[] args){
        if("MP_Multiplayer".equals(key))return BRAND;
        if("MP_ServerBrowser".equals(key))return KreyliText.t("Список серверов","Server list");
        if(text!=null&&!text.isBlank())return text;
        String result=fallback;
        for(int i=0;i<args.length;i++)result=result.replace("{"+i+"}",String.valueOf(args[i]));
        return result;
    }
    public static boolean shared = Boolean.getBoolean("coop.shared");
    private static boolean pending, militaryOnly;
    private static int pendingRequest, snapshotCommands;
    private static long revision;
    private static final Map<Object,Long> futures = new IdentityHashMap<>();
    private static final Map<MP_Net.Conn,Long> expected = new IdentityHashMap<>(), acknowledged = new IdentityHashMap<>();
    private static Object get(String name) {try{var f=MP.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);}catch(Exception e){throw new IllegalStateException(e);}}
    private static void set(String name,Object value) {try{var f=MP.class.getDeclaredField(name);f.setAccessible(true);f.set(null,value);}catch(Exception e){throw new IllegalStateException(e);}}
    private static Object call(String name,Class<?>[] types,Object...args){try{var m=MP.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(null,args);}catch(InvocationTargetException e){throw new IllegalStateException(e.getCause());}catch(Exception e){throw new IllegalStateException(e);}}
    public static boolean command(String op,int civ,Object[] args){
        KreyliNuclearSync.beforeCommand(op);
        if(!MP.applying&&KreyliRealtimePause.paused())return true;
        if(!MP.applying&&KreyliQuickGame.active()
                &&(KreyliQuickGame.blocksOrders(civ)||"dw".equals(op)&&args.length>1&&args[1] instanceof Integer target&&!KreyliQuickGame.allowWar(civ,target)))
            return true;
        if(MP.isHost()){militaryOnly=op.equals("ma")||op.equals("mtp");KreyliOrderSync.mark();}
        // New local orders withdraw the vote before the ordered network command.
        // In particular, accepting diplomacy must not silently discard its mutation.
        if(!MP.applying&&MP.playing()&&MP.inputOrders()&&
           (MP.isClient()?civ==MP.myCiv:MP.isHost()&&civ==CFG.core.getPlayer(0).getCivId()))cancelReady();
        if(KreyliCountrySeats.sharedCountry(civ) && MP.isClient() && !MP.applying && civ==MP.myCiv && MP.playing() && MP.inputOrders()){
            if(pending || MP.ready) return true;
            int before=(Integer)get("sentCmds");
            boolean result=(Boolean)call("coopOriginalCmd",new Class[]{String.class,int.class,Object[].class},op,civ,args);
            if((Integer)get("sentCmds")>before){pendingRequest=(Integer)get("sentCmds");pending=true;return true;}
            return result;
        }
        if(KreyliCountrySeats.anyShared() && MP.isHost() && !MP.applying && MP.started && MP.inputOrders()&&!militaryOnly) MP.requestSync();
        return (Boolean)call("coopOriginalCmd",new Class[]{String.class,int.class,Object[].class},op,civ,args);
    }
    public static void afterCommand(){KreyliOrderSync.mark();if(KreyliCountrySeats.anyShared() && MP.isHost()&&!militaryOnly)MP.requestSync();}
    public static boolean leave(MP_Net.Conn connection){
        if(!MP.validCiv(connection.civ))return false;
        boolean remaining=connection.civ==CFG.core.getPlayer(0).getCivId();
        for(var other:MP.clients)if(other!=connection&&other.civ==connection.civ)remaining=true;
        if(!remaining)return false;
        MP.clients.remove(connection);expected.remove(connection);acknowledged.remove(connection);
        MP.toast(connection.nick+" left",false);return true;
    }
    static void clearLocalReports(){((java.util.List<?>)get("reportsToShow")).clear();}
    public static boolean keepLeader(int civ){return MP.isOn();}
    public static Map<String,Object> pieces(Map<String,Object> pieces){
        pieces.put("coop_realtime",KreyliLobby.realTime());
        pieces.put("coop_limits",KreyliCountrySeats.encode());
        pieces.put("coop_legacy",shared);
        if(KreyliCountrySeats.anyShared()){pieces.put("coop_mode",true);pieces.put("coop_revision",++revision);pieces.put("coop_armies",SharedState.capture());}
        else pieces.put("coop_mode",false);
        if(KreyliLobby.realTime()){
            if(!pieces.containsKey("coop_armies"))pieces.put("coop_realtime_orders",SharedState.capture());
            pieces.put("coop_march_clock",KreyliRealtimeMarch.clock());
            pieces.put("coop_economy_sequence",KreyliRealtimeEconomy.snapshotSequence());
        }
        return pieces;
    }
    public static Future<?> built(Future<?> future){if(KreyliCountrySeats.anyShared() && future!=null){futures.clear();futures.put(future,revision);}return future;}
    public static void sending(MP_Net.Conn connection,Future<?> future){
        if(KreyliCountrySeats.sharedCountry(connection.civ)){Long rev=futures.get(future);if(rev==null)throw new IllegalStateException("Missing snapshot revision");expected.put(connection,rev);}
    }
    public static void loaded(Object data){
        if(!(data instanceof Map<?,?> map))return;
        if(map.get("coop_realtime") instanceof Boolean mode)KreyliLobby.readMode(mode);
        KreyliRealtimeEconomy.afterSnapshot(map.get("coop_economy_sequence"));
        if(map.get("coop_limits") instanceof String limits)KreyliCountrySeats.decode(limits);
        shared=Boolean.TRUE.equals(map.get("coop_legacy"));
        KreyliInputRecovery.afterSnapshot();KreyliTurns.afterSnapshot();KreyliOrderSync.request();
        if(!Boolean.TRUE.equals(map.get("coop_mode"))){
            if(KreyliLobby.realTime()&&map.get("coop_realtime_orders") instanceof String orders){
                SharedState.restore(orders);
                if(map.get("coop_march_clock") instanceof String clock)KreyliRealtimeMarch.applyClock(clock);
            }
            return;
        }
        if(!(map.get("coop_revision") instanceof Long rev)||!(map.get("coop_armies") instanceof String state))throw new IllegalStateException("Shared snapshot missing state");
        SharedState.restore(state);
        if(KreyliLobby.realTime()&&map.get("coop_march_clock") instanceof String clock)KreyliRealtimeMarch.applyClock(clock);
        if(snapshotCommands>=pendingRequest)pending=false;
        if(KreyliCountrySeats.sharedCountry(MP.myCiv))((MP_Net.Conn)get("host")).send("coop_ack",rev);
    }
    public static boolean message(MP_Net.Conn connection,Object[] message){
        if(KreyliMilitaryExpertise.hostMessage(connection,message))return true;
        if(KreyliCountrySeats.hostMessage(connection,message))return true;
        if(KreyliProfile.hostMessage(connection,message))return true;
        if(KreyliOrderSync.hostMessage(connection,message))return true;
        if(KreyliTurns.hostMessage(connection,message))return true;
        if(KreyliPings.hostMessage(connection,message))return true;
        if(KreyliLobby.hostMessage(connection,message))return true;
        if(KreyliSaveSeats.hostMessage(connection,message))return true;
        if(message.length==0||!"coop_ack".equals(message[0]))return false;
        if(!KreyliCountrySeats.sharedCountry(connection.civ)||message.length!=2||!(message[1] instanceof Long rev))return true;
        if(Objects.equals(expected.get(connection),rev))acknowledged.put(connection,rev);
        return true;
    }
    public static boolean canEnd(){
        return !KreyliTurns.blocksNativeAdvance()&&synchronizedToAdvance();
    }
    static boolean synchronizedToAdvance(){
        for(var c:MP.clients)if(KreyliCountrySeats.sharedCountry(c.civ)&&(!expected.containsKey(c)||!Objects.equals(expected.get(c),acknowledged.get(c))))return false;
        return true;
    }
    public static void incoming(Object[] message){
        if(message.length==1&&"turnEnd".equals(message[0]))KreyliRealtimeUi.beforeRemoteAdvance();
        if(message.length>0&&"welcome".equals(message[0])){
            KreyliProfile.connected((MP_Net.Conn)get("host"));
            KreyliSaveSeats.afterWelcome();
        }
        if(message.length>9&&"snap".equals(message[0])&&message[9] instanceof Integer count)snapshotCommands=count;
    }
    public static boolean waitBeforeReady(){return KreyliCountrySeats.sharedCountry(MP.myCiv) && MP.isClient() && pending;}
    /** A second press withdraws only the local player's vote, before turn processing. */
    public static boolean cancelReady(){
        if(KreyliQuickGame.active()&&KreyliQuickGame.blocksOrders(KreyliQuickGame.localCountry()))return true;
        if(KreyliLobby.realTime()&&MP.playing())return true;
        if(!MP.playing()||!MP.inputOrders()||!((java.util.List<?>)get("reportsToShow")).isEmpty())return false;
        if(MP.isHost()&&Boolean.TRUE.equals(get("hostReady"))){set("hostReady",false);KreyliTurns.cancelSkip();MP.toast(MP.nick+": готовность отменена",false);return true;}
        if(MP.isClient()&&MP.ready){
            MP.ready=false;KreyliTurns.cancelSkip();KreyliTurns.sendReady(false);
            MP.toast(MP.nick+": готовность отменена",false);return true;
        }
        return false;
    }
    public static boolean lightSync(String op){if(MP.isHost()&&MP.playing()&&(op.equals("ma")||op.equals("mtp"))){KreyliOrderSync.mark();return true;}return false;}
    public static void ordersAcknowledged(int count){if(count>=pendingRequest)pending=false;}
    public static void reset(){KreyliCountrySeats.reset();KreyliOrderSync.reset();KreyliTurns.reset();KreyliPings.reset();pending=false;pendingRequest=0;snapshotCommands=0;revision=0;futures.clear();expected.clear();acknowledged.clear();}
}








