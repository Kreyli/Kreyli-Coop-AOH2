package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.CFG;
import age.of.civilizations2.jakowski.lukasz.Menus.NewGame.Menu_CreateNewGame;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static age.of.civilizations2.jakowski.lukasz.mp.KreyliText.t;

/** Host-owned limits. A country's first controller chooses the capacity. */
public final class KreyliCountrySeats {
    static final int MAX=KreyliSteam.MAX_PLAYERS;
    private static final Map<Integer,Integer> limits=new LinkedHashMap<>();
    private static final Field HOST=field("host"),PENDING=field("pickPending");
    private static final Method ON_PICK=onPick();

    private KreyliCountrySeats(){}
    private static Field field(String name){
        try{Field field=MP.class.getDeclaredField(name);field.setAccessible(true);return field;}
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static Method onPick(){
        try{Method method=MP.class.getDeclaredMethod("onPick",MP_Net.Conn.class,int.class);method.setAccessible(true);return method;}
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static void pending(boolean value){
        try{PENDING.setBoolean(null,value);}
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static MP_Net.Conn host(){
        try{return (MP_Net.Conn)HOST.get(null);}
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static int hostCountry(){return CFG.core==null||CFG.core.getPlayersSize()==0?0:CFG.core.getPlayer(0).getCivId();}
    static int capacity(int civ){
        Integer limit=limits.get(civ);
        if(limit!=null)return limit;
        int owner=MP.isHost()?hostCountry():MP.rosterCivs.length>0?MP.rosterCivs[0]:0;
        return SharedSupport.shared&&civ==owner?MAX:1;
    }
    static boolean sharedCountry(int civ){return civ>0&&capacity(civ)>1;}
    static boolean anyShared(){return SharedSupport.shared||limits.values().stream().anyMatch(n->n>1);}
    public static boolean available(int civ,MP_Net.Conn guest){
        return occupied(civ,guest)+KreyliSaveSeats.unoccupiedReservations(civ,guest)<capacity(civ);
    }
    private static int occupied(int civ,MP_Net.Conn except){
        int count=hostCountry()==civ?1:0;
        for(var guest:MP.clients)if(guest!=except&&guest.civ==civ)count++;
        return count;
    }
    static int remoteOccupied(int civ){
        int count=0;
        for(int current:MP.rosterCivs)if(current==civ)count++;
        return count;
    }
    static boolean configured(int civ){return limits.containsKey(civ);}
    public static void configureHost(int civ,int limit){
        if(MP.isHost()&&!MP.started&&MP.pickable(civ)&&limit>=1&&limit<=MAX)limits.put(civ,limit);
    }
    static String encode(){
        StringBuilder text=new StringBuilder();
        for(var entry:limits.entrySet()){
            if(!text.isEmpty())text.append(',');
            text.append(entry.getKey()).append(':').append(entry.getValue());
        }
        return text.toString();
    }
    static void decode(String text){
        limits.clear();
        if(text.length()>4096)return;
        for(String entry:text.split(",")){
            int separator=entry.indexOf(':');
            if(separator<1)continue;
            try{
                int civ=Integer.parseInt(entry.substring(0,separator)),limit=Integer.parseInt(entry.substring(separator+1));
                if(civ>0&&limit>=1&&limit<=MAX)limits.put(civ,limit);
            }catch(NumberFormatException ignored){}
        }
    }
    static void reset(){limits.clear();KreyliCountryChoice.close();}

    static boolean hostMessage(MP_Net.Conn guest,Object[] packet){
        if(packet.length==0||!"kreyli_country_pick".equals(packet[0]))return false;
        if(!MP.isHost()||guest.nick==null||!MP.clients.contains(guest))return true;
        if(packet.length!=3||!(packet[1] instanceof Integer civ)||!(packet[2] instanceof Integer requested)
                ||requested<1||requested>MAX||!MP.started||guest.civ>0||!MP.pickable(civ)||!MP.inputOrders()){
            guest.send("kreyli_country_denied");
            return true;
        }
        boolean newCountry=!configured(civ)&&occupied(civ,null)==0&&KreyliSaveSeats.unoccupiedReservations(civ,null)==0;
        if(newCountry)limits.put(civ,requested);
        if(available(civ,guest)){
            try{ON_PICK.invoke(null,guest,civ);}
            catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        }
        if(guest.civ!=civ){
            if(newCountry)limits.remove(civ);
            guest.send("kreyli_country_denied");
        }else{
            String encoded=encode();
            for(var player:MP.clients)if(player.nick!=null)player.send("kreyli_country_limits",encoded);
        }
        return true;
    }
    static boolean clientMessage(Object[] packet){
        if(!MP.isClient())return false;
        if(packet.length==2&&"kreyli_country_limits".equals(packet[0])&&packet[1] instanceof String encoded){decode(encoded);CFG.setRenderO(true);return true;}
        if(packet.length==1&&"kreyli_country_denied".equals(packet[0])){
            pending(false);
            MP.toast(t("Страна недоступна или все места заняты","Country unavailable or all seats taken"),false);
            return true;
        }
        return false;
    }
    static void submit(int civ,int limit){
        MP_Net.Conn server=host();
        if(server==null||MP.pickPending())return;
        pending(true);
        server.send("kreyli_country_pick",civ,limit);
    }
    public static boolean pick(int civ){
        if(!MP.isClient()||!MP.picking||!MP.pickable(civ))return false;
        if(!MP.pickPending())KreyliCountryChoice.open(civ,false);
        return true;
    }
    public static boolean beforeNewGame(){
        if(!MP.isHost()||MP.started||!CFG.menus.getInCreateNewGame())return false;
        int civ=hostCountry();
        if(!MP.pickable(civ)||configured(civ))return false;
        KreyliCountryChoice.open(civ,true);
        return true;
    }
    static void choose(int civ,int limit,boolean host){
        if(host){configureHost(civ,limit);Menu_CreateNewGame.newGame();}
        else submit(civ,limit);
    }
}
