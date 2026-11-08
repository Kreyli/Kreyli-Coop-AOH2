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
        if(MP.clients.size()<1)return false;
        connection.sendAndClose("reject","Kreyli_LobbyFull",BRAND+": максимум 2 игрока"," ");
        return true;
    }
    public static String brandedText(String text,String key){
        return "MP_Multiplayer".equals(key)?BRAND:"MP_ServerBrowser".equals(key)?"Список серверов":text;
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
        if(MP.isHost()){if(!MP.applying&&MP.playing()&&MP.inputOrders())KreyliTurns.order();militaryOnly=op.equals("ma")||op.equals("mtp");KreyliOrderSync.mark();}
        // New local orders withdraw the vote before the ordered network command.
        // In particular, accepting diplomacy must not silently discard its mutation.
        if(!MP.applying&&MP.playing()&&MP.inputOrders()&&
           (MP.isClient()?civ==MP.myCiv:MP.isHost()&&civ==CFG.core.getPlayer(0).getCivId()))cancelReady();
        if(shared && MP.isClient() && !MP.applying && civ==MP.myCiv && MP.playing() && MP.inputOrders()){
            if(pending || MP.ready) return true;
            int before=(Integer)get("sentCmds");
            boolean result=(Boolean)call("coopOriginalCmd",new Class[]{String.class,int.class,Object[].class},op,civ,args);
            if((Integer)get("sentCmds")>before){KreyliTurns.order();pendingRequest=(Integer)get("sentCmds");pending=true;return true;}
            return result;
        }
        if(shared && MP.isHost() && !MP.applying && MP.started && MP.inputOrders()&&!militaryOnly) MP.requestSync();
        return (Boolean)call("coopOriginalCmd",new Class[]{String.class,int.class,Object[].class},op,civ,args);
    }
    public static void afterCommand(){KreyliOrderSync.mark();KreyliTurns.order();if(shared && MP.isHost()&&!militaryOnly)MP.requestSync();}
    public static int free(int civ){return shared ? (civ==CFG.core.getPlayer(0).getCivId()?1:0) : -1;}
    public static boolean pick(int civ){
        if(!shared)return false;
        if(!MP.isClient()||!MP.picking||!MP.pickable(civ))return true;
        if(MP.rosterCivs.length==0||civ!=MP.rosterCivs[0]){MP.toast("Choose the host's country for shared control",false);return true;}
        set("pickPending",true);((MP_Net.Conn)get("host")).send("pick",civ);return true;
    }
    public static boolean leave(MP_Net.Conn connection){
        if(!shared || !MP.validCiv(connection.civ))return false;
        boolean remaining=connection.civ==CFG.core.getPlayer(0).getCivId();
        for(var other:MP.clients)if(other!=connection&&other.civ==connection.civ)remaining=true;
        if(!remaining)return false;
        MP.clients.remove(connection);expected.remove(connection);acknowledged.remove(connection);
        MP.toast(connection.nick+" left",false);return true;
    }
    public static boolean keepLeader(int civ){return shared && MP.started && civ==CFG.core.getPlayer(0).getCivId();}
    public static Map<String,Object> pieces(Map<String,Object> pieces){
        if(shared){pieces.put("coop_mode",true);pieces.put("coop_revision",++revision);pieces.put("coop_armies",SharedState.capture());}
        else pieces.put("coop_mode",false);
        return pieces;
    }
    public static Future<?> built(Future<?> future){if(shared && future!=null){futures.clear();futures.put(future,revision);}return future;}
    public static void sending(MP_Net.Conn connection,Future<?> future){
        if(shared && connection.civ>0){Long rev=futures.get(future);if(rev==null)throw new IllegalStateException("Missing snapshot revision");expected.put(connection,rev);}
    }
    public static void loaded(Object data){
        if(!(data instanceof Map<?,?> map))return;
        shared=Boolean.TRUE.equals(map.get("coop_mode"));
        KreyliInputRecovery.afterSnapshot();KreyliTurns.afterSnapshot();KreyliOrderSync.request();
        if(!shared)return;
        if(!(map.get("coop_revision") instanceof Long rev)||!(map.get("coop_armies") instanceof String state))throw new IllegalStateException("Shared snapshot missing state");
        SharedState.restore(state);if(snapshotCommands>=pendingRequest)pending=false;
        if(MP.myCiv>0)((MP_Net.Conn)get("host")).send("coop_ack",rev);
    }
    public static boolean message(MP_Net.Conn connection,Object[] message){
        if(KreyliOrderSync.hostMessage(connection,message))return true;
        if(KreyliTurns.hostMessage(connection,message))return true;
        if(KreyliPings.hostMessage(connection,message))return true;
        if(KreyliLobby.hostMessage(connection,message))return true;
        if(message.length==0||!"coop_ack".equals(message[0]))return false;
        if(!shared||message.length!=2||!(message[1] instanceof Long rev))return true;
        if(Objects.equals(expected.get(connection),rev))acknowledged.put(connection,rev);
        return true;
    }
    public static boolean canEnd(){
        if(!shared)return true;
        for(var c:MP.clients)if(c.civ>0&&(!expected.containsKey(c)||!Objects.equals(expected.get(c),acknowledged.get(c))))return false;
        return true;
    }
    public static void incoming(Object[] message){if(message.length>9&&"snap".equals(message[0])&&message[9] instanceof Integer count)snapshotCommands=count;}
    public static boolean waitBeforeReady(){return shared && MP.isClient() && pending;}
    /** A second press withdraws only the local player's vote, before turn processing. */
    public static boolean cancelReady(){
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
    public static void reset(){KreyliOrderSync.reset();KreyliTurns.reset();KreyliPings.reset();pending=false;pendingRequest=0;snapshotCommands=0;revision=0;futures.clear();expected.clear();acknowledged.clear();}
}








