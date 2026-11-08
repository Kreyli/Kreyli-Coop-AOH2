package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.*;
import age.of.civilizations2.jakowski.lukasz.MoveUnitsB.*;
import java.util.*;
import java.lang.reflect.*;

/** Captures transient orders that native save DTOs omit, preserving their order. */
public final class SharedState {
    private static void field(Object target,String name,Object value){try{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}catch(Exception e){throw new IllegalStateException(e);}}
    public static String capture(){
        StringBuilder b=new StringBuilder("1,"+GameCalendar.TURNID+","+CFG.core.getCivsSize()+","+CFG.core.getProvinSize()+"\n");
        for(int p=0;p<CFG.core.getProvinSize();p++){var v=CFG.core.getProv(p);for(int i=0;i<v.getCivsSize();i++)if(v.getArmyID(i)>0)b.append("a,").append(v.getCivId(i)).append(',').append(p).append(',').append(v.getArmyID(i)).append('\n');}
        for(int c=1;c<CFG.core.getCivsSize();c++){
            var v=CFG.core.getCiv(c);b.append("p,").append(c).append(',').append(v.getMovemPoints()).append('\n');
            for(int i=0;i<v.getRecruitArmySize();i++){var q=v.getRecruitArmy(i);b.append("r,").append(c).append(',').append(q.getProvinceID()).append(',').append(q.getArmy()).append('\n');}
            for(int i=0;i<v.moveUnitsSize();i++){var q=v.getMoveUnits(i);b.append("m,").append(c).append(',').append(q.getFromProviID()).append(',').append(q.getToProvID()).append(',').append(q.getNumberOfUnits()).append('\n');}
            for(int i=0;i<v.getMigrateSize();i++){var q=v.getMigrateMU(i);b.append("g,").append(c).append(',').append(q.getFromProviID()).append(',').append(q.getToProvID()).append(',').append(q.getNumberOfUnits()).append('\n');}
            for(int i=0;i<v.getMoveUnitsPlunderSize();i++){var q=v.getMoveUnitsPlunder(i);b.append("l,").append(c).append(',').append(q.getFromProvinceID()).append(',').append(q.getNumOfUnits()).append('\n');}
        }
        return b.toString();
    }
    public static void restore(String state){
        if(state.length()>16_000_000)throw new IllegalArgumentException("Oversize state");
        String[] lines=state.split("\n");int civs=CFG.core.getCivsSize(),provinces=CFG.core.getProvinSize();
        if(lines.length>200001||!lines[0].equals("1,"+GameCalendar.TURNID+","+civs+","+provinces))throw new IllegalArgumentException("Wrong state identity");
        List<String[]> rows=new ArrayList<>();Set<Integer> points=new HashSet<>();Set<String> armies=new HashSet<>();
        for(int i=1;i<lines.length;i++){
            var s=lines[i].split(",");if(s.length<3)throw new IllegalArgumentException("Short row");
            int c=Integer.parseInt(s[1]);if(c<0||c>=civs||(!s[0].equals("a")&&c==0))throw new IllegalArgumentException("Country bounds");
            if(s[0].equals("p")){if(s.length!=3||!points.add(c))throw new IllegalArgumentException("Points row");Integer.parseInt(s[2]);}
            else{
                int length=s[0].equals("m")||s[0].equals("g")?5:4;
                if(!Set.of("a","r","m","g","l").contains(s[0])||s.length!=length)throw new IllegalArgumentException("Invalid order");
                int from=Integer.parseInt(s[2]);if(from<0||from>=provinces||Integer.parseInt(s[length-1])<=0)throw new IllegalArgumentException("Order bounds");
                if(length==5){int to=Integer.parseInt(s[3]);if(to<0||to>=provinces||to==from)throw new IllegalArgumentException("Destination bounds");}
                if(s[0].equals("a")&&!armies.add(c+":"+from))throw new IllegalArgumentException("Duplicate army");
            }
            rows.add(s);
        }
        if(points.size()!=civs-1)throw new IllegalArgumentException("Missing movement points");
        Map<Integer,List<int[]>> newMoves=new HashMap<>();
        Map<Integer,List<int[]>> newMigrate=new HashMap<>();
        Map<Integer,List<int[]>> newRecruit=new HashMap<>();
        Map<Integer,Integer> newPoints=new HashMap<>();
        Map<Integer,Map<Integer,Integer>> newArmies=new HashMap<>();
        for(var s:rows){
            int c=Integer.parseInt(s[1]),p=Integer.parseInt(s[2]);
            switch(s[0]){
                case "p"->newPoints.put(c,p);
                case "a"->newArmies.computeIfAbsent(c,k->new HashMap<>()).put(p,Integer.parseInt(s[3]));
                case "r"->newRecruit.computeIfAbsent(c,k->new ArrayList<>()).add(new int[]{p,Integer.parseInt(s[3])});
                case "m"->newMoves.computeIfAbsent(c,k->new ArrayList<>()).add(new int[]{p,Integer.parseInt(s[3]),Integer.parseInt(s[4])});
                case "g"->newMigrate.computeIfAbsent(c,k->new ArrayList<>()).add(new int[]{p,Integer.parseInt(s[3]),Integer.parseInt(s[4])});
            }
        }
        CFG.core.clearMoveUnits_JustDraw_AnotherArmies();
        for(int c=1;c<civs;c++){
            var v=CFG.core.getCiv(c);
            if(newPoints.containsKey(c))v.setMovementPoints(newPoints.get(c));
            List<MoveUnits> existingMoves=new ArrayList<>();
            for(int i=0;i<v.moveUnitsSize();i++)existingMoves.add(v.getMoveUnits(i));
            List<int[]> desiredMoves=newMoves.getOrDefault(c,List.of());
            boolean movesMatch=existingMoves.size()==desiredMoves.size();
            if(movesMatch){
                for(int i=0;i<existingMoves.size();i++){
                    var em=existingMoves.get(i);var dm=desiredMoves.get(i);
                    if(em.getFromProviID()!=dm[0]||em.getToProvID()!=dm[1]||em.getNumberOfUnits()!=dm[2]){movesMatch=false;break;}
                }
            }
            if(!movesMatch){
                v.clearMoveUnits();
                for(var dm:desiredMoves)v.newMove(dm[0],dm[1],dm[2],true);
            }
            v.clearRecruitArmy();
            for(var r:newRecruit.getOrDefault(c,List.of())){
                v.recruitArmy.add(new RecruitArmy(r[0],r[1]));
            }
            v.recruitArmySize=v.recruitArmy.size();
            v.clearMoveUnits_Plunder();
            for(var s:rows){
                if(s[0].equals("l")&&Integer.parseInt(s[1])==c)v.newPlunder(Integer.parseInt(s[2]),Integer.parseInt(s[3]));
            }
            v.lMigrate.clear();
            for(var g:newMigrate.getOrDefault(c,List.of()))v.lMigrate.add(new MoveUnits(g[0],g[1],g[2],true,true));
            field(v,"iMigrateSize",v.lMigrate.size());
        }
        for(int p=0;p<provinces;p++){
            var v=CFG.core.getProv(p);
            for(int i=v.getCivsSize()-1;i>=0;i--){
                int civId=v.getCivId(i);
                Integer amount=newArmies.getOrDefault(civId,Map.of()).get(p);
                if(amount!=null)v.updateArmy4(civId,amount);else v.updateArmy4(civId,0);
            }
            for(var entry:newArmies.entrySet()){
                int civId=entry.getKey();Integer amount=entry.getValue().get(p);
                if(amount!=null&&amount>0)v.updateArmy4(civId,amount);
            }
        }
        NewGameManager.build_ArmyInAnotherProvince();
        CFG.core.buildMoveUnits_JustDraw_AnotherArmies();
        CFG.core.updateDrawMoveUnitsArmy();
        for(int p=0;p<provinces;p++)CFG.core.getProv(p).updateDrawArmyInProv();
    }
}
