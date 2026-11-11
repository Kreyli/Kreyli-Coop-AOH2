package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.CFG;
import age.of.civilizations2.jakowski.lukasz.Images;
import age.of.civilizations2.jakowski.lukasz.Province;
import age.of.civilizations2.jakowski.lukasz.Renderer;
import age.of.civilizations2.jakowski.lukasz.Report_Data;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import static age.of.civilizations2.jakowski.lukasz.mp.KreyliText.t;

/** Local notices for host-confirmed territory changes and an opt-in battle archive. */
public final class KreyliRealtimeAlerts {
    private static final String MESSAGE="kreyli_territory";
    private static final long NOTICE_NANOS=14_000_000_000L;
    private static final int MAX_PENDING=128, MAX_ARCHIVE=20, MAX_NOTICES=4, ARCHIVE_ROWS=6;
    private static final Color BORDER=new Color(0xc39648ff), PANEL=new Color(0x101b2af6);
    private static final Color HOVER=new Color(0x2b405aff);

    private record Capture(int province,int former,int owner){
        boolean concerns(int civ){return civ>0&&(civ==former||civ==owner);}
    }
    private record Notice(Capture capture,long expires){}
    private record Hit(int x,int y,int width,int height,Object action){
        boolean contains(int px,int py){return px>=x&&px<x+width&&py>=y&&py<y+height;}
    }
    private static final Object ARCHIVE_BUTTON=new Object();
    private static final ArrayDeque<Capture> pending=new ArrayDeque<>();
    private static final ArrayDeque<Notice> notices=new ArrayDeque<>();
    private static final ArrayDeque<Report_Data> archive=new ArrayDeque<>();
    private static final ArrayList<Hit> hits=new ArrayList<>();
    private static boolean archiveOpen;
    private static Object pressed;

    private KreyliRealtimeAlerts(){}

    public static boolean active(){return KreyliLobby.realTime()&&MP.playing();}
    public static boolean showNativeReport(boolean requested){return requested&&!active();}

    private static int localCountry(){
        if(MP.isHost()&&CFG.core.getPlayersSize()>0)return CFG.core.getPlayer(0).getCivId();
        return MP.myCiv;
    }
    private static boolean provinceExists(int id){return id>=0&&id<CFG.core.getProvinSize();}

    /** Province ownership can change on a native worker. Deliver notices on the main thread. */
    public static void capture(Province province,int owner){
        if(!MP.isHost()||!MP.started||!KreyliLobby.realTime()||MP.applying)return;
        int former=province.getCivId();
        if(former<=0||owner<=0||former==owner||!MP.validCiv(former)||!MP.validCiv(owner)
                ||!CFG.core.getCivsAtWar(former,owner))return;
        synchronized(pending){
            if(pending.size()==MAX_PENDING)pending.removeFirst();
            pending.addLast(new Capture(province.getProvID(),former,owner));
        }
    }

    public static void tick(){
        if(!active())return;
        long now=System.nanoTime();
        while(!notices.isEmpty()&&notices.peekFirst().expires()<=now){
            notices.removeFirst();
            CFG.setRenderO(true);
        }
        if(!MP.isHost()||!MP.inputOrders())return;
        var reports=CFG.gameAction.battleReports;
        int local=localCountry();
        for(int i=Math.max(0,reports.size()-MAX_ARCHIVE);i<reports.size();i++){
            Report_Data report=reports.get(i);
            if(report!=null&&(report.lAttackers_IDs.contains(local)||report.lDefenders_IDs.contains(local)))store(report);
        }
        List<Capture> changes;
        synchronized(pending){
            if(pending.isEmpty())return;
            changes=new ArrayList<>(pending);
            pending.clear();
        }
        for(Capture capture:changes){
            if(!provinceExists(capture.province())||CFG.core.getProv(capture.province()).getCivId()!=capture.owner())continue;
            if(capture.concerns(local))notice(capture,now);
            for(var guest:MP.clients)
                if(capture.concerns(guest.civ))guest.send(MESSAGE,capture.province(),capture.former(),capture.owner());
        }
    }

    public static boolean receive(Object[] message){
        if(message.length==0||!MESSAGE.equals(message[0]))return false;
        if(active()&&MP.isClient()&&message.length==4&&message[1] instanceof Integer province
                &&message[2] instanceof Integer former&&message[3] instanceof Integer owner
                &&provinceExists(province)&&former>0&&owner>0&&former!=owner&&MP.validCiv(former)&&MP.validCiv(owner)){
            var capture=new Capture(province,former,owner);
            if(capture.concerns(localCountry()))notice(capture,System.nanoTime());
        }
        return true;
    }

    private static void notice(Capture capture,long now){
        if(!notices.isEmpty()&&notices.peekLast().capture().equals(capture))return;
        if(notices.size()==MAX_NOTICES)notices.removeFirst();
        notices.addLast(new Notice(capture,now+NOTICE_NANOS));
        CFG.setRenderO(true);
    }

    /** Drain the native automatic popup queue without losing the report data. */
    public static boolean archiveQueued(List<Report_Data> reports){
        if(!active())return false;
        for(Report_Data report:reports)store(report);
        reports.clear();
        return true;
    }

    private static void store(Report_Data report){
        if(report==null||!provinceExists(report.iBattleOfProvinceID)||archive.contains(report))return;
        if(archive.size()==MAX_ARCHIVE)archive.removeFirst();
        archive.addLast(report);
        CFG.setRenderO(true);
    }

    private static boolean visible(){
        return active()&&CFG.menus!=null&&CFG.menus.getInGameView()&&!CFG.menus.getInGameView_Options()
                &&!CFG.menus.getInGame_Report_Visible()&&!CFG.menus.getVisibleInGame_Event();
    }

    static void draw(SpriteBatch batch,int top){
        hits.clear();
        if(!visible())return;
        int gap=Math.max(5,CFG.PADD), row=Math.max(26,CFG.TEXT_HEIGHT_DEFAULT_SMALL+gap*2);
        int width=Math.min(CFG.GAMEWIDTH-gap*2,CFG.BUTTON_W*3);
        int x=CFG.GAMEWIDTH-width-gap, y=top+gap;
        float color=batch.getPackedColor();
        try{
            for(var it=notices.descendingIterator();it.hasNext()&&y+row<=CFG.GAMEHEIGHT;){
                Capture capture=it.next().capture();
                boolean loss=capture.former()==localCountry();
                String province=CFG.core.getProv(capture.province()).getProvName();
                y=card(batch,x,y,width,row,gap,
                        (loss?t("Территория потеряна: ","Territory lost: "):t("Территория захвачена: ","Territory captured: "))+province,
                        loss?Color.SALMON:Color.GOLD,capture);
            }
            if(archive.isEmpty()||y+row>CFG.GAMEHEIGHT)return;
            y=card(batch,x,y,width,row,gap,t("Журнал боёв","Battle log"),Color.WHITE,ARCHIVE_BUTTON);
            if(!archiveOpen)return;
            int remaining=ARCHIVE_ROWS;
            for(var it=archive.descendingIterator();it.hasNext()&&remaining-->0&&y+row<=CFG.GAMEHEIGHT;){
                var report=it.next();
                if(!provinceExists(report.iBattleOfProvinceID))continue;
                String name=CFG.core.getProv(report.iBattleOfProvinceID).getProvName();
                y=card(batch,x,y,width,row,gap,t("Бой за ","Battle for ")+name,Color.WHITE,report);
            }
        }finally{batch.setPackedColor(color);}
    }

    private static int card(SpriteBatch batch,int x,int y,int width,int height,int gap,String text,Color color,Object action){
        var hit=new Hit(x,y,width,height,action);
        hits.add(hit);
        batch.setColor(BORDER);
        Images.pix.draw(batch,x,y,width,height);
        batch.setColor(hit.contains(Gdx.input.getX(),Gdx.input.getY())?HOVER:PANEL);
        Images.pix.draw(batch,x+1,y+1,width-2,height-2);
        batch.setColor(Color.WHITE);
        Renderer.drawTextWithShadow(batch,MP_UI.font(),MP_UI.fit(text,width-gap*2),
                x+gap,y+(height-CFG.TEXT_HEIGHT_DEFAULT_SMALL)/2,color);
        return y+height+gap;
    }

    static boolean touchDown(int x,int y,int pointer,int button){
        if(pointer!=0||button!=Input.Buttons.LEFT||!visible())return false;
        pressed=hitAt(x,y);
        return pressed!=null;
    }

    static boolean touchUp(int x,int y,int pointer,int button){
        if(pointer!=0||pressed==null)return false;
        Object action=pressed;
        pressed=null;
        if(button!=Input.Buttons.LEFT||!visible()||action!=hitAt(x,y))return true;
        if(action==ARCHIVE_BUTTON)archiveOpen=!archiveOpen;
        else if(action instanceof Capture capture&&provinceExists(capture.province()))
            CFG.map.getMpC().centerToProvID(capture.province());
        else if(action instanceof Report_Data report&&provinceExists(report.iBattleOfProvinceID)){
            archiveOpen=false;
            CFG.reportData=report;
            CFG.menus.rebuildInGame_Report();
        }
        CFG.setRenderO(true);
        return true;
    }

    private static Object hitAt(int x,int y){
        for(Hit hit:hits)if(hit.contains(x,y))return hit.action();
        return null;
    }

    public static void reset(){
        synchronized(pending){pending.clear();}
        notices.clear();
        archive.clear();
        hits.clear();
        archiveOpen=false;
        pressed=null;
    }
}