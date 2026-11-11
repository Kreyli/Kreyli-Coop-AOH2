package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.*;
import com.badlogic.gdx.Gdx;
import java.lang.reflect.*;
import java.util.*;
import java.util.Map;

/** Host-owned turn readiness and skip votes. All mutations run on the game thread. */
public final class KreyliTurns {
    static final KreyliSkipVote vote=new KreyliSkipVote();
    private static int selected=1,remoteCount,remoteRemaining,observedTurn=-1,remoteSecondsLeft,remoteYes,remoteThreshold;
    private static long serial,remoteId,outcomeUntil;
    private static String remoteProposer="",outcome="";
    private static boolean remoteCanVote,remoteCanCancel,lastHost;
    private static int lastVoteSeconds=-1;
    private static final Object HOST=new Object();
    private static final Set<MP_Net.Conn> lastReady=Collections.newSetFromMap(new IdentityHashMap<>());
    private static long readySequence;
    private static long waitDeadline;
    private static Object waitInitiator;
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
    private static List<Object> voters(){
        List<Object> result=new ArrayList<>();result.add(HOST);
        for(var c:MP.clients)if(c.civ>0&&!MP.eliminated(c.civ))result.add(c);
        return result;
    }
    static MP_Net.Conn host(){return (MP_Net.Conn)field("host");}
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
        if(!MP.isHost()||!MP.started||!MP.inputOrders()||!MP.clients.contains(conn)||conn.civ<=0||data.length!=5||!(data[1] instanceof Integer turn)||!(data[2] instanceof Integer commands)||!(data[3] instanceof Long sequence)||!(data[4] instanceof Boolean ready))return true;
        long last=readySequences.getOrDefault(conn,0L);
        if(!KreyliReadyVote.valid(GameCalendar.TURNID,turn,conn.cmds,commands,last,sequence))return true;
        readySequences.put(conn,sequence);readyRequests.remove(conn);conn.ready=false;
        if(ready){if(commands==conn.cmds)conn.ready=true;else readyRequests.put(conn,new ReadyRequest(turn,commands,sequence));}
        return true;
    }
    public static int selectedSkipCount(){return selected;}
    public static boolean blocksNativeAdvance(){return KreyliRealtimePause.paused()||vote.id!=0&&!vote.waiting;}
    public static long activeSkipId(){return MP.isHost()?vote.id:remoteId;}
    public static boolean hasSkipVote(){return activeSkipId()!=0;}
    public static boolean skipCanRespond(){return active()&&(MP.isHost()?vote.canVote(HOST):remoteCanVote);}
    public static boolean skipCanCancel(){return MP.isHost()?vote.id!=0&&vote.proposer==HOST&&vote.remaining==0:remoteCanCancel;}
    public static String incomingSkipStatus(){
        if(!skipCanRespond())return "";
        return (MP.isHost()?name(vote.proposer):remoteProposer)+KreyliText.t(" предлагает пропустить: "," proposes to skip: ")+(MP.isHost()?vote.count:remoteCount);
    }
    private static String name(Object who){return who==HOST?MP.nick:who instanceof MP_Net.Conn c?c.nick:"";}
    public static String voteOutcome(){return System.nanoTime()<outcomeUntil?outcome:"";}
    public static String readinessStatus(){
        if(!active())return "";
        boolean host=MP.isHost();
        String name=host?readyPlayer:remoteReadyTurn==GameCalendar.TURNID?remoteReadyPlayer:"";
        if(name.isEmpty())return "";
        int seconds=host?shownSeconds:remoteSeconds;
        return name+KreyliText.t(" готов к ходу"," is ready")+(seconds<0?"":" · "+seconds+KreyliText.t(" с"," s"));
    }
    public static String skipStatus(){
        boolean host=MP.isHost();
        int remaining=host?vote.remaining:remoteRemaining;
        if(remaining>0){
            int count=host?vote.count:remoteCount;
            return KreyliText.t("Пропущено ходов: ","Turns skipped: ")+(count-remaining)+" / "+count;
        }
        if(!hasSkipVote())return voteOutcome();
        return (host?name(vote.proposer):remoteProposer)+" · "+(host?vote.count:remoteCount)+KreyliText.t(" ход. · "," turns · ")+(host?lastVoteSeconds:remoteSecondsLeft)+KreyliText.t(" с · "," s · ")+(host?vote.yes():remoteYes)+"/"+(host?vote.threshold():remoteThreshold);
    }
    public static void chooseSkip(){changeSkip(1);}
    public static void changeSkip(int change){selected=Math.max(1,Math.min(KreyliSkipVote.MAX_TURNS,selected+change));}
    public static void proposeSkip(){
        if(KreyliQuickGame.active()||KreyliLobby.realTime()||!active())return;
        if(MP.isHost())propose(selected,HOST);else if(MP.isClient()&&host()!=null)host().send("kreyli_skip_propose",GameCalendar.TURNID,selected);
    }
    public static void acceptSkip(){respond(true);}
    public static void rejectSkip(){respond(false);}
    private static void respond(boolean yes){
        if(!skipCanRespond())return;
        if(MP.isHost())cast(vote.id,GameCalendar.TURNID,HOST,yes);
        else if(host()!=null)host().send("kreyli_skip_vote",remoteId,GameCalendar.TURNID,yes);
    }
    public static void cancelSkip(){
        if(!skipCanCancel())return;
        if(MP.isHost())finish(KreyliText.t("Предложение отменено","Proposal cancelled"));
        else if(host()!=null)host().send("kreyli_skip_cancel",remoteId,GameCalendar.TURNID);
    }
    private static void propose(int count,Object sender){
        if(KreyliQuickGame.active()||KreyliLobby.realTime()||!active()||!vote.propose(++serial,GameCalendar.TURNID,count,voters(),sender,System.nanoTime()))return;
        clearWait();send("kreyli_ready_state",GameCalendar.TURNID,"",-1);
        lastVoteSeconds=30;outcome="";publish();
    }
    private static void cast(long id,int turn,Object sender,boolean yes){
        long now=System.nanoTime();
        if(vote.vote(id,turn,sender,yes,now)){
            if(vote.failed(now))finish(KreyliText.t("Предложение отклонено","Proposal rejected"));
            else publish();
        }
    }
    private static void publish(){
        for(var c:MP.clients)c.send("kreyli_skip_state_v2",vote.id,vote.count,vote.remaining,lastVoteSeconds,
                vote.yes(),vote.threshold(),name(vote.proposer),vote.canVote(c),vote.id!=0&&vote.remaining==0&&vote.proposer==c);
    }
    private static void finish(String result){
        vote.clear();publish();showOutcome(result);send("kreyli_skip_result",result);
    }
    private static void showOutcome(String result){outcome=result;outcomeUntil=System.nanoTime()+4_000_000_000L;}
    public static boolean hostMessage(MP_Net.Conn conn,Object[] data){
        if(KreyliRealtimePause.hostMessage(conn,data)||readyMessage(conn,data))return true;
        if(data.length==0||!(data[0] instanceof String op)||!op.startsWith("kreyli_skip_"))return false;
        if(!MP.isHost()||!MP.clients.contains(conn)||conn.nick==null||conn.civ<=0)return true;
        switch(op){
            case "kreyli_skip_propose"->{if(data.length==3&&data[1] instanceof Integer t&&t==GameCalendar.TURNID&&data[2] instanceof Integer n)propose(n,conn);}
            case "kreyli_skip_vote"->{if(data.length==4&&data[1] instanceof Long id&&data[2] instanceof Integer t&&data[3] instanceof Boolean yes&&t==GameCalendar.TURNID)cast(id,t,conn,yes);}
            case "kreyli_skip_cancel"->{if(data.length==3&&data[1] instanceof Long id&&id==vote.id&&data[2] instanceof Integer t&&t==GameCalendar.TURNID&&vote.remaining==0&&vote.proposer==conn)finish(KreyliText.t("Предложение отменено","Proposal cancelled"));}
        }
        return true;
    }
    public static boolean clientMessage(Object[] data){
        if(KreyliRealtimePause.clientMessage(data))return true;
        if(data.length==4&&"kreyli_ready_state".equals(data[0])){
            if(MP.isClient()&&data[1] instanceof Integer turn&&data[2] instanceof String name&&data[3] instanceof Integer seconds){
                remoteReadyTurn=turn;remoteReadyPlayer=name;remoteSeconds=seconds;
            }
            return true;
        }
        if(data.length==2&&"kreyli_turn_notice".equals(data[0])&&data[1] instanceof String text){MP.toast(text,false);return true;}
        if(data.length==2&&"kreyli_skip_result".equals(data[0])&&data[1] instanceof String result){showOutcome(result);return true;}
        if(data.length==0||!"kreyli_skip_state_v2".equals(data[0]))return false;
        if(MP.isClient()&&data.length==10&&data[1] instanceof Long id&&data[2] instanceof Integer count&&data[3] instanceof Integer remaining&&data[4] instanceof Integer seconds&&data[5] instanceof Integer yes&&data[6] instanceof Integer threshold&&data[7] instanceof String proposer&&data[8] instanceof Boolean respond&&data[9] instanceof Boolean cancel){
            remoteId=id;remoteCount=count;remoteRemaining=remaining;remoteSecondsLeft=seconds;remoteYes=yes;remoteThreshold=threshold;
            remoteProposer=proposer;remoteCanVote=respond;remoteCanCancel=cancel;
        }
        return true;
    }
    public static void afterSnapshot(){
        int camX=CFG.map!=null&&CFG.map.getMpC()!=null?CFG.map.getMpC().getPX():0;
        int camY=CFG.map!=null&&CFG.map.getMpC()!=null?CFG.map.getMpC().getPY():0;
        if(CFG.map!=null&&CFG.map.getMpC()!=null){CFG.map.getMpC().setNewPosX(camX);CFG.map.getMpC().setNewPosY(camY);}
    }
    /** Call only for a genuine local or accepted remote command during INPUT_ORDERS. */
    public static void order(){
        if(MP.isHost()&&vote.remaining>0)finish(KreyliText.t("Пропуск прерван новым приказом","Skip interrupted by a new order"));
    }
    public static void newTurn(){
        if(!MP.isHost())return;
        clearWait();
        readyRequests.clear();
        for(var connection:MP.clients)connection.ready=false;
        if(vote.id!=0){
            boolean wasSkipping=vote.waiting;
            vote.completed(GameCalendar.TURNID);
            if(vote.id==0)finish(wasSkipping?KreyliText.t("Пропуск завершён","Skip completed"):KreyliText.t("Предложение закрыто","Proposal closed"));else publish();
        }
    }
    private static void clearWait(){
        waitDeadline=0;waitInitiator=null;shownSeconds=-1;readyPlayer="";
    }
    private static void advance(){
        field("hostReady",true);
        for(var client:MP.clients)if(client.civ>0)client.ready=true;
        try{
            Method method=MP.class.getDeclaredMethod("tryEndTurn");method.setAccessible(true);method.invoke(null);
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static void updateWait(int turn,boolean hostReady,List<MP_Net.Conn> players,boolean forcePublish){
        String oldPlayer=readyPlayer;
        int oldSeconds=shownSeconds;
        long readyCount=players.stream().filter(c->c.ready).count()+(hostReady?1:0);
        if(players.isEmpty()||readyCount==0||readyCount==players.size()+1||!active()){
            clearWait();
        }else{
            long now=System.nanoTime();
            if(!(waitInitiator==HOST?hostReady:waitInitiator instanceof MP_Net.Conn c&&players.contains(c)&&c.ready)){
                waitInitiator=hostReady?HOST:players.stream().filter(c->c.ready).findFirst().orElseThrow();
                readyPlayer=name(waitInitiator);waitDeadline=0;
            }
            if(MP.turnSeconds>0){
                if(waitDeadline==0)waitDeadline=now+MP.turnSeconds*1_000_000_000L;
                shownSeconds=(int)Math.max(0,(waitDeadline-now+999_999_999L)/1_000_000_000L);
            }
        }
        if(forcePublish||!oldPlayer.equals(readyPlayer)||oldSeconds!=shownSeconds)send("kreyli_ready_state",turn,readyPlayer,shownSeconds);
        if(waitDeadline!=0&&shownSeconds==0&&SharedSupport.canEnd())advance();
    }
    private static final long AUTO_TURN_MILLIS=5_000;
    private static long automaticTurn, quickTurnDeadline;
    private static int quickTurn=-1;
    /** Stretch the native marker animation across the auto-turn interval. */
    public static float marchMillis(float nativeMillis){
        if(KreyliRealtimePause.paused())return Float.POSITIVE_INFINITY;
        return KreyliLobby.realTime()&&MP.playing()?KreyliRealtimeMarch.DURATION_MILLIS:nativeMillis;
    }
    private static void tickAutomaticTurn(){
        KreyliRealtimeUi.finishAutomaticTurn();
        KreyliRealtimePause.tick();
        if(KreyliRealtimePause.paused())return;
        KreyliRealtimeMarch.tick();
        KreyliRealtimeRecruitment.tick();
        KreyliRealtimeEconomy.tick();
        KreyliRealtimeConstruction.tick();
        if(!active()){
            automaticTurn=0;
            return;
        }
        long now=KreyliRealtimePause.clock();
        if(automaticTurn==0)automaticTurn=now+AUTO_TURN_MILLIS*1_000_000L;
        if(now<automaticTurn||!SharedSupport.canEnd())return;
        if(CFG.menus.getInGame_ProvinceMoveUnits_Visible()&&now-automaticTurn<1_500_000_000L)return;
        automaticTurn=now+AUTO_TURN_MILLIS*1_000_000L;
        KreyliRealtimeUi.beforeAutomaticTurn();
        advance();
    }
    private static boolean tickQuickTurn(){
        if(!KreyliQuickGame.running()||!active()){
            quickTurnDeadline=0;
            return false;
        }
        long now=System.nanoTime();
        int turn=GameCalendar.TURNID;
        if(quickTurnDeadline==0||quickTurn!=turn){
            quickTurn=turn;
            quickTurnDeadline=now+KreyliQuickRules.TURN_MILLIS*1_000_000L;
        }
        if(now<quickTurnDeadline||!SharedSupport.canEnd())return false;
        advance();
        return true;
    }
    public static void tick(){
        if(!MP.isHost()||!MP.started)return;
        if(KreyliQuickGame.active()&&tickQuickTurn())return;
        if(KreyliLobby.realTime()){tickAutomaticTurn();return;}
        int turn=GameCalendar.TURNID;
        for(var iterator=readyRequests.entrySet().iterator();iterator.hasNext();){
            var entry=iterator.next();var conn=entry.getKey();var request=entry.getValue();
            if(!MP.clients.contains(conn)||request.turn!=turn||conn.cmds>request.commands){iterator.remove();continue;}
            if(conn.cmds==request.commands){conn.ready=true;iterator.remove();}
        }
        readySequences.keySet().retainAll(MP.clients);
        var players=MP.clients.stream().filter(c->c.civ>0&&!MP.eliminated(c.civ)).toList();
        boolean changedTurn=observedTurn!=turn;
        if(changedTurn){clearWait();observedTurn=turn;lastHost=false;lastReady.clear();}
        boolean hostReady=Boolean.TRUE.equals(field("hostReady"));
        if(hostReady!=lastHost){lastHost=hostReady;notice(MP.nick+(hostReady?KreyliText.t(" готов к следующему ходу"," is ready for the next turn"):KreyliText.t(" отменил готовность"," is no longer ready")));}
        lastReady.retainAll(players);
        for(var c:players){
            if(c.ready&&lastReady.add(c))notice(c.nick+KreyliText.t(" готов к следующему ходу"," is ready for the next turn"));
            else if(!c.ready&&lastReady.remove(c))notice(c.nick+KreyliText.t(" отменил готовность"," is no longer ready"));
        }
        if(vote.id!=0){
            long now=System.nanoTime();
            if(!vote.sameRoster(voters()))finish(KreyliText.t("Состав игроков изменился","Player roster changed"));
            else if(vote.failed(now))finish(KreyliText.t("Большинство не набрано","Majority not reached"));
            else if(vote.mayAdvance(turn)&&active()&&SharedSupport.synchronizedToAdvance()){vote.started();advance();}
            else if(vote.remaining==0){
                int seconds=(int)Math.max(0,(vote.deadline-now+999_999_999L)/1_000_000_000L);
                if(seconds!=lastVoteSeconds){lastVoteSeconds=seconds;publish();}
            }
        }else updateWait(turn,hostReady,players,changedTurn);
    }
    public static boolean blockRealtimeReady(){
        return KreyliLobby.realTime()&&MP.playing()&&!MP.hasBattleReports();
    }
    public static boolean hideRealtimeControls(){
        if(!KreyliLobby.realTime()||!MP.playing()||CFG.menus==null||!CFG.menus.getInGameView())return false;
        var top=CFG.menus.getInGameMenu();
        if(top instanceof age.of.civilizations2.jakowski.lukasz.Menus.ZRest.Menu_InGame_2)
            top.getMenuElem(5).setVisibleE(false);
        var ready=CFG.menus.getInGameProvInfo().getMenuElem(0);
        ready.setVisibleE(MP.hasBattleReports());
        if(MP.hasBattleReports())return false;
        ready.setClickable(false);
        return true;
    }
    public static void updateUi(){
        KreyliTurnHud.update();
        if(KreyliLobby.realTime()||!MP.playing()||!MP.inputOrders()||MP.hasBattleReports())return;
        var button=CFG.menus.getInGameProvInfo().getMenuElem(0);
        boolean ready=MP.isHost()?Boolean.TRUE.equals(field("hostReady")):MP.ready;
        button.setTextE(ready?KreyliText.t("Отменить готовность","Cancel readiness"):KreyliText.t("Готов","Ready"));button.setClickable(true);
    }
    /** Before clickEndTurn: remove only the multiplayer no-orders prompt. */
    public static void beforeReadyClick(){if(MP.playing()&&MP.inputOrders())CFG.core.getPlayer(CFG.PLAYER_TURN_ID).setNoOrders(false);}
    public static void reset(){automaticTurn=quickTurnDeadline=0;quickTurn=-1;KreyliRealtimePause.reset();KreyliRealtimeUi.reset();KreyliRealtimeRecruitment.reset();KreyliRealtimeEconomy.reset();KreyliRealtimeConstruction.reset();clearWait();remoteReadyTurn=remoteSeconds=-1;remoteReadyPlayer="";vote.clear();remoteId=readySequence=outcomeUntil=0;readySequences.clear();readyRequests.clear();lastReady.clear();remoteCount=remoteRemaining=0;remoteCanVote=remoteCanCancel=lastHost=false;observedTurn=-1;}
}
