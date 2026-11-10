package age.of.civilizations2.jakowski.lukasz.mp;
import java.util.List;
import java.util.stream.IntStream;

public final class KreyliLobbyGateTest {
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        var gate=new KreyliLobby.Gate();
        long now=1_000_000_000L,second=1_000_000_000L;
        require(!gate.updateCountdown(now)&&gate.seconds==0,"Idle solo lobby");
        gate.hostReady=true;
        for(int i=0;i<5;i++){
            require(gate.updateCountdown(now+i*second)&&gate.seconds==5-i&&!gate.released,"Solo countdown "+i);
        }
        require(gate.updateCountdown(now+5*second)&&gate.released,"Solo starts once");
        require(!gate.updateCountdown(now+6*second),"No repeat start");
        var guest=new Object();
        require(gate.members(List.of(guest))&&!gate.hostReady&&!gate.released,"New member resets readiness");
        gate.hostReady=true;
        require(!gate.updateCountdown(now)&&!gate.released,"One of two cannot start");
        gate.ready.add(guest);
        require(gate.updateCountdown(now)&&gate.seconds==5,"Both ready start countdown");
        gate.ready.remove(guest);
        require(gate.updateCountdown(now+second)&&gate.seconds==0,"Unready cancels countdown");
        gate.ready.add(guest);
        gate.updateCountdown(now+second);
        require(!gate.updateCountdown(now+second)&&!gate.released,"Full countdown restarts");
        require(gate.updateCountdown(now+6*second)&&gate.released,"Two players start");
        require(gate.members(List.of())&&!gate.released&&!gate.hostReady&&gate.seconds==0,"Disconnect resets countdown");
        var guests=IntStream.range(0,9).mapToObj(i->new Object()).toList();
        gate.members(guests);gate.hostReady=true;
        gate.ready.addAll(guests.subList(0,8));
        require(!gate.updateCountdown(now),"9 of 10 does not start the lobby countdown");
        gate.ready.add(guests.getLast());
        require(gate.updateCountdown(now)&&gate.seconds==5,"All 10 ready start the countdown");
        require(gate.updateCountdown(now+5*second)&&gate.released,"Ten players start once");
        System.out.println("KREYLI_LOBBY_GATE: PASS solo, two and ten players, cancellation and reset");
    }
}
