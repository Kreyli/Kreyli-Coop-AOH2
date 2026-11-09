package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.*;
import com.badlogic.gdx.Gdx;
import java.lang.reflect.*;
import java.util.*;
import java.util.Map;

/** Consent and presentation for two-player turns. All mutations run on the game thread. */
public final class KreyliTurns {
    static final KreyliSkipVote vote=new KreyliSkipVote();
    private static int selected=1,remoteCount,remoteRemaining,remoteTurn,observedTurn=-1,ackTurn=-1;
    private static long serial,remoteId;
    private static long lastAckId;private static int loadedTurn=-1,lastAckTurn=-1;
    private static boolean remoteHost,remoteGuest,lastHost,lastGuest;
    private static Object observedGuest;
    private static long readySequence;
    private static long waitDeadline;
    private static int shownSeconds=-1, remoteReadyTurn=-1, remoteSeconds=-1;
    private static String readyPlayer="", remoteReadyPlayer="";
    static int waitSetting(){
        int seconds=Gdx.app.getPreferences("kreyli-coop").getInteger("secondPlayerWait",30);
        return switch(seconds){case 0,10,20,30,45,60,90 -> seconds;default -> 30;};
    }
    static void saveWaitSetting(int seconds){
        Gdx.app.getPreferences("kreyli-coop").putInteger("secondPlayerWait",seconds).flush();
    }
    private record ReadyRequest(int turn,int commands,long sequence){}
    private static final Map<MP_Net.Conn,Long> readySequences=new IdentityHashMap<>();
    private static final Map<MP_Net.Conn,ReadyRequest> readyRequests=new IdentityHashMap<>();
    private static Object field(String name){try{Field f=MP.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);}catch(Exception e){throw new IllegalStateException(e);}}
    private static void field(String name,Object value){try{Field f=MP.class.getDeclaredField(name);f.setAccessible(true);f.set(null,value);}catch(Exception e){throw new IllegalStateException(e);}}
    private static MP_Net.Conn guest(){return MP.clients.size()==1?MP.clients.get(0):null;}
    private static MP_Net.Conn host(){return (MP_Net.Conn)field("host");}
    private static boolean active(){return MP.playing()&&MP.inputOrders()&&MP.myCiv>0||MP.isHost()&&MP.started&&MP.inputOrders();}
    private static void send(Object...data){for(var c:MP.clients)c.send(data);}
    private static void notice(String text){MP.toast(text,false);send("kreyli_turn_notice",text);}
    /** Replaces native markReady; no unversioned Boolean ready packet is sent. */
    public static boolean markReady(){if(MP.isClient()&&MP.myCiv>0&&MP.inputOrders()){MP.ready=true;sendReady(true);}return true;}
    public static void sendReady(boolean ready){if(MP.isClient()&&host()!=null)host().send("kreyli_ready",GameCalendar.TURNID,(Integer)field("sentCmds"),++readySequence,ready);}
    public static boolean readyMessage(MP_Net.Conn conn,Object[] data){
        if(data.length==0)return false;
        if("ready".equals(data[0]))return true; // legacy packets cannot bypass turn identity
        if(!"kreyli_ready".equals(data[0]))return false;
        if(!MP.isHost()||!MP.started||!MP.inputOrders()||conn!=guest()||conn.civ<=0||data.length!=5||!(data[1] instanceof Integer turn)||!(data[2] instanceof Integer commands)||!(data[3] instanceof Long sequence)||!(data[4] instanceof Boolean ready))return true;
        long last=readySequences.getOrDefault(conn,0L);
        if(!KreyliReadyVote.valid(GameCalendar.TURNID,turn,conn.cmds,commands,last,sequence))return true;
        readySequences.put(conn,sequence);readyRequests.remove(conn);conn.ready=false;
        if(ready){if(commands==conn.cmds)conn.ready=true;else readyRequests.put(conn,new ReadyRequest(turn,commands,sequence));}
        return true;
    }
    public static String skipChoice(){return "Пропуск ходов: "+selected+" (изменить)";}
    public static int selectedSkipCount(){return selected;}
    public static boolean hasSkipVote(){return MP.isHost()?vote.id!=0:remoteId!=0&&remoteTurn==GameCalendar.TURNID;}
    public static boolean skipCanRespond(){
        return active()&&hasSkipVote()&&(MP.isHost()?vote.remaining==0&&!vote.hostYes&&vote.guestYes:remoteRemaining==0&&!remoteGuest&&remoteHost);
    }
    public static String incomingSkipStatus(){
        if(!skipCanRespond())return "";
        String name=MP.isHost()?guest().nick:MP.rosterNicks.length>0?MP.rosterNicks[0]:"Хост";
        return name+" предлагает пропустить: "+(MP.isHost()?vote.count:remoteCount);
    }
    public static String readinessStatus(){
        if(!active())return "";
        boolean host=MP.isHost();
        String name=host?readyPlayer:remoteReadyTurn==GameCalendar.TURNID?remoteReadyPlayer:"";
        if(name.isEmpty())return "";
        int seconds=host?shownSeconds:remoteSeconds;
        return name+" готов к ходу"+(seconds<0?"":" · "+seconds+" с");
    }
    public static String skipStatus(){
        long id=MP.isHost()?vote.id:remoteId;int count=MP.isHost()?vote.count:remoteCount,remaining=MP.isHost()?vote.remaining:remoteRemaining;
        boolean localAccepted=MP.isHost()?vote.hostYes:remoteGuest;
        return id==0?"Предложить пропуск ходов":remaining>0?"Пропускаем: осталось "+remaining:localAccepted?"Ожидаем второго игрока: "+count+" ходов":"Принять пропуск "+count+" ходов";
    }
    public static void chooseSkip(){selected=selected%5+1;MP.toast(skipChoice(),false);}
    public static void proposeSkip(){
        if(!active())return;
        if(MP.isHost())propose(selected,true);else if(MP.isClient()&&host()!=null)host().send("kreyli_skip_propose",GameCalendar.TURNID,selected);
    }
    public static void acceptSkip(){
        if(!active())return;
        if(MP.isHost()){if(vote.accept(vote.id,GameCalendar.TURNID,guest(),true))publish();}
        else if(remoteId!=0&&host()!=null)host().send("kreyli_skip_accept",remoteId,GameCalendar.TURNID);
    }
    public static void voteAction(){if((MP.isHost()?vote.id:remoteId)==0)proposeSkip();else acceptSkip();}
    public static void cancelSkip(){
        if(MP.isHost()){vote.clear();publish();}else if(MP.isClient()&&host()!=null)host().send("kreyli_skip_cancel",remoteId,GameCalendar.TURNID);
    }
    private static void propose(int count,boolean byHost){
        if(!MP.isHost()||!active()||guest()==null||guest().civ<=0||count<1||count>5)return;
        vote.propose(++serial,GameCalendar.TURNID,count,guest(),byHost);ackTurn=GameCalendar.TURNID;publish();notice((byHost?MP.nick:guest().nick)+" предлагает пропустить "+count+" ходов");
    }
    private static void publish(){send("kreyli_skip_state",vote.id,GameCalendar.TURNID,vote.count,vote.remaining,vote.hostYes,vote.guestYes);}
    public static boolean hostMessage(MP_Net.Conn conn,Object[] data){
        if(readyMessage(conn,data))return true;
        if(data.length==0||!(data[0] instanceof String op)||!op.startsWith("kreyli_skip_"))return false;
        if(!MP.isHost()||conn!=guest()||conn.nick==null||conn.civ<=0)return true;
        switch(op){
            case "kreyli_skip_propose"->{if(data.length==3&&data[1] instanceof Integer t&&t==GameCalendar.TURNID&&data[2] instanceof Integer n)propose(n,false);}
            case "kreyli_skip_accept"->{if(data.length==3&&data[1] instanceof Long id&&data[2] instanceof Integer t&&t==GameCalendar.TURNID&&vote.accept(id,t,conn,false))publish();}
            case "kreyli_skip_cancel"->{if(data.length==3&&data[1] instanceof Long id&&id==vote.id&&data[2] instanceof Integer t&&t==GameCalendar.TURNID){vote.clear();publish();}}
            case "kreyli_skip_ack"->{if(data.length==3&&data[1] instanceof Long id&&id==vote.id&&data[2] instanceof Integer t&&t==GameCalendar.TURNID)ackTurn=t;}
        }
        return true;
    }
    public static boolean clientMessage(Object[] data){
        if(data.length==4&&"kreyli_ready_state".equals(data[0])){
            if(MP.isClient()&&data[1] instanceof Integer turn&&data[2] instanceof String name&&data[3] instanceof Integer seconds){
                remoteReadyTurn=turn;remoteReadyPlayer=name;remoteSeconds=seconds;
            }
            return true;
        }
        if(data.length==2&&"kreyli_turn_notice".equals(data[0])&&data[1] instanceof String text){MP.toast(text,false);return true;}
        if(data.length==0||!"kreyli_skip_state".equals(data[0]))return false;
        if(MP.isClient()&&data.length==7&&data[1] instanceof Long id&&data[2] instanceof Integer t&&data[3] instanceof Integer count&&count>=0&&count<=5&&data[4] instanceof Integer remaining&&remaining>=0&&remaining<=5&&data[5] instanceof Boolean hy&&data[6] instanceof Boolean gy){remoteId=id;remoteTurn=t;remoteCount=count;remoteRemaining=remaining;remoteHost=hy;remoteGuest=gy;}
        return true;
    }
    private static void acknowledge(){if(MP.isClient()&&remoteId!=0&&host()!=null&&remoteTurn==loadedTurn&&loadedTurn==GameCalendar.TURNID&&(lastAckId!=remoteId||lastAckTurn!=loadedTurn)){host().send("kreyli_skip_ack",remoteId,loadedTurn);lastAckId=remoteId;lastAckTurn=loadedTurn;}}
    public static void afterSnapshot(){
        int camX=CFG.map!=null&&CFG.map.getMpC()!=null?CFG.map.getMpC().getPX():0;
        int camY=CFG.map!=null&&CFG.map.getMpC()!=null?CFG.map.getMpC().getPY():0;
        loadedTurn=GameCalendar.TURNID;acknowledge();
        if(CFG.map!=null&&CFG.map.getMpC()!=null){CFG.map.getMpC().setNewPosX(camX);CFG.map.getMpC().setNewPosY(camY);}
    }
    /** Call only for a genuine local or accepted remote command during INPUT_ORDERS. */
    public static void order(){if(MP.isHost()&&vote.id!=0){vote.clear();publish();}else if(MP.isClient()&&remoteId!=0)cancelSkip();}
    public static void newTurn(){
        if(!MP.isHost())return;
        clearWait();
        // Deferred votes belong to the completed input phase, never to this one.
        readyRequests.clear();
        for(var connection:MP.clients)connection.ready=false;
        if(vote.id!=0){vote.completed(GameCalendar.TURNID);ackTurn=-1;publish();}
    }
    private static void clearWait(){
        waitDeadline=0;shownSeconds=-1;readyPlayer="";
    }
    private static void advance(MP_Net.Conn guest){
        field("hostReady",true);guest.ready=true;
        try{
            Method method=MP.class.getDeclaredMethod("tryEndTurn");method.setAccessible(true);method.invoke(null);
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static void updateWait(MP_Net.Conn guest,int turn,boolean hostReady,boolean guestReady,boolean forcePublish){
        String oldPlayer=readyPlayer;
        int oldSeconds=shownSeconds;
        if(guest==null||guest.civ<=0||!active()||hostReady==guestReady){
            clearWait();
        }else{
            long now=System.nanoTime();
            readyPlayer=hostReady?MP.nick:guest.nick;
            if(MP.turnSeconds>0){
                if(waitDeadline==0||!readyPlayer.equals(oldPlayer))waitDeadline=now+MP.turnSeconds*1_000_000_000L;
                shownSeconds=(int)Math.max(0,(waitDeadline-now+999_999_999L)/1_000_000_000L);
            }
        }
        if(forcePublish||!oldPlayer.equals(readyPlayer)||oldSeconds!=shownSeconds)send("kreyli_ready_state",turn,readyPlayer,shownSeconds);
        if(waitDeadline!=0&&shownSeconds==0&&SharedSupport.canEnd())advance(guest);
    }
    public static void tick(){
        if(MP.isClient()){acknowledge();return;}
        if(!MP.isHost()||!MP.started)return;
        var guest=guest();int turn=GameCalendar.TURNID;
        for(var iterator=readyRequests.entrySet().iterator();iterator.hasNext();){var entry=iterator.next();var conn=entry.getKey();var request=entry.getValue();
            if(conn!=guest||request.turn!=turn||conn.cmds>request.commands){iterator.remove();continue;}
            if(conn.cmds==request.commands){conn.ready=true;iterator.remove();}
        }
        if(vote.id!=0&&!vote.waiting&&vote.turn!=turn){vote.clear();publish();}
        boolean changedGuest=observedGuest!=guest, changedTurn=observedTurn!=turn;
        if(changedGuest){clearWait();if(vote.id!=0){vote.clear();publish();}readySequences.keySet().removeIf(c->c!=guest);readyRequests.keySet().removeIf(c->c!=guest);observedGuest=guest;lastGuest=false;}
        if(changedTurn){clearWait();observedTurn=turn;lastHost=false;lastGuest=false;}
        boolean h=Boolean.TRUE.equals(field("hostReady")),g=guest!=null&&guest.ready;
        if(vote.id!=0&&((lastHost&&!h)||(lastGuest&&!g))){vote.clear();publish();}
        if(h!=lastHost){lastHost=h;notice(MP.nick+(h?" готов к следующему ходу":" отменил готовность"));}
        if(g!=lastGuest){lastGuest=g;notice(guest.nick+(g?" готов к следующему ходу":" отменил готовность"));}
        if(active()&&guest!=null&&vote.mayAdvance(turn,guest)&&SharedSupport.canEnd()){
            vote.started();advance(guest);
        }else{
            updateWait(guest,turn,h,g,changedGuest||changedTurn);
        }
    }
    public static void updateUi(){
        KreyliTurnHud.update();
        if(!MP.playing()||!MP.inputOrders()||MP.hasBattleReports())return;
        var button=CFG.menus.getInGameProvInfo().getMenuElem(0);
        boolean ready=MP.isHost()?Boolean.TRUE.equals(field("hostReady")):MP.ready;
        button.setTextE(ready?"Отменить готовность":"Готов");button.setClickable(true);
    }
    /** Before clickEndTurn: remove only the multiplayer no-orders prompt. */
    public static void beforeReadyClick(){if(MP.playing()&&MP.inputOrders())CFG.core.getPlayer(CFG.PLAYER_TURN_ID).setNoOrders(false);}
    public static void reset(){clearWait();remoteReadyTurn=remoteSeconds=-1;remoteReadyPlayer="";vote.clear();remoteId=lastAckId=readySequence=0;readySequences.clear();readyRequests.clear();remoteCount=remoteRemaining=0;remoteHost=remoteGuest=lastHost=lastGuest=false;observedGuest=null;observedTurn=ackTurn=loadedTurn=lastAckTurn=-1;}
}
