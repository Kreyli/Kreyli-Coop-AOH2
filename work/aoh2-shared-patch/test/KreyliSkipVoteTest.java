package age.of.civilizations2.jakowski.lukasz.mp;

import java.util.List;
import java.util.stream.IntStream;

public final class KreyliSkipVoteTest {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        var players=IntStream.range(0,10).mapToObj(i->new Object()).toList();
        var proposer=players.get(0);
        var vote=new KreyliSkipVote();
        for(int invalid:new int[]{0,21,-1})require(!vote.propose(1,10,invalid,players,proposer,0),"Invalid skip count");
        require(vote.propose(1,10,20,players,proposer,0),"Proposal with 20 turns");
        require(vote.yes()==1&&vote.threshold()==6,"Proposer counts once and ties cannot win");
        require(!vote.vote(1,10,proposer,true,0),"Duplicate vote blocked");
        require(!vote.vote(2,10,players.get(1),true,0),"Wrong proposal blocked");
        require(!vote.vote(1,11,players.get(1),true,0),"Wrong turn blocked");
        require(!vote.vote(1,10,new Object(),true,0),"Outside roster blocked");
        for(int i=1;i<5;i++)require(vote.vote(1,10,players.get(i),true,0),"Count each vote");
        require(!vote.mayAdvance(10),"Five votes are not a majority of ten");
        require(vote.vote(1,10,players.get(5),true,0),"Sixth vote");
        require(!vote.sameRoster(players.subList(0,9)),"Departure is detected");
        for(int turn=10;turn<30;turn++){
            require(vote.mayAdvance(turn),"Approved turn");
            vote.started();
            require(!vote.mayAdvance(turn),"No duplicate advancement");
            vote.completed(turn+1);
        }
        require(vote.id==0,"Exactly twenty turns");
        require(vote.propose(2,30,3,players,proposer,0),"Timeout proposal");
        require(!vote.failed(KreyliSkipVote.DURATION-1),"Still open");
        require(!vote.vote(2,30,players.get(1),true,KreyliSkipVote.DURATION),"Deadline enforced on host");
        require(vote.failed(KreyliSkipVote.DURATION),"Proposal expired");
        vote.clear();
        require(vote.propose(3,30,1,List.of(proposer,players.get(1)),proposer,0),"Two-player proposal");
        require(vote.vote(3,30,players.get(1),false,0)&&vote.failed(0),"Rejection makes majority impossible");
        System.out.println("KREYLI_SKIP_VOTE: PASS roster, majority, expiry and 20 turns");
    }
}
