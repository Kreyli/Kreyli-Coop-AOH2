package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.*;
import com.badlogic.gdx.Gdx;
import java.util.*;

/** Pregame readiness is deliberately separate from native end-turn readiness. */
public final class KreyliLobby {
    public static final String DISCORD="https://discord.gg/zGdxgSvW4";
    static final class Gate {
        boolean hostReady, released;
        List<?> members=List.of();
        final Set<Object> ready=Collections.newSetFromMap(new IdentityHashMap<>());
        long deadline;
        int seconds;
        void clearCountdown(){deadline=0;seconds=0;}
        boolean members(List<?> next){
            if(members.equals(next))return false;
            members=List.copyOf(next);
            resetReady();
            return true;
        }
        void resetReady(){
            hostReady=false;ready.clear();released=false;clearCountdown();
        }
        boolean updateCountdown(long now){
            if(released)return false;
            if(!hostReady||!ready.containsAll(members)){
                boolean changed=seconds!=0;clearCountdown();return changed;
            }
            if(deadline==0)deadline=now+5_000_000_000L;
            long remaining=deadline-now;
            int next=remaining>0?(int)((remaining+999_999_999L)/1_000_000_000L):0;
            boolean changed=next!=seconds;
            seconds=next;
            if(next==0)released=true;
            return changed||released;
        }
    }
    static Gate gate=new Gate();
    private static boolean active, choosing, realTime;
    record Member(String name,boolean ready){}
    private static List<Member> roster=List.of();
    private static boolean hostReady,guestReady;
    private static int guestTimer,guestCountdown;
    private KreyliLobby(){}
    public static void reset(){KreyliQuickGame.reset();gate=new Gate();active=false;choosing=false;realTime=false;roster=List.of();hostReady=false;guestReady=false;guestTimer=0;guestCountdown=0;}
    public static void tick(){
        KreyliQuickGame.tick();
        if(KreyliQuickGame.active())return;
        if(!MP.isHost()||MP.started)return;
        boolean wasReleased=gate.released;
        if(gate.members(MP.clients)){
            publish();
            if(wasReleased)MP_UI.open(View.eMP_HOST);
        }
        if(gate.updateCountdown(System.nanoTime()))publishCountdown();
    }
    public static boolean hostMessage(MP_Net.Conn connection,Object[] message){
        if(message.length==0||!"kreyli_lobby_ready".equals(message[0]))return false;
        if(!MP.isHost()||MP.started||connection.nick==null||!MP.clients.contains(connection)||message.length!=2||!(message[1] instanceof Boolean value))return true;
        gate.members(MP.clients);
        if(gate.released)return true;
        if(value)gate.ready.add(connection);else gate.ready.remove(connection);
        advance();return true;
    }
    public static boolean clientMessage(Object[] message){
        if(KreyliQuickGame.clientMessage(message))return true;
        if(KreyliCountrySeats.clientMessage(message))return true;
        if(KreyliProfile.clientMessage(message))return true;
        if(KreyliIdentity.clientMessage(message))return true;
        if(KreyliOrderSync.receive(message))return true;
        if(KreyliRealtimeEconomy.receive(message))return true;
        if(KreyliTurns.clientMessage(message))return true;
        if(KreyliPings.clientMessage(message))return true;
        if(message.length==0)return false;
        if("snap".equals(message[0])){active=false;return false;}
        if("kreyli_lobby_options".equals(message[0])){
            if(MP.isClient()&&message.length>=2&&message[1] instanceof Integer seconds){
                guestTimer=seconds;
                if(message.length==3&&message[2] instanceof Boolean mode)realTime=mode;
                CFG.setRenderO(true);
            }
            return true;
        }
        if("kreyli_lobby_countdown".equals(message[0])){
            if(MP.isClient()&&message.length==2&&message[1] instanceof Integer seconds&&seconds>=0&&seconds<=5)guestCountdown=seconds;
            return true;
        }
        if(!"kreyli_lobby_state".equals(message[0]))return false;
        if(!MP.isClient()||message.length<6||message.length>4+2*KreyliSteam.MAX_PLAYERS||(message.length-4)%2!=0
                ||!(message[1] instanceof Boolean gr)||!(message[2] instanceof Boolean released)||!(message[3] instanceof Boolean shared))return true;
        var entries=new ArrayList<Member>();
        for(int i=4;i<message.length;i+=2){
            if(!(message[i] instanceof String name)||!(message[i+1] instanceof Boolean ready))return true;
            entries.add(new Member(name,ready));
        }
        boolean wasActive=active;
        roster=List.copyOf(entries);hostReady=roster.getFirst().ready();guestReady=gr;choosing=released;SharedSupport.shared=shared;active=true;
        if(!wasActive)MP_UI.open(View.eMP_JOINING);
        return true;
    }
    public static Menu[] menus(Menu[] original,View view){
        return view==View.eMP_JOINING&&active?new Menu[]{room()}:original;
    }
    private static void publish(){
        var entries=new ArrayList<Member>();
        entries.add(new Member(MP.nick,gate.hostReady));
        for(var client:MP.clients)if(client.nick!=null)entries.add(new Member(client.nick,gate.ready.contains(client)));
        roster=List.copyOf(entries);
        hostReady=gate.hostReady;choosing=gate.released;
        Object[] packet=new Object[4+entries.size()*2];
        packet[0]="kreyli_lobby_state";packet[2]=choosing;packet[3]=SharedSupport.shared;
        for(int i=0;i<entries.size();i++){
            packet[4+i*2]=entries.get(i).name();packet[5+i*2]=entries.get(i).ready();
        }
        for(var c:MP.clients){
            c.send("kreyli_lobby_options",MP.turnSeconds,realTime);
            c.send("kreyli_lobby_countdown",gate.seconds);
            packet[1]=gate.ready.contains(c);
            c.send(packet.clone());
        }
    }
    static String timerLabel(){return timerLabel(MP.isHost()?MP.turnSeconds:guestTimer);}
    static String timerLabel(int seconds){return seconds==0?KreyliText.t("Без ограничения","No limit"):seconds+KreyliText.t(" с"," s");}
    static boolean realTime(){return realTime;}
    static void readMode(boolean mode){realTime=mode;}
    static void toggleMode(){
        if(!MP.isHost()||MP.started)return;
        realTime=!realTime;
        settingsChanged();
        CFG.setRenderO(true);
    }
    static void settingsChanged(){
        gate.resetReady();publish();
    }
    static void profileChanged(){if(MP.isHost()&&!MP.started)publish();}
    private static void advance(){
        gate.updateCountdown(System.nanoTime());publishCountdown();
    }
    private static void publishCountdown(){
        publish();
        if(gate.released)openCampaigns();
    }
    private static void openCampaigns(){
        MP_UI.open(View.eGAMES);
        CFG.menus.setBackAnimation(false);
        CFG.setRenderO(true);
    }
    static int countdown(){return MP.isHost()?gate.seconds:guestCountdown;}
    public static void toggleReady(){
        if(MP.isHost()){
            gate.members(MP.clients);
            if(gate.released){openCampaigns();return;}
            gate.hostReady=!gate.hostReady;advance();
        }else if(MP.isClient()&&!choosing){
            try{var field=MP.class.getDeclaredField("host");field.setAccessible(true);var host=(MP_Net.Conn)field.get(null);if(host!=null)host.send("kreyli_lobby_ready",!guestReady);}
            catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
        }
    }
    static List<Member> roster(){return roster;}
    static String hostDisplay(){return roster.isEmpty()?MP.nick:roster.getFirst().name();}
    static String guestDisplay(){return roster.size()<2?"":roster.get(1).name();}
    static boolean hostIsReady(){return hostReady;}
    static boolean guestIsReady(){return guestReady;}
    static boolean choosingGame(){return choosing;}
    public static void discord(){Gdx.net.openURI(DISCORD);}
    public static Menu room(){
        if(MP.isHost()){gate.members(MP.clients);publish();}
        return new KreyliLobbyRoom();
    }
}






