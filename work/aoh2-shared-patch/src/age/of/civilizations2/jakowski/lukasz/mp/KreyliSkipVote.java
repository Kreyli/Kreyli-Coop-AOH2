package age.of.civilizations2.jakowski.lukasz.mp;

/** Host-owned consent bound to a member, a proposal and an exact world turn. */
final class KreyliSkipVote {
    long id; int turn,count,remaining; Object guest; boolean hostYes,guestYes,waiting;
    void propose(long id,int turn,int count,Object guest,boolean host){
        if(count<1||count>5||guest==null)throw new IllegalArgumentException("Skip requires 1..5 turns and two players");
        this.id=id;this.turn=turn;this.count=count;this.guest=guest;remaining=0;waiting=false;hostYes=host;guestYes=!host;
    }
    boolean accept(long id,int turn,Object guest,boolean host){
        if(this.id==0||this.id!=id||this.turn!=turn||this.guest!=guest||remaining>0)return false;
        if(host)hostYes=true;else guestYes=true;
        if(hostYes&&guestYes)remaining=count;
        return true;
    }
    boolean mayAdvance(int worldTurn,Object guest,boolean acknowledged){return id!=0&&remaining>0&&!waiting&&turn==worldTurn&&this.guest==guest&&acknowledged;}
    void started(){if(remaining<=0||waiting)throw new IllegalStateException();waiting=true;}
    void completed(int worldTurn){
        if(!waiting||worldTurn!=turn+1){clear();return;}
        remaining--;turn=worldTurn;waiting=false;if(remaining==0)clear();
    }
    void clear(){id=0;count=remaining=0;guest=null;hostYes=guestYes=waiting=false;}
}
