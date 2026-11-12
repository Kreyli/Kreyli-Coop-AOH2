package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.CFG;
import java.util.HashMap;

/** Schedule wartime military decisions by country on the authoritative simulation clock. */
public final class KreyliRealtimeAi {
    private static final long INTERVAL=5_000_000_000L;
    private static final HashMap<Integer,Long> due=new HashMap<>();
    private static int cursor=1;

    private KreyliRealtimeAi(){}

    private static boolean enabled(){
        return MP.isHost()&&MP.started&&KreyliLobby.realTime();
    }

    private static boolean computerCountry(int civ){
        return civ>0&&MP.validCiv(civ)&&!CFG.core.getCiv(civ).getIsPlayer()
                &&CFG.core.getPlayer(0).getCivId()!=civ
                &&MP.clients.stream().noneMatch(client->client.civ==civ);
    }

    public static boolean skipNativePlanning(int civ){
        return enabled()&&!MP.inputOrders()&&computerCountry(civ);
    }

    static void tick(){
        if(!enabled()||!MP.inputOrders())return;
        long now=KreyliRealtimePause.clock();
        int count=CFG.core.getCivsSize();
        for(int checked=1;checked<count;checked++){
            if(cursor>=count)cursor=1;
            int id=cursor++;
            var country=CFG.core.getCiv(id);
            if(!computerCountry(id)||country.getNumOfProvs()==0||!country.isAtWarC()){
                due.remove(id);
                continue;
            }
            long next=due.computeIfAbsent(id,key->now+key%20*100_000_000L);
            if(now<next)continue;
            due.put(id,now+INTERVAL);
            var style=CFG.oAI.getAIStyle(country.getAIStyleID());
            if(style!=null)style.moveAtWar(id);
            return;
        }
    }

    static void reset(){due.clear();cursor=1;}
}