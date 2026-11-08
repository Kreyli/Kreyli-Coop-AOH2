package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.*;
import com.badlogic.gdx.Gdx;

/** Pregame readiness is deliberately separate from native end-turn readiness. */
public final class KreyliLobby {
    public static final String DISCORD="https://discord.gg/zGdxgSvW4";
    static final class Gate {
        boolean hostReady, guestReady, released;
        Object guest;
        long deadline;
        int seconds;
        void clearCountdown(){deadline=0;seconds=0;}
        boolean member(Object next){
            if(guest==next)return false;
            guest=next;hostReady=false;guestReady=false;released=false;clearCountdown();return true;
        }
        boolean updateCountdown(long now){
            if(released)return false;
            if(!hostReady||(guest!=null&&!guestReady)){
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
    private static boolean active, choosing;
    private static String hostName="",guestName="";
    private static boolean hostReady,guestReady;
    private static int guestTimer,guestCountdown;
    private KreyliLobby(){}
    public static void reset(){gate=new Gate();active=false;choosing=false;hostName="";guestName="";hostReady=false;guestReady=false;guestTimer=0;guestCountdown=0;}
    public static void tick(){
        if(!MP.isHost()||MP.started)return;
        MP_Net.Conn member=MP.clients.isEmpty()?null:MP.clients.get(0);
        boolean wasReleased=gate.released;
        if(gate.member(member)){
            publish();
            if(wasReleased)MP_UI.open(View.eMP_HOST);
        }
        if(gate.updateCountdown(System.nanoTime()))publishCountdown();
    }
    public static boolean hostMessage(MP_Net.Conn connection,Object[] message){
        if(message.length==0||!"kreyli_lobby_ready".equals(message[0]))return false;
        if(!MP.isHost()||MP.started||connection.nick==null||!MP.clients.contains(connection)||message.length!=2||!(message[1] instanceof Boolean value))return true;
        gate.member(connection);
        if(gate.released)return true;
        gate.guestReady=value;advance();return true;
    }
    public static boolean clientMessage(Object[] message){
        if(KreyliIdentity.clientMessage(message))return true;
        if(KreyliOrderSync.receive(message))return true;
        if(KreyliTurns.clientMessage(message))return true;
        if(KreyliPings.clientMessage(message))return true;
        if(message.length==0)return false;
        if("snap".equals(message[0])){active=false;return false;}
        if("kreyli_lobby_options".equals(message[0])){
            if(MP.isClient()&&message.length==2&&message[1] instanceof Integer seconds)guestTimer=seconds;
            return true;
        }
        if("kreyli_lobby_countdown".equals(message[0])){
            if(MP.isClient()&&message.length==2&&message[1] instanceof Integer seconds&&seconds>=0&&seconds<=5)guestCountdown=seconds;
            return true;
        }
        if(!"kreyli_lobby_state".equals(message[0]))return false;
        if(!MP.isClient()||message.length!=7||!(message[1] instanceof String h)||!(message[2] instanceof String g)||!(message[3] instanceof Boolean hr)||!(message[4] instanceof Boolean gr)||!(message[5] instanceof Boolean released)||!(message[6] instanceof Boolean shared))return true;
        boolean wasActive=active;
        hostName=h;guestName=g;hostReady=hr;guestReady=gr;choosing=released;SharedSupport.shared=shared;active=true;
        if(!wasActive)MP_UI.open(View.eMP_JOINING);
        return true;
    }
    public static Menu[] menus(Menu[] original,View view){
        return view==View.eMP_JOINING&&active?new Menu[]{room()}:original;
    }
    private static void publish(){
        hostName=MP.nick;guestName=gate.guest instanceof MP_Net.Conn c?c.nick:"";
        hostReady=gate.hostReady;guestReady=gate.guestReady;choosing=gate.released;
        for(var c:MP.clients){
            c.send("kreyli_lobby_options",MP.turnSeconds);
            c.send("kreyli_lobby_countdown",gate.seconds);
            c.send("kreyli_lobby_state",hostName,guestName,hostReady,guestReady,choosing,SharedSupport.shared);
        }
    }
    static String timerLabel(){return timerLabel(MP.isHost()?MP.turnSeconds:guestTimer);}
    static String timerLabel(int seconds){return seconds==0?"Без ограничения":seconds+" с";}
    static void settingsChanged(){
        gate.hostReady=false;gate.guestReady=false;gate.released=false;gate.clearCountdown();publish();
    }
    private static void advance(){
        gate.updateCountdown(System.nanoTime());publishCountdown();
    }
    private static void publishCountdown(){
        publish();
        if(gate.released)MP_UI.open(View.eGAMES);
    }
    static int countdown(){return MP.isHost()?gate.seconds:guestCountdown;}
    public static void toggleReady(){
        if(MP.isHost()){
            gate.member(MP.clients.isEmpty()?null:MP.clients.get(0));
            if(gate.released){MP_UI.open(View.eGAMES);return;}
            gate.hostReady=!gate.hostReady;advance();
        }else if(MP.isClient()&&!choosing){
            try{var field=MP.class.getDeclaredField("host");field.setAccessible(true);var host=(MP_Net.Conn)field.get(null);if(host!=null)host.send("kreyli_lobby_ready",!guestReady);}
            catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
        }
    }
    static String hostDisplay(){return hostName.isEmpty()?MP.nick:hostName;}
    static String guestDisplay(){return guestName;}
    static boolean hostIsReady(){return hostReady;}
    static boolean guestIsReady(){return guestReady;}
    static boolean choosingGame(){return choosing;}
    public static void discord(){Gdx.net.openURI(DISCORD);}
    public static Menu room(){
        if(MP.isHost()){gate.member(MP.clients.isEmpty()?null:MP.clients.get(0));publish();}
        return new KreyliLobbyRoom();
    }
}






