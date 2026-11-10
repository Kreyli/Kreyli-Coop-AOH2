package age.of.civilizations2.jakowski.lukasz.mp;

import java.util.*;

/** A strict majority of the roster at proposal time, counted by connection identity. */
final class KreyliSkipVote {
    static final int MAX_TURNS=20;
    static final long DURATION=30_000_000_000L;
    private final Map<Object,Boolean> ballots=new IdentityHashMap<>();
    private final Set<Object> voters=Collections.newSetFromMap(new IdentityHashMap<>());
    long id, deadline;
    int turn,count,remaining;
    Object proposer;
    boolean waiting;

    boolean propose(long id,int turn,int count,List<?> players,Object proposer,long now){
        if(this.id!=0||count<1||count>MAX_TURNS||players.size()<2||!players.contains(proposer))return false;
        this.id=id;this.turn=turn;this.count=count;this.proposer=proposer;
        deadline=now+DURATION;
        voters.addAll(players);
        ballots.put(proposer,true);
        return true;
    }
    int yes(){return (int)ballots.values().stream().filter(Boolean.TRUE::equals).count();}
    int threshold(){return voters.size()/2+1;}
    boolean canVote(Object who){return id!=0&&remaining==0&&voters.contains(who)&&!ballots.containsKey(who);}
    boolean vote(long id,int turn,Object who,boolean yes,long now){
        if(this.id!=id||this.turn!=turn||now>=deadline||!canVote(who))return false;
        ballots.put(who,yes);
        if(yes()>=threshold())remaining=count;
        return true;
    }
    boolean failed(long now){return id!=0&&remaining==0&&(now>=deadline||yes()+voters.size()-ballots.size()<threshold());}
    boolean sameRoster(List<?> players){return voters.size()==players.size()&&voters.containsAll(players);}
    boolean mayAdvance(int worldTurn){return id!=0&&remaining>0&&!waiting&&turn==worldTurn;}
    void started(){waiting=true;}
    void completed(int worldTurn){
        if(!waiting||worldTurn!=turn+1){clear();return;}
        remaining--;turn=worldTurn;waiting=false;if(remaining==0)clear();
    }
    void clear(){id=deadline=0;count=remaining=0;proposer=null;waiting=false;ballots.clear();voters.clear();}
}
