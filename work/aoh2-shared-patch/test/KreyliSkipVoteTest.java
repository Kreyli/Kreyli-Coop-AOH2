package age.of.civilizations2.jakowski.lukasz.mp;
public final class KreyliSkipVoteTest {
 static int checks;static void check(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 public static void main(String[] args){
  Object a=new Object(),b=new Object();var v=new KreyliSkipVote();
  for(int n:new int[]{0,6,-1}){try{v.propose(1,10,n,a,true);throw new AssertionError("bad count");}catch(IllegalArgumentException expected){checks++;}}
  v.propose(1,10,5,a,true);check(!v.mayAdvance(10,a,true));check(!v.accept(2,10,a,false));check(!v.accept(1,11,a,false));check(!v.accept(1,10,b,false));
  check(v.accept(1,10,a,false));check(!v.mayAdvance(10,a,false));check(!v.mayAdvance(10,b,true));check(!v.mayAdvance(11,a,true));
  for(int turn=10;turn<15;turn++){check(v.mayAdvance(turn,a,true));v.started();check(!v.mayAdvance(turn,a,true));v.completed(turn+1);}
  check(v.id==0&&!v.mayAdvance(15,a,true));
  v.propose(2,20,2,a,false);check(!v.mayAdvance(20,a,true));v.accept(2,20,a,true);v.started();v.completed(22);check(v.id==0);
  v.propose(3,30,3,a,true);v.clear();check(!v.accept(3,30,a,false));check(!v.mayAdvance(30,a,true));
  System.out.println("KREYLI_SKIP_VOTE: PASS checks="+checks);
 }
}
