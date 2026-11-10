package age.of.civilizations2.jakowski.lukasz.mp;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** In-memory matchmaking policy. The coordinator supplies authenticated player IDs and a monotonic clock. */
public final class KreyliQuickQueue {
    public static final int MIN_PLAYERS=3, MAX_PLAYERS=10;
    public static final long GATHER_MILLIS=30_000, ACCEPT_MILLIS=60_000;
    public enum Phase { SEARCHING, ACCEPTING, STARTED, CANCELLED }
    public record Offer(long id,List<String> players,Set<String> accepted,long deadline,Phase phase) {}

    private final Set<String> waiting=new LinkedHashSet<>();
    private final Set<String> accepted=new LinkedHashSet<>();
    private List<String> offered=List.of();
    private long deadline, id;
    private Phase phase=Phase.SEARCHING;

    public synchronized boolean join(String player,long now){
        advance(now);
        if(player==null||player.isBlank()||offered.contains(player)||!waiting.add(player))return false;
        schedule(now);
        return true;
    }

    public synchronized void leave(String player,long now){
        advance(now);
        waiting.remove(player);
        if(phase==Phase.ACCEPTING&&offered.contains(player))phase=Phase.CANCELLED;
        if(phase==Phase.SEARCHING&&waiting.size()<MIN_PLAYERS)deadline=0;
    }

    public synchronized boolean accept(long offer,String player,long now){
        advance(now);
        if(phase!=Phase.ACCEPTING||id!=offer||!offered.contains(player)||!accepted.add(player))return false;
        if(accepted.size()==offered.size())phase=Phase.STARTED;
        return true;
    }

    public synchronized Offer view(long now){
        advance(now);
        return new Offer(id,offered,Set.copyOf(accepted),deadline,phase);
    }

    public synchronized void clearResult(long offer,long now){
        if(offer!=id||(phase!=Phase.STARTED&&phase!=Phase.CANCELLED))return;
        phase=Phase.SEARCHING;
        offered=List.of();
        accepted.clear();
        deadline=0;
        schedule(now);
    }

    private void schedule(long now){
        if(phase==Phase.SEARCHING&&deadline==0&&waiting.size()>=MIN_PLAYERS)deadline=now+GATHER_MILLIS;
    }

    private void advance(long now){
        if(phase==Phase.SEARCHING&&deadline!=0&&now>=deadline){
            offered=waiting.stream().limit(MAX_PLAYERS).toList();
            waiting.removeAll(offered);
            id++;
            phase=Phase.ACCEPTING;
            deadline=now+ACCEPT_MILLIS;
        }else if(phase==Phase.ACCEPTING&&now>=deadline){
            phase=Phase.CANCELLED;
        }
    }
}
