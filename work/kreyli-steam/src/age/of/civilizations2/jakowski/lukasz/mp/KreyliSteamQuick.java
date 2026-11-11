package age.of.civilizations2.jakowski.lukasz.mp;

import com.codedisaster.steamworks.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/** Steam lobby rendezvous for the Quick Match waiting and acceptance screens. */
final class KreyliSteamQuick {
    private static final String TAG="kreyli_quick", PHASE="quick_phase", DEADLINE="quick_deadline",
            ROSTER="quick_roster", READY="quick_ready", ROOM="quick_room";
    private final SteamMatchmaking steam;
    private final SteamUtils utils;
    private final SteamID self;
    private SteamID lobby,pendingJoin;
    private IntFunction<String> host;
    private Consumer<String> join;
    private boolean searching,listing,creating,launching,accepted;
    private long started,nextList,releaseAt;
    private String error="";

    KreyliSteamQuick(SteamMatchmaking steam,SteamUtils utils,SteamID self){
        this.steam=steam;this.utils=utils;this.self=self;
    }

    boolean start(IntFunction<String> host,Consumer<String> join){
        if(searching||listing||creating||pendingJoin!=null||lobby!=null)return false;
        this.host=host;this.join=join;
        searching=true;accepted=false;started=now();nextList=0;error="";
        tick();
        return true;
    }

    void cancel(){
        if(searching&&owner()&&"accept".equals(data(PHASE)))steam.setLobbyData(lobby,PHASE,"cancelled");
        searching=false;
        closeLobby();
    }

    private void fail(String message){
        error=message;
        cancel();
    }

    private void closeLobby(){
        if(lobby!=null){steam.leaveLobby(lobby);lobby=null;}
        releaseAt=0;accepted=false;
    }

    void tick(){
        long now=now();
        if(!searching){
            if(releaseAt!=0&&(now>=releaseAt||steam.getNumLobbyMembers(lobby)==1))closeLobby();
            return;
        }
        if(lobby==null){
            if(!listing&&!creating&&pendingJoin==null&&now>=nextList)requestList(now);
            return;
        }
        String phase=data(PHASE);
        List<String> players=members();
        if("cancelled".equals(phase)){fail("cancelled");return;}
        if("merge".equals(phase)){
            try{
                SteamID target=SteamID.createFromNativeHandle(Long.parseUnsignedLong(data(ROOM)));
                if(target.isValid()&&compare(target,lobby)<0){moveTo(target);return;}
            }catch(NumberFormatException ignored){}
            fail("steam");
            return;
        }
        if("started".equals(phase)){
            if(owner())return;
            String route=data(ROOM),roster=data(ROSTER);
            if(!route.isEmpty()&&List.of(roster.split(",")).contains(id(self))){
                searching=false;
                closeLobby();
                join.accept(route+"\n"+roster.split(",").length);
            }
            return;
        }
        if(!owner())return;
        if("waiting".equals(phase)){
            long deadline=deadline();
            if(players.size()<3){
                if(deadline!=0)steam.deleteLobbyData(lobby,DEADLINE);
                if(!listing&&now>=nextList)requestList(now);
            }else if(deadline==0){
                steam.setLobbyData(lobby,DEADLINE,Long.toString(now+30));
            }else if(now>=deadline){
                steam.setLobbyJoinable(lobby,false);
                steam.setLobbyData(lobby,ROSTER,String.join(",",players));
                steam.setLobbyData(lobby,DEADLINE,Long.toString(now+60));
                steam.setLobbyData(lobby,PHASE,"accept");
            }
            return;
        }
        if("accept".equals(phase)){
            long deadline=deadline();
            if(deadline==0||now>=deadline||!String.join(",",players).equals(data(ROSTER))){
                fail("cancelled");
            }else if(!launching&&acceptedCount(deadline)==players.size()){
                launching=true;
                try{
                    String route=host.apply(players.size());
                    if(route==null){fail("relay");return;}
                    steam.setLobbyData(lobby,ROOM,route);
                    steam.setLobbyData(lobby,PHASE,"started");
                    searching=false;
                    releaseAt=now+60;
                }catch(RuntimeException failure){
                    System.err.println("Quick Match host: "+failure);
                    fail("relay");
                }finally{launching=false;}
            }
        }
    }

    void accept(){
        if(!searching||accepted||lobby==null||!"accept".equals(data(PHASE))||deadline()<=now())return;
        if(!List.of(data(ROSTER).split(",")).contains(id(self)))return;
        steam.setLobbyMemberData(lobby,READY,Long.toString(deadline()));
        accepted=true;
    }

    KreyliSteam.QuickStatus status(){
        long now=now(),deadline=deadline();
        String phase=!error.isEmpty()?"failed":!searching?"idle":lobby==null?"waiting":data(PHASE);
        int members=lobby==null?0:steam.getNumLobbyMembers(lobby);
        return new KreyliSteam.QuickStatus(phase,error,members,"accept".equals(phase)?acceptedCount(deadline):0,
                (int)Math.max(0,now-started),(int)Math.max(0,deadline-now),accepted);
    }

    private void requestList(long now){
        listing=true;nextList=now+5;
        steam.addRequestLobbyListStringFilter(TAG,KreyliSteam.PROTOCOL,SteamMatchmaking.LobbyComparison.Equal);
        steam.addRequestLobbyListStringFilter(PHASE,"waiting",SteamMatchmaking.LobbyComparison.Equal);
        steam.addRequestLobbyListFilterSlotsAvailable(1);
        steam.addRequestLobbyListDistanceFilter(SteamMatchmaking.LobbyDistanceFilter.Worldwide);
        steam.requestLobbyList();
    }

    boolean event(String event,Object[] args){
        switch(event){
            case "onLobbyMatchList" -> {
                if(!listing)return false;
                listing=false;
                if(!searching)return true;
                SteamID best=null;
                for(int i=0;i<(Integer)args[0];i++){
                    SteamID candidate=steam.getLobbyByIndex(i);
                    if(candidate!=null&&candidate.isValid()&&!candidate.equals(lobby)
                            &&(best==null||compare(candidate,best)<0))best=candidate;
                }
                if(best!=null&&(lobby==null||owner()&&steam.getNumLobbyMembers(lobby)<3&&compare(best,lobby)<0)){
                    if(lobby!=null){
                        steam.setLobbyJoinable(lobby,false);
                        steam.setLobbyData(lobby,ROOM,id(best));
                        steam.setLobbyData(lobby,PHASE,"merge");
                    }
                    moveTo(best);
                }else if(lobby==null){
                    creating=true;
                    steam.createLobby(SteamMatchmaking.LobbyType.Public,KreyliSteam.MAX_PLAYERS);
                }
                return true;
            }
            case "onLobbyCreated" -> {
                if(!creating)return false;
                creating=false;
                if(args[0]!=SteamResult.OK){if(searching)fail("steam");return true;}
                SteamID created=(SteamID)args[1];
                if(!searching){steam.leaveLobby(created);return true;}
                lobby=created;
                steam.setLobbyData(lobby,TAG,KreyliSteam.PROTOCOL);
                steam.setLobbyData(lobby,PHASE,"waiting");
                return true;
            }
            case "onLobbyEnter" -> {
                if(pendingJoin==null||!pendingJoin.equals(args[0]))return false;
                SteamID entered=pendingJoin;
                pendingJoin=null;
                if(args[3]==SteamMatchmaking.ChatRoomEnterResponse.Success){
                    if(searching)lobby=entered;
                    else steam.leaveLobby(entered);
                }
                return true;
            }
            case "onLobbyKicked" -> {
                if(lobby==null||!lobby.equals(args[0]))return false;
                fail("cancelled");
                return true;
            }
            default -> {return false;}
        }
    }

    private void moveTo(SteamID target){
        closeLobby();
        pendingJoin=target;
        steam.joinLobby(target);
    }

    private boolean owner(){return lobby!=null&&self.equals(steam.getLobbyOwner(lobby));}
    private List<String> members(){
        int count=steam.getNumLobbyMembers(lobby);
        List<String> result=new ArrayList<>(count);
        for(int i=0;i<count;i++)result.add(id(steam.getLobbyMemberByIndex(lobby,i)));
        result.sort(Comparator.naturalOrder());
        return result;
    }
    private int acceptedCount(long deadline){
        if(lobby==null||deadline==0)return 0;
        int accepted=0,count=steam.getNumLobbyMembers(lobby);
        String expected=Long.toString(deadline);
        for(int i=0;i<count;i++){
            SteamID member=steam.getLobbyMemberByIndex(lobby,i);
            if(expected.equals(steam.getLobbyMemberData(lobby,member,READY)))accepted++;
        }
        return accepted;
    }
    private String data(String key){return lobby==null?"":steam.getLobbyData(lobby,key);}
    private long deadline(){
        try{return Long.parseLong(data(DEADLINE));}
        catch(NumberFormatException ignored){return 0;}
    }
    private long now(){return Integer.toUnsignedLong(utils.getServerRealTime());}
    private static int compare(SteamID a,SteamID b){
        return Long.compareUnsigned(SteamNativeHandle.getNativeHandle(a),SteamNativeHandle.getNativeHandle(b));
    }
    private static String id(SteamID value){return Long.toUnsignedString(SteamNativeHandle.getNativeHandle(value));}
}
