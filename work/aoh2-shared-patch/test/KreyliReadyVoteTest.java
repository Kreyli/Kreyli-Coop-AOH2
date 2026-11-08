package age.of.civilizations2.jakowski.lukasz.mp;
public final class KreyliReadyVoteTest {
 public static void main(String[] args){
  check(KreyliReadyVote.valid(4,4,10,10,1,2));
  check(!KreyliReadyVote.valid(4,3,10,10,1,2));
  check(!KreyliReadyVote.valid(4,5,10,10,1,2));
  check(!KreyliReadyVote.valid(4,4,10,9,1,2));
  check(!KreyliReadyVote.valid(4,4,10,10,2,2));
  check(!KreyliReadyVote.valid(4,4,10,10,2,1));
  check(KreyliReadyVote.valid(4,4,10,11,1,2));
  check(!KreyliReadyVote.valid(4,4,0,-1,1,2));
  System.out.println("KREYLI_READY_VOTE: PASS 8 turn/command/sequence checks");
 }
 static void check(boolean value){if(!value)throw new AssertionError();}
}
