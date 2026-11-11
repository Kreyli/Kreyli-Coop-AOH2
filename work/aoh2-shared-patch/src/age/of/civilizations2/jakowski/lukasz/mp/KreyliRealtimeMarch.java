package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.CFG;
import age.of.civilizations2.jakowski.lukasz.GameCalendar;
import age.of.civilizations2.jakowski.lukasz.MoveUnitsB.MoveUnits;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;

/** Runs player marches between adjacent friendly land provinces on the host clock. */
final class KreyliRealtimeMarch {
    static final int DURATION_MILLIS=5_000;
    private static final long DURATION_NANOS=DURATION_MILLIS*1_000_000L;
    private static final IdentityHashMap<MoveUnits,Long> starts=new IdentityHashMap<>();
    private static final HashSet<MoveUnits> nativeFallback=new HashSet<>();
    private record Pending(int civ,MoveUnits move,long start) {}
    private static final List<Pending> pending=new ArrayList<>();
    private static long nextCheck;

    private KreyliRealtimeMarch(){}

    private static boolean enabled(){
        return MP.isHost()&&MP.started&&KreyliLobby.realTime();
    }

    private static HashSet<Integer> countries(){
        var countries=new HashSet<Integer>();
        if(CFG.core.getPlayersSize()>0)countries.add(CFG.core.getPlayer(0).getCivId());
        for(var guest:MP.clients)countries.add(guest.civ);
        countries.removeIf(civ->civ<=0||!MP.validCiv(civ));
        return countries;
    }

    private static boolean eligible(int civ,MoveUnits move){
        if(move.getNumberOfUnits()<=0||move.getFromProviID()==move.getToProvID())return false;
        var from=CFG.core.getProv(move.getFromProviID());
        var to=CFG.core.getProv(move.getToProvID());
        if(from.getSeaProv()||to.getSeaProv()||from.getCivId()!=civ||to.getCivId()!=civ)return false;
        for(int i=0;i<from.getNeighProvincesSize();i++){
            if(from.getNeighProvinces(i)==move.getToProvID())return true;
        }
        return false;
    }

    static void tick(){
        if(!enabled()||!MP.inputOrders()||!pending.isEmpty())return;
        long now=System.nanoTime();
        if(now<nextCheck)return;
        nextCheck=now+50_000_000L;
        boolean changed=false;
        var live=new HashSet<MoveUnits>();
        var fallback=new HashSet<MoveUnits>();
        for(int civ:countries()){
            var country=CFG.core.getCiv(civ);
            for(int i=0;i<country.moveUnitsSize();i++){
                var move=country.getMoveUnits(i);
                if(!eligible(civ,move)){
                    if(starts.containsKey(move)||nativeFallback.contains(move))fallback.add(move);
                    continue;
                }
                Long original=starts.putIfAbsent(move,now);
                nativeFallback.remove(move);
                if(original==null)KreyliOrderSync.mark();
                long start=original==null?now:original;
                if(now-start<DURATION_NANOS){
                    live.add(move);
                    continue;
                }
                var to=CFG.core.getProv(move.getToProvID());
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
                changed=true;
            }
        }
        starts.keySet().retainAll(live);
        nativeFallback.clear();
        nativeFallback.addAll(fallback);
        if(changed)refresh();
    }

    static void beforeTurn(){
        if(!enabled()||!pending.isEmpty())return;
        long now=System.nanoTime();
        for(int civ:countries()){
            var country=CFG.core.getCiv(civ);
            for(int i=0;i<country.moveUnitsSize();i++){
                var move=country.getMoveUnits(i);
                if(!eligible(civ,move))continue;
                pending.add(new Pending(civ,move,starts.getOrDefault(move,now)));
                country.removeMove(i--);
            }
        }
    }

    static void afterTurn(){
        if(pending.isEmpty())return;
        starts.clear();
        for(var march:pending){
            int civ=march.civ();
            var move=march.move();
            var country=CFG.core.getCiv(civ);
            country.newMove(move.getFromProviID(),move.getToProvID(),move.getNumberOfUnits(),true);
            var restored=country.getMoveUnits(country.moveUnitsSize()-1);
            if(eligible(civ,restored)){
                starts.put(restored,march.start());
                drawProgress(restored,elapsed(restored));
            }else nativeFallback.add(restored);
        }
        pending.clear();
        refresh();
    }

    static int elapsed(MoveUnits move){
        Long start=starts.get(move);
        return start==null?-1:(int)Math.clamp((System.nanoTime()-start)/1_000_000L,0,DURATION_MILLIS);
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
        for(int civ:countries()){
            var country=CFG.core.getCiv(civ);
            for(int i=0;i<country.moveUnitsSize();i++){
                var move=country.getMoveUnits(i);
                if(eligible(civ,move)||nativeFallback.contains(move))
                    appendRoute(routes,civ,move,Math.max(0,elapsed(move)));
            }
        }
        long now=System.nanoTime();
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
        long now=System.nanoTime();
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
                if(move.getNumberOfUnits()!=amount||!KreyliLobby.realTime()||!eligible(civ,move))continue;
                starts.putIfAbsent(move,now-age*1_000_000L);
                drawProgress(move,elapsed(move));
            }catch(NumberFormatException ignored){}
        }
    }

    static String clock(){
        var clock=new StringBuilder();
        for(int civ:countries()){
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

    private static void refresh(){
        CFG.core.buildMoveUnits_JustDraw_AnotherArmies();
        CFG.core.updateDrawMoveUnitsArmy();
        CFG.setRenderO(true);
        KreyliOrderSync.mark();
    }

    static void reset(){
        starts.clear();
        pending.clear();
        nativeFallback.clear();
        nextCheck=0;
    }
}