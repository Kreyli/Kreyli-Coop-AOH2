package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.AI.AI;
import age.of.civilizations2.jakowski.lukasz.CFG;
import age.of.civilizations2.jakowski.lukasz.Civilization;
import age.of.civilizations2.jakowski.lukasz.GameAction;
import age.of.civilizations2.jakowski.lukasz.GameCalendar;
import age.of.civilizations2.jakowski.lukasz.MoveUnitsB.MoveUnits;
import age.of.civilizations2.jakowski.lukasz.Province;
import age.of.civilizations2.jakowski.lukasz.RegroupArmy.RegroupArmy;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;

/** Runs supported marches between adjacent land provinces on the host clock. */
public final class KreyliRealtimeMarch {
    static final int DURATION_MILLIS=5_000;
    private static final long DURATION_NANOS=DURATION_MILLIS*1_000_000L;
    private static final long AI_ATTACK_SPACING_NANOS=3_000_000_000L;
    private static final HashMap<Integer,Long> nextAiAttack=new HashMap<>();
    private static final IdentityHashMap<MoveUnits,Long> starts=new IdentityHashMap<>();
    private record Pending(int civ,MoveUnits move,long start) {}
    private static final List<Pending> pending=new ArrayList<>();
    private static volatile List<Pending> heldForDraw=List.of();
    private static long nextCheck;

    private KreyliRealtimeMarch(){}

    private static boolean enabled(){
        return MP.isHost()&&MP.started&&KreyliLobby.realTime();
    }

    private static boolean playerCountry(int civ){
        if(CFG.core.getPlayersSize()>0&&CFG.core.getPlayer(0).getCivId()==civ)return true;
        for(var guest:MP.clients)if(guest.civ==civ)return true;
        return false;
    }

    public static void finishAiOrders(AI ai){
        try{
            ai.turnOrders_End();
        }finally{
            // Actions refunds queued moves to the source immediately after AI planning.
            // Detach these first so the native turn never executes a timed order.
            if(enabled())detachTimedOrders();
        }
    }

    private static boolean eligible(int civ,MoveUnits move){
        if(move.getNumberOfUnits()<=0||move.getFromProviID()==move.getToProvID())return false;
        var from=CFG.core.getProv(move.getFromProviID());
        var to=CFG.core.getProv(move.getToProvID());
        if(from.getSeaProv()||to.getSeaProv()||from.getCivId()!=civ)return false;
        if(to.getCivId()!=civ&&!KreyliRealtimeCombat.hostile(civ,to,move.getNumberOfUnits()))return false;
        for(int i=0;i<from.getNeighProvincesSize();i++){
            if(from.getNeighProvinces(i)==move.getToProvID())return true;
        }
        return false;
    }

    public static boolean firstRegroupLeg(GameAction action,int from,int next,int troops,int civ,
                                          boolean drawLine,boolean useMovement,RegroupArmy route){
        if(MP.isClient()&&MP.playing()&&KreyliLobby.realTime()&&civ==MP.myCiv){
            if(MP.inputOrders()&&!KreyliRealtimePause.paused()){
                KreyliOrderSync.requestRoute(from,route.getToProvinceID(),troops);
            }
            return false;
        }
        return action.moveArmyAction(from,next,troops,civ,drawLine,useMovement);
    }

    static void startGuestRoute(int civ,int from,int destination,int troops){
        if(!enabled()||!MP.inputOrders()||KreyliRealtimePause.paused()||!MP.validCiv(civ))return;
        int provinces=CFG.core.getProvinSize();
        if(from<0||from>=provinces||destination<0||destination>=provinces||from==destination||troops<=0)return;
        var origin=CFG.core.getProv(from);
        if(origin.getCivId()!=civ||origin.getArmyCivID1(civ)<troops)return;
        var route=new RegroupArmy(civ,from,destination);
        if(route.getRouteSize()==0)return;
        int first=route.getRoute(0);
        if(!CFG.gameAction.moveArmyAction(from,first,troops,civ,true,true))return;
        if(route.getRouteSize()>1){
            route.setFromProvinceID(first);
            route.removeRoute(0);
            route.setNumOfUnits(troops);
            CFG.core.getCiv(civ).addRegroupArmy(route);
        }
        KreyliOrderSync.mark();
    }

    public static boolean waitForRegroup(Civilization country,int index){
        if(!enabled())return false;
        var route=country.getRegroupArmy(index);
        int civ=country.getCivId();
        for(var march:pending){
            if(march.civ()==civ&&follows(route,march.move()))return true;
        }
        for(int i=0;i<country.moveUnitsSize();i++){
            var move=country.getMoveUnits(i);
            if(follows(route,move)&&(starts.containsKey(move)||eligible(civ,move)))return true;
        }
        return false;
    }

    private static boolean follows(RegroupArmy route,MoveUnits move){
        return follows(route,move.getToProvID(),move.getNumberOfUnits());
    }

    private static boolean follows(RegroupArmy route,int waypoint,int troops){
        return route.getFromProvinceID()==waypoint&&route.getNumOfUnits()==troops;
    }

    private static int followingRoute(Civilization country,int waypoint,int troops){
        for(int i=0;i<country.getRegroupArmySize();i++){
            if(follows(country.getRegroupArmy(i),waypoint,troops))return i;
        }
        return -1;
    }

    private static void continueRoute(Civilization country,int waypoint,int sent,int arrived){
        int index=followingRoute(country,waypoint,sent);
        if(index<0)return;
        int civ=country.getCivId();
        var route=country.getRegroupArmy(index);
        int from=route.getFromProvinceID();
        var province=CFG.core.getProv(from);
        int troops=Math.min(arrived,province.getArmyCivID1(civ));
        if(province.getCivId()!=civ||troops<=0||route.getRouteSize()==0||!route.continueMovingArmy(civ)){
            country.removeRegroupArmy(index);
            return;
        }
        int next=route.getRoute(0);
        if(!RegroupArmy.canBeUsedInPath(civ,next,false,route.getToProvinceID())){
            country.removeRegroupArmy(index);
            return;
        }
        route.setNumOfUnits(troops);
        if(!CFG.gameAction.moveArmyAction(from,next,troops,civ,true,true))return;
        if(route.getRouteSize()==1){
            country.removeRegroupArmy(index);
        }else{
            route.setFromProvinceID(next);
            route.removeRoute(0);
            country.getCurrentRegroupArmyLine(index).remove(0);
        }
    }

    static void tick(){
        if(!enabled()||!MP.inputOrders()||!pending.isEmpty())return;
        long now=KreyliRealtimePause.clock();
        if(now<nextCheck)return;
        nextCheck=now+50_000_000L;
        boolean changed=false;
        var live=new HashSet<MoveUnits>();
        for(int civ=1;civ<CFG.core.getCivsSize();civ++){
            var country=CFG.core.getCiv(civ);
            for(int i=0;i<country.moveUnitsSize();i++){
                var move=country.getMoveUnits(i);
                if(!eligible(civ,move)){
                    if(starts.containsKey(move)){
                        country.removeMove(i--);
                        cancel(civ,move);
                        changed=true;
                    }
                    continue;
                }
                Long original=starts.putIfAbsent(move,now);
                if(original==null)KreyliOrderSync.mark();
                long start=original==null?now:original;
                if(now-start<DURATION_NANOS){
                    live.add(move);
                    continue;
                }
                var to=CFG.core.getProv(move.getToProvID());
                if(to.getCivId()!=civ){
                    boolean ai=!playerCountry(civ);
                    if(ai&&now<nextAiAttack.getOrDefault(civ,Long.MIN_VALUE)){
                        live.add(move);
                        continue;
                    }
                    int sent=move.getNumberOfUnits(),stationed=to.getArmyCivID1(civ);
                    if(KreyliRealtimeCombat.resolve(civ,i)){
                        if(ai)nextAiAttack.put(civ,now+AI_ATTACK_SPACING_NANOS);
                        int arrived=Math.min(sent,to.getArmyCivID1(civ)-stationed);
                        continueRoute(country,move.getToProvID(),sent,arrived);
                        i--;
                        changed=true;
                    }else live.add(move);
                    continue;
                }
                int destination=to.getArmyCivID1(civ);
                int amount=move.getNumberOfUnits();
                if(destination>Integer.MAX_VALUE-amount){
                    live.add(move);
                    continue;
                }
                // The native order already took these troops from the origin.
                country.removeMove(i--);
                to.updateArmy4(civ,destination+amount);
                to.updateDrawArmyInProv();
                continueRoute(country,move.getToProvID(),amount,amount);
                changed=true;
            }
        }
        starts.keySet().retainAll(live);
        if(changed)refresh();
    }

    static void beforeTurn(){
        if(enabled()&&pending.isEmpty())detachTimedOrders();
    }

    private static void detachTimedOrders(){
        long now=KreyliRealtimePause.clock();
        for(int civ=1;civ<CFG.core.getCivsSize();civ++){
            var country=CFG.core.getCiv(civ);
            for(int i=0;i<country.moveUnitsSize();i++){
                var move=country.getMoveUnits(i);
                if(!eligible(civ,move)){
                    if(starts.containsKey(move)){
                        country.removeMove(i--);
                        cancel(civ,move);
                    }
                    continue;
                }
                pending.add(new Pending(civ,move,starts.getOrDefault(move,now)));
                country.removeMove(i--);
            }
        }
        heldForDraw=List.copyOf(pending);
    }

    static void afterTurn(){
        if(pending.isEmpty())return;
        starts.clear();
        for(var march:pending){
            int civ=march.civ();
            var move=march.move();
            if(!eligible(civ,move)){
                cancel(civ,move);
                continue;
            }
            var country=CFG.core.getCiv(civ);
            country.newMove(move.getFromProviID(),move.getToProvID(),move.getNumberOfUnits(),true);
            var restored=country.getMoveUnits(country.moveUnitsSize()-1);
            starts.put(restored,march.start());
            drawProgress(restored,elapsed(restored));
        }
        pending.clear();
        heldForDraw=List.of();
        refresh();
    }

    private static void cancel(int civ,MoveUnits move){
        var country=CFG.core.getCiv(civ);
        int route=followingRoute(country,move.getToProvID(),move.getNumberOfUnits());
        if(route>=0)country.removeRegroupArmy(route);
        var origin=CFG.core.getProv(move.getFromProviID());
        var retreat=origin.getCivId()==civ?origin:null;
        for(int i=0;retreat==null&&i<origin.getNeighProvincesSize();i++){
            var adjacent=CFG.core.getProv(origin.getNeighProvinces(i));
            if(!adjacent.getSeaProv()&&adjacent.getCivId()==civ)retreat=adjacent;
        }
        int troops=move.getNumberOfUnits();
        if(retreat!=null){
            int stationed=retreat.getArmyCivID1(civ);
            int received=Math.min(troops,Integer.MAX_VALUE-stationed);
            retreat.updateArmy4(civ,stationed+received);
            retreat.updateDrawArmyInProv();
            troops-=received;
        }
        if(troops>0){
            country.setNumberOfUnits(Math.max(0,country.getNumberOfUnits()-troops));
        }
        KreyliOrderSync.worldChanged();
    }

    static int elapsed(MoveUnits move){
        Long start=starts.get(move);
        return start==null?-1:(int)Math.clamp((KreyliRealtimePause.clock()-start)/1_000_000L,0,DURATION_MILLIS);
    }

    static void drawProgress(MoveUnits move,int elapsed){
        var line=move.getMoveUnits_Line();
        if(elapsed<0||line==null)return;
        float progress=.1f+.9f*elapsed/DURATION_MILLIS;
        line.fMovingPercentage=Math.max(line.fMovingPercentage,progress);
        line.lMovingTime=System.currentTimeMillis();
    }

    static String storedRoutes(){
        if(!enabled())return "";
        var routes=new StringBuilder().append(GameCalendar.TURNID).append('\n');
        for(int civ=1;civ<CFG.core.getCivsSize();civ++){
            var country=CFG.core.getCiv(civ);
            for(int i=0;i<country.moveUnitsSize();i++){
                var move=country.getMoveUnits(i);
                if(eligible(civ,move)||starts.containsKey(move))
                    appendRoute(routes,civ,move,Math.max(0,elapsed(move)));
            }
        }
        long now=KreyliRealtimePause.clock();
        for(var march:pending)
            appendRoute(routes,march.civ(),march.move(),
                    (int)Math.clamp((now-march.start())/1_000_000L,0,DURATION_MILLIS));
        return routes.toString();
    }

    private static void appendRoute(StringBuilder routes,int civ,MoveUnits move,int age){
        routes.append(civ).append(',').append(move.getFromProviID()).append(',').append(move.getToProvID())
                .append(',').append(move.getNumberOfUnits()).append(',').append(age).append('\n');
    }

    static void restoreStoredRoutes(String routes){
        if(routes.isEmpty()||routes.length()>1_000_000)return;
        String[] rows=routes.split("\n");
        if(rows.length<2||!rows[0].equals(Integer.toString(GameCalendar.TURNID)))return;
        long now=KreyliRealtimePause.clock();
        for(int row=1;row<rows.length;row++){
            String[] columns=rows[row].split(",");
            if(columns.length!=5)continue;
            try{
                int civ=Integer.parseInt(columns[0]),from=Integer.parseInt(columns[1]),to=Integer.parseInt(columns[2]);
                int amount=Integer.parseInt(columns[3]),age=Integer.parseInt(columns[4]);
                if(civ<=0||!MP.validCiv(civ)||from<0||to<0||from>=CFG.core.getProvinSize()||to>=CFG.core.getProvinSize()
                        ||from==to||amount<=0||age<0||age>DURATION_MILLIS)continue;
                var country=CFG.core.getCiv(civ);
                MoveUnits move=null;
                for(int i=0;i<country.moveUnitsSize();i++){
                    var queued=country.getMoveUnits(i);
                    if(queued.getFromProviID()==from&&queued.getToProvID()==to){move=queued;break;}
                }
                if(move==null){
                    country.newMove(from,to,amount,true);
                    move=country.getMoveUnits(country.moveUnitsSize()-1);
                }
                if(move.getNumberOfUnits()!=amount||!KreyliLobby.realTime())continue;
                starts.putIfAbsent(move,now-age*1_000_000L);
                drawProgress(move,elapsed(move));
            }catch(NumberFormatException ignored){}
        }
    }

    static String clock(){
        var clock=new StringBuilder();
        for(int civ=1;civ<CFG.core.getCivsSize();civ++){
            var country=CFG.core.getCiv(civ);
            for(int i=0;i<country.moveUnitsSize();i++){
                int elapsed=elapsed(country.getMoveUnits(i));
                if(elapsed>=0)clock.append(civ).append(',').append(i).append(',').append(elapsed).append('\n');
            }
        }
        return clock.toString();
    }

    static void applyClock(String clock){
        if(clock.length()>1_000_000)return;
        for(String row:clock.split("\n")){
            if(row.isEmpty())continue;
            String[] fields=row.split(",");
            if(fields.length!=3)continue;
            try{
                int civ=Integer.parseInt(fields[0]);
                int index=Integer.parseInt(fields[1]);
                int age=Integer.parseInt(fields[2]);
                if(civ<=0||!MP.validCiv(civ)||index<0||age<0||age>DURATION_MILLIS)continue;
                var country=CFG.core.getCiv(civ);
                if(index<country.moveUnitsSize())drawProgress(country.getMoveUnits(index),age);
            }catch(NumberFormatException ignored){}
        }
    }

    public static boolean drawArmies(SpriteBatch batch,float scale){
        if(!KreyliLobby.realTime()||!MP.playing()||CFG.core.getPlayersSize()==0)return false;
        var player=CFG.core.getPlayer(0);
        int viewer=MP.isHost()?player.getCivId():MP.myCiv;
        if(viewer<=0&&!CFG.SPECTATOR_MODE)return false;
        float previous=batch.getPackedColor();
        try{
            if(MP.isHost()&&!MP.inputOrders()){
                for(var march:heldForDraw)drawArmy(batch,scale,viewer,march.civ(),march.move(),
                        (float)(KreyliRealtimePause.clock()-march.start())/DURATION_NANOS);
                return false;
            }
            for(int civ=1;civ<CFG.core.getCivsSize();civ++){
                var country=CFG.core.getCiv(civ);
                for(int i=0;i<country.moveUnitsSize();i++){
                    var move=country.getMoveUnits(i);
                    var line=move.getMoveUnits_Line();
                    if(line==null)continue;
                    int age=MP.isHost()?elapsed(move):-1;
                    if(age<0){
                        line.updateMovingLine();
                        line.fMovingPercentage=Math.min(1f,line.fMovingPercentage);
                        line.lMovingTime=System.currentTimeMillis();
                    }
                    float progress=age>=0?(float)age/DURATION_MILLIS:(line.getMovingPercentage()-.1f)/.9f;
                    drawArmy(batch,scale,viewer,civ,move,progress);
                }
            }
            return true;
        }finally{batch.setPackedColor(previous);}
    }

    private static void drawArmy(SpriteBatch batch,float scale,int viewer,int civ,MoveUnits move,float progress){
        progress=Math.clamp(progress,0f,1f);
        var from=CFG.core.getProv(move.getFromProviID());
        var to=CFG.core.getProv(move.getToProvID());
        if(!from.getDrawProv()&&!to.getDrawProv())return;
        if(viewer!=civ&&!CFG.SPECTATOR_MODE&&CFG.FOG_OF_WAR!=0){
            int position=progress<.5f?move.getFromProviID():move.getToProvID();
            if(!CFG.core.getPlayer(0).getFog(position))return;
        }
        int x=(int)((armyX(from)+(armyX(to)-armyX(from))*progress)*scale);
        int fromY=from.getCeY()+from.getShPY(),toY=to.getCeY()+to.getShPY();
        int y=(int)((fromY+(toY-fromY)*progress+CFG.map.getMpC().getPY())*scale);
        if(civ!=to.getCivId()&&to.getCivId()>0&&CFG.core.getCivsAtWar(civ,to.getCivId()))
            CFG.core.drawProvinceArmyWithFlag_Attack(batch,x,y,civ,move.getNumberOfUnits(),move.getUnitsWidth());
        else CFG.core.drawProvArmyWithFlag(batch,x,y,civ,move.getNumberOfUnits(),move.getUnitsWidth());
    }

    private static int armyX(Province province){
        return province.getCeX()+province.getShPX()+province.getTranslateProvPosX();
    }

    private static void refresh(){
        CFG.core.buildMoveUnits_JustDraw_AnotherArmies();
        CFG.core.updateDrawMoveUnitsArmy();
        CFG.setRenderO(true);
        KreyliOrderSync.mark();
    }

    static void reset(){
        nextAiAttack.clear();
        starts.clear();
        pending.clear();
        heldForDraw=List.of();
        nextCheck=0;
    }
}