package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.CFG;
import age.of.civilizations2.jakowski.lukasz.Images;
import age.of.civilizations2.jakowski.lukasz.Renderer;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import java.util.HashSet;
import java.util.Set;

import static age.of.civilizations2.jakowski.lukasz.mp.KreyliText.t;

/** Host-owned unanimous pause and resume voting, independent of battle readiness. */
public final class KreyliRealtimePause {
    private static final String REQUEST="kreyli_pause_request", VOTE="kreyli_pause_vote", STATE="kreyli_pause_state";
    private static final long VOTE_TIMEOUT=30_000_000_000L;
    private static final Object HOST=new Object();
    private static final Color BORDER=new Color(0xc39648ff), BACKGROUND=new Color(0x101b2af6), HOVER=new Color(0x2b405aff);

    private static Set<Object> electorate=Set.of(), previousRoster=Set.of();
    private static final Set<Object> yes=new HashSet<>();
    private static long serial, voteId, deadline, pausedAt, totalPaused;
    private static boolean remotePaused, remoteCanVote;
    private static long remoteVoteId;
    private static int remoteYes, remoteTotal;
    private static long pressedVote;
    private static boolean pressedPaused;
    private static int pressed, x, y, width, height;

    private KreyliRealtimePause(){}

    public static boolean paused(){
        return KreyliLobby.realTime()&&(MP.isHost()?pausedAt!=0:remotePaused);
    }

    static long clock(){
        return (pausedAt==0?System.nanoTime():pausedAt)-totalPaused;
    }

    private static boolean inMatch(){
        return KreyliLobby.realTime()&&MP.playing();
    }

    private static boolean eligible(MP_Net.Conn conn){
        return conn.civ>0&&!MP.eliminated(conn.civ);
    }

    private static Set<Object> roster(){
        var players=new HashSet<>();
        players.add(HOST);
        for(var conn:MP.clients)if(eligible(conn))players.add(conn);
        return players;
    }

    static void tick(){
        if(!MP.isHost()||!MP.started||!KreyliLobby.realTime())return;
        var players=roster();
        boolean rosterChanged=!players.equals(previousRoster);
        previousRoster=players;
        if(voteId!=0&&(!players.equals(electorate)||yes.size()<electorate.size()&&System.nanoTime()>=deadline)){
            clearVote();
            publish();
        }else if(voteId!=0&&yes.size()==electorate.size()&&MP.inputOrders()){
            long now=System.nanoTime();
            if(pausedAt==0)pausedAt=now;
            else{
                totalPaused+=now-pausedAt;
                pausedAt=0;
            }
            clearVote();
            publish();
        }else if(rosterChanged)publish();
    }

    private static void clearVote(){
        voteId=deadline=0;
        electorate=Set.of();
        yes.clear();
    }

    private static void propose(Object player,boolean expectedPaused){
        if(voteId!=0||paused()!=expectedPaused)return;
        var players=roster();
        if(!players.contains(player))return;
        electorate=players;
        voteId=++serial;
        deadline=System.nanoTime()+VOTE_TIMEOUT;
        yes.add(player);
        publish();
    }

    private static void vote(Object player,long id,boolean approve){
        if(voteId==0||voteId!=id||System.nanoTime()>=deadline||!electorate.contains(player)||yes.contains(player))return;
        if(approve)yes.add(player);
        else clearVote();
        publish();
    }

    private static void publish(){
        for(var conn:MP.clients){
            conn.send(STATE,paused(),voteId,yes.size(),electorate.size(),electorate.contains(conn)&&!yes.contains(conn));
        }
        CFG.setRenderO(true);
    }

    static boolean hostMessage(MP_Net.Conn conn,Object[] data){
        if(data.length==0||!REQUEST.equals(data[0])&&!VOTE.equals(data[0]))return false;
        if(!inMatch()||!MP.isHost()||!MP.clients.contains(conn)||!eligible(conn))return true;
        if(REQUEST.equals(data[0])&&data.length==2&&data[1] instanceof Boolean expected)propose(conn,expected);
        if(VOTE.equals(data[0])&&data.length==3&&data[1] instanceof Long id&&data[2] instanceof Boolean approve)vote(conn,id,approve);
        return true;
    }

    static boolean clientMessage(Object[] data){
        if(data.length==0||!STATE.equals(data[0]))return false;
        if(MP.isClient()&&data.length==6&&data[1] instanceof Boolean stopped&&data[2] instanceof Long id
                &&data[3] instanceof Integer accepted&&data[4] instanceof Integer total&&data[5] instanceof Boolean canVote
                &&id>=0&&accepted>=0&&accepted<=total&&total<=10){
            remotePaused=stopped;
            remoteVoteId=id;
            remoteYes=accepted;
            remoteTotal=total;
            remoteCanVote=canVote;
            CFG.setRenderO(true);
        }
        return true;
    }

    static long currentVote(){return MP.isHost()?voteId:remoteVoteId;}
    static boolean canVote(){return MP.isHost()?electorate.contains(HOST)&&!yes.contains(HOST):remoteCanVote;}

    private static boolean visible(){
        return inMatch()&&CFG.menus!=null&&CFG.menus.getInGameView()&&!CFG.menus.getInGameView_Options()
                &&!CFG.menus.getInGame_Report_Visible()&&!CFG.menus.getVisibleInGame_Event();
    }

    static int top(){
        int gap=Math.max(5,CFG.PADD);
        return CFG.GAMEHEIGHT-gap-Math.max(26,CFG.TEXT_HEIGHT_DEFAULT_SMALL+gap*2);
    }

    public static void draw(SpriteBatch batch){
        if(!visible())return;
        int gap=Math.max(5,CFG.PADD);
        width=Math.min(CFG.GAMEWIDTH-gap*2,CFG.BUTTON_W*3);
        y=top();
        height=CFG.GAMEHEIGHT-gap-y;
        x=CFG.GAMEWIDTH-width-gap;
        float original=batch.getPackedColor();
        try{
            long id=currentVote();
            if(id!=0&&canVote()){
                String approval=paused()?t("Продолжить","Resume"):t("Пауза","Pause");
                button(batch,x,width/2,approval,true);
                button(batch,x+width/2,width-width/2,t("Отклонить","Reject"),true);
            }else if(id!=0){
                int accepted=MP.isHost()?yes.size():remoteYes;
                int total=MP.isHost()?electorate.size():remoteTotal;
                String action=paused()?t("Продолжение","Resume vote"):t("Пауза","Pause vote");
                button(batch,x,width,action+" - "+accepted+"/"+total,false);
            }else{
                button(batch,x,width,paused()?t("На паузе - Продолжить","Paused - Resume"):t("Предложить паузу","Request pause"),true);
            }
        }finally{
            batch.setPackedColor(original);
        }
    }

    private static void button(SpriteBatch batch,int left,int span,String label,boolean clickable){
        batch.setColor(BORDER);
        Images.pix.draw(batch,left,y,span,height);
        int mx=Gdx.input.getX(),my=Gdx.input.getY();
        boolean hover=clickable&&mx>=left&&mx<left+span&&my>=y&&my<y+height;
        batch.setColor(hover?HOVER:BACKGROUND);
        Images.pix.draw(batch,left+1,y+1,span-2,height-2);
        batch.setColor(Color.WHITE);
        String text=MP_UI.fit(label,span-CFG.PADD*2);
        Renderer.drawTextWithShadow(batch,MP_UI.font(),text,left+(span-MP_UI.textWidth(text))/2,
                y+(height-CFG.TEXT_HEIGHT_DEFAULT_SMALL)/2,Color.WHITE);
    }

    private static int target(int px,int py){
        if(!visible()||px<x||px>=x+width||py<y||py>=y+height)return 0;
        if(currentVote()==0)return 1;
        if(!canVote())return 0;
        return px<x+width/2?2:3;
    }

    public static boolean touchDown(int px,int py,int pointer,int button){
        if(pointer!=0||button!=Input.Buttons.LEFT)return false;
        pressed=target(px,py);
        pressedVote=currentVote();
        pressedPaused=paused();
        return pressed!=0;
    }

    public static boolean touchUp(int px,int py,int pointer,int button){
        if(pointer!=0||button!=Input.Buttons.LEFT||pressed==0)return false;
        int action=pressed;
        pressed=0;
        if(action!=target(px,py)||pressedVote!=currentVote()||pressedPaused!=paused())return true;
        if(action==1)requestLocal();
        else respondLocal(action==2);
        return true;
    }

    static void requestLocal(){
        if(!inMatch()||currentVote()!=0)return;
        if(MP.isHost())propose(HOST,paused());
        else if(MP.isClient()){
            var host=KreyliTurns.host();
            if(host!=null)host.send(REQUEST,paused());
        }
    }

    static void respondLocal(boolean approve){
        if(!inMatch()||currentVote()==0||!canVote())return;
        if(MP.isHost())vote(HOST,currentVote(),approve);
        else if(MP.isClient()){
            var host=KreyliTurns.host();
            if(host!=null)host.send(VOTE,currentVote(),approve);
        }
    }

    static void reset(){
        clearVote();
        serial=pausedAt=totalPaused=remoteVoteId=pressedVote=0;
        previousRoster=Set.of();
        remotePaused=remoteCanVote=pressedPaused=false;
        remoteYes=remoteTotal=pressed=width=0;
    }
}