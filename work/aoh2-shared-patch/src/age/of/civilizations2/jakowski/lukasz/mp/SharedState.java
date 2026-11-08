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
        CFG.core.clearMoveUnits_JustDraw_AnotherArmies();
        for(int c=1;c<civs;c++){var v=CFG.core.getCiv(c);v.clearMoveUnits();v.clearRecruitArmy();v.clearMoveUnits_Plunder();v.lMigrate.clear();field(v,"iMigrateSize",0);}
        for(int p=0;p<provinces;p++){var v=CFG.core.getProv(p);for(int i=v.getCivsSize()-1;i>=0;i--)v.updateArmy4(v.getCivId(i),0);}
        for(var s:rows){int c=Integer.parseInt(s[1]),p=Integer.parseInt(s[2]);var v=CFG.core.getCiv(c);
            switch(s[0]){
                case "p"->v.setMovementPoints(p);
                case "a"->CFG.core.getProv(p).updateArmy4(c,Integer.parseInt(s[3]));
                case "r"->{v.recruitArmy.add(new RecruitArmy(p,Integer.parseInt(s[3])));v.recruitArmySize=v.recruitArmy.size();}
                case "m"->v.newMove(p,Integer.parseInt(s[3]),Integer.parseInt(s[4]),true);
                case "g"->{v.lMigrate.add(new MoveUnits(p,Integer.parseInt(s[3]),Integer.parseInt(s[4]),true,true));field(v,"iMigrateSize",v.lMigrate.size());}
                case "l"->v.newPlunder(p,Integer.parseInt(s[3]));
            }
        }
        NewGameManager.build_ArmyInAnotherProvince();
        CFG.core.buildMoveUnits_JustDraw_AnotherArmies();
        CFG.core.updateDrawMoveUnitsArmy();
        for(int p=0;p<provinces;p++)CFG.core.getProv(p).updateDrawArmyInProv();
    }
}
