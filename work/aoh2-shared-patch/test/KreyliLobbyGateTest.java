package age.of.civilizations2.jakowski.lukasz.mp;

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
        require(gate.member(guest)&&!gate.hostReady&&!gate.released,"New member resets readiness");
        gate.hostReady=true;
        require(!gate.updateCountdown(now)&&!gate.released,"One of two cannot start");
        gate.guestReady=true;
        require(gate.updateCountdown(now)&&gate.seconds==5,"Both ready start countdown");
        gate.guestReady=false;
        require(gate.updateCountdown(now+second)&&gate.seconds==0,"Unready cancels countdown");
        gate.guestReady=true;
        gate.updateCountdown(now+second);
        require(!gate.updateCountdown(now+second)&&!gate.released,"Full countdown restarts");
        require(gate.updateCountdown(now+6*second)&&gate.released,"Two players start");
        require(gate.member(null)&&!gate.released&&!gate.hostReady&&gate.seconds==0,"Disconnect resets countdown");
        System.out.println("KREYLI_LOBBY_GATE: PASS solo, two players, cancellation and reset");
    }
}
