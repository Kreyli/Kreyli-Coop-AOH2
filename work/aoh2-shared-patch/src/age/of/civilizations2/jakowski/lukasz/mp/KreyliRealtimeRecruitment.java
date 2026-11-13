package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.CFG;
import age.of.civilizations2.jakowski.lukasz.Civilization;
import age.of.civilizations2.jakowski.lukasz.GameCalendar;
import age.of.civilizations2.jakowski.lukasz.RecruitArmy;
import age.of.civilizations2.jakowski.lukasz.Core.Core;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import java.util.ArrayList;
import java.util.IdentityHashMap;

public final class KreyliRealtimeRecruitment {
    static final long TRAINING_NANOS=5_000_000_000L;
    private record Training(int amount,long due) {}
    private static final IdentityHashMap<RecruitArmy,Training> training=new IdentityHashMap<>();
    private static long nextCheck;

    private KreyliRealtimeRecruitment(){}

    /** Core's native moving-army renderer also owns recruitment badges. Restore
     * them when the realtime march renderer replaces that entire native pass. */
    public static void drawPending(SpriteBatch batch,float scale,int viewer){
        if(viewer<=0||viewer>=CFG.core.getCivsSize())return;
        boolean preview=CFG.menus.getInGame_ProvinceRecruit().getVisibleM();
        boolean instant=CFG.menus.getInGame_ProvinceRecruit_Instantly().getVisibleM();
        int alliance=CFG.core.getCiv(viewer).getAlliance();
        for(int civ=1;civ<CFG.core.getCivsSize();civ++){
            if(civ!=viewer&&(alliance<=0||CFG.core.getCiv(civ).getAlliance()!=alliance))continue;
            var country=CFG.core.getCiv(civ);
            for(int i=0;i<country.getRecruitArmySize();i++){
                var order=country.getRecruitArmy(i);
                if(civ==viewer&&(preview||instant)&&Core.ISIP(order.getProvinceID()))continue;
                drawBadge(batch,scale,civ,order.getProvinceID(),order.getArmy(),order.getArmyWidth(),false);
            }
        }
        if(preview){
            for(int i=Core.AMRCT.size()-1;i>=0;i--){
                var order=Core.AMRCT.get(i);
                drawBadge(batch,scale,viewer,order.OBC,order.SPR,width(order.SPR),true);
            }
        }else if(instant){
            int amount=CFG.menus.getInGame_ProvinceRecruit_Instantly().getMenuElem(2).getCurr();
            drawBadge(batch,scale,viewer,CFG.core.getActiveProvID(),amount,width(amount),false);
        }
    }

    private static int width(int amount){
        CFG.glyphLay.setText(CFG.fontArmy,Integer.toString(amount));
        return (int)CFG.glyphLay.width;
    }

    private static void drawBadge(SpriteBatch batch,float scale,int civ,int province,int amount,int width,boolean preview){
        if(province<0||province>=CFG.core.getProvinSize())return;
        var location=CFG.core.getProv(province);
        if(!location.getDrawProv())return;
        int x=(int)((location.getCeX()+location.getShPX()+location.getTranslateProvPosX())*scale);
        int y=CFG.core.getDrawProvinceArmy_EndPosY(province,scale)
                +(CFG.ARMY_HEIGHT+CFG.ARMY_BG_EXTRA_HEIGHT*2)/2+CFG.ARMY_BG_EXTRA_HEIGHT;
        if(preview)CFG.core.drawProvinceArmyWithFlag_Recruit2(batch,x,y,civ,amount,width);
        else CFG.core.drawProvinceArmyWithFlag_Recruit(batch,x,y,civ,amount,width);
    }

    private static boolean enabled(){
        return MP.isHost()&&MP.started&&KreyliLobby.realTime()&&!CFG.RECRUIT_AND_COUNTERATTACK;
    }

    private static boolean controls(Civilization country){
        return country.getCivId()==CFG.core.getPlayer(0).getCivId()
                ||MP.clients.stream().anyMatch(connection->connection.civ==country.getCivId());
    }

    public static void resolveNative(Civilization country){
        if(!enabled()||!controls(country))country.runRecruitArmyNT();
    }

    static void tick(){
        if(!enabled()||!MP.inputOrders())return;
        long now=KreyliRealtimePause.clock();
        if(now<nextCheck)return;
        nextCheck=now+100_000_000L;
        var current=new IdentityHashMap<RecruitArmy,Training>();
        boolean completed=false;
        for(int id=1;id<CFG.core.getCivsSize();id++){
            var country=CFG.core.getCiv(id);
            if(country.getRecruitArmySize()==0||!controls(country))continue;
            var due=new ArrayList<RecruitArmy>();
            for(var order:country.recruitArmy){
                var timer=training.get(order);
                if(timer==null||timer.amount()!=order.getArmy())timer=new Training(order.getArmy(),now+TRAINING_NANOS);
                if(now>=timer.due())due.add(order);
                else current.put(order,timer);
            }
            if(!due.isEmpty()){
                resolveDue(country,due);
                completed=true;
            }
        }
        training.clear();
        training.putAll(current);
        if(completed){
            CFG.setRenderO(true);
            KreyliOrderSync.mark();
            KreyliOrderSync.worldChanged();
        }
    }

    private static void resolveDue(Civilization country,ArrayList<RecruitArmy> due){
        var waiting=country.recruitArmy;
        waiting.removeAll(due);
        country.recruitArmy=due;
        country.recruitArmySize=due.size();
        try{
            country.runRecruitArmyNT();
        }finally{
            country.recruitArmy=waiting;
            country.recruitArmySize=waiting.size();
        }
    }

    static String save(){
        if(!enabled())return "";
        long now=KreyliRealtimePause.clock();
        var saved=new StringBuilder();
        for(int id=1;id<CFG.core.getCivsSize();id++){
            var country=CFG.core.getCiv(id);
            if(country.getRecruitArmySize()==0||!controls(country))continue;
            for(var order:country.recruitArmy){
                var timer=training.get(order);
                long remaining=timer==null||timer.amount()!=order.getArmy()?TRAINING_NANOS:Math.max(0,timer.due()-now);
                saved.append(id).append(',').append(order.getProvinceID()).append(',')
                        .append(order.getArmy()).append(',').append(remaining).append('\n');
            }
        }
        return saved.isEmpty()?"":GameCalendar.TURNID+"\n"+saved;
    }

    static void load(String saved){
        if(saved.isEmpty()||saved.length()>1_000_000)return;
        String[] rows=saved.split("\n");
        if(!rows[0].equals(Integer.toString(GameCalendar.TURNID)))return;
        long now=KreyliRealtimePause.clock();
        for(int i=1;i<rows.length;i++){
            String[] parts=rows[i].split(",");
            if(parts.length!=4)continue;
            try{
                int civ=Integer.parseInt(parts[0]),province=Integer.parseInt(parts[1]),amount=Integer.parseInt(parts[2]);
                long remaining=Long.parseLong(parts[3]);
                if(civ<=0||!MP.validCiv(civ)||province<0||province>=CFG.core.getProvinSize()
                        ||amount<=0||remaining<0||remaining>TRAINING_NANOS)continue;
                var country=CFG.core.getCiv(civ);
                if(country.getRecruitArmy_BasedOnProvinceID(province)>0)continue;
                var order=new RecruitArmy(province,amount);
                country.recruitArmy.add(order);
                country.recruitArmySize=country.recruitArmy.size();
                training.put(order,new Training(amount,now+remaining));
            }catch(NumberFormatException ignored){}
        }
    }

    static void reset(){training.clear();nextCheck=0;}
}
