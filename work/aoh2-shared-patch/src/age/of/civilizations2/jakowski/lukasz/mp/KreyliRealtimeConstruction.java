package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.CFG;
import age.of.civilizations2.jakowski.lukasz.Civilization;
import age.of.civilizations2.jakowski.lukasz.Civilizations.Construction.BuildingsConstruction;
import java.util.ArrayList;
import java.util.IdentityHashMap;

/** Each project advances on the host clock while retaining native completion and ownership rules. */
public final class KreyliRealtimeConstruction {
    private static final long INTERVAL=5_000_000_000L;
    private static final IdentityHashMap<BuildingsConstruction,Long> deadlines=new IdentityHashMap<>();
    private static long nextCheck;

    private KreyliRealtimeConstruction(){}

    private static boolean enabled(){
        return MP.isHost()&&MP.started&&KreyliLobby.realTime();
    }

    public static void resolveNative(Civilization country){
        if(!enabled())country.runConstructions();
    }

    static void tick(){
        if(!enabled()||!MP.inputOrders())return;
        long now=System.nanoTime();
        if(now<nextCheck)return;
        nextCheck=now+100_000_000L;
        var current=new IdentityHashMap<BuildingsConstruction,Long>();
        boolean updated=false;
        for(int id=1;id<CFG.core.getCivsSize();id++){
            var country=CFG.core.getCiv(id);
            var jobs=country.civGD.lConstructions;
            if(jobs.isEmpty())continue;
            var due=new ArrayList<BuildingsConstruction>();
            for(var job:jobs){
                long deadline=deadlines.getOrDefault(job,now+INTERVAL);
                if(now>=deadline)due.add(job);
                else current.put(job,deadline);
            }
            if(due.isEmpty())continue;
            jobs.removeAll(due);
            country.civGD.lConstructions=due;
            try{
                country.runConstructions();
            }finally{
                jobs.addAll(due);
                country.civGD.lConstructions=jobs;
            }
            for(var job:due)current.put(job,now+INTERVAL);
            updated=true;
        }
        deadlines.clear();
        deadlines.putAll(current);
        if(updated){
            CFG.setRenderO(true);
            KreyliOrderSync.worldChanged();
        }
    }

    static void reset(){deadlines.clear();nextCheck=0;}
}