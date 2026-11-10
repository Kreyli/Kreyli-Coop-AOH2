package age.of.civilizations2.jakowski.lukasz.mp;

public final class KreyliQuickQueueTest {
    private static void check(boolean ok){if(!ok)throw new AssertionError();}

    public static void main(String[] args){
        var queue=new KreyliQuickQueue();
        queue.join("one",0);queue.join("two",0);
        check(queue.view(100_000).phase()==KreyliQuickQueue.Phase.SEARCHING);
        queue.join("three",100_000);
        check(queue.view(129_999).phase()==KreyliQuickQueue.Phase.SEARCHING);
        var offer=queue.view(130_000);
        check(offer.players().size()==3&&offer.deadline()==190_000);
        check(queue.accept(offer.id(),"one",131_000));
        check(!queue.accept(offer.id(),"one",131_000));
        check(!queue.accept(offer.id(),"outsider",131_000));
        check(queue.accept(offer.id(),"two",131_000));
        check(queue.view(190_000).phase()==KreyliQuickQueue.Phase.CANCELLED);
        check(!queue.accept(offer.id(),"three",190_000));
        queue.clearResult(offer.id(),190_000);
        for(int i=0;i<11;i++)queue.join("player"+i,191_000);
        offer=queue.view(221_000);
        check(offer.players().size()==10&&!offer.players().contains("player10"));
        for(String player:offer.players())check(queue.accept(offer.id(),player,221_000));
        check(queue.view(221_000).phase()==KreyliQuickQueue.Phase.STARTED);
        queue.clearResult(offer.id(),221_000);
        check(queue.view(300_000).phase()==KreyliQuickQueue.Phase.SEARCHING);
        var first=new KreyliQuickRules.Standing("first",1,true,-1,10,1);
        var second=new KreyliQuickRules.Standing("second",2,true,-1,8,0);
        var eliminated=new KreyliQuickRules.Standing("eliminated",1,false,55,7,0);
        var standings=java.util.List.of(second,eliminated,first);
        check(!KreyliQuickRules.shouldFinish(standings,3_599_999));
        check(KreyliQuickRules.shouldFinish(standings,3_600_000));
        check(KreyliQuickRules.shouldFinish(java.util.List.of(first,eliminated),1));
        check(KreyliQuickRules.results(standings).equals(java.util.List.of(first,second,eliminated)));
        check(!KreyliQuickRules.canConquerPlayer(80,16)&&KreyliQuickRules.canConquerPlayer(81,16));
        System.out.println("KREYLI_QUICK_QUEUE_AND_RULES: PASS");
    }
}
