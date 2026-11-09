package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.CFG;
import age.of.civilizations2.jakowski.lukasz.Civilization;
import age.of.civilizations2.jakowski.lukasz.Province;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import java.util.*;

/** Local profile preferences and host-authoritative campaign counters. */
public final class KreyliProfile {
    public record Player(String nick,TextureRegion avatar) {}
    private static final int AVATAR_BYTES=32*32*4;
    private static final int[] COLORS={0x5768bbff,0xb83a53ff,0x267f65ff,0x8a5bb3ff};
    private static final Map<String,Portrait> portraits=new HashMap<>();
    private static final Map<Integer,long[]> counters=new HashMap<>();
    private static String remoteName="";
    private static byte[] remoteAvatar=new byte[0], localAvatar=new byte[0], steamAvatar;
    private static MP_Net.Conn peer;
    private static long nextSend,nextAvatar,lastTick,playNanos,flushedSeconds;
    private static long[] remoteStats=new long[3];
    private static boolean initialized,inCampaign;
    private static String requestedName;
    private KreyliProfile(){}

    private static Preferences prefs(){return Gdx.app.getPreferences("kreyli-player-profile");}
    private static String account(){return Long.toUnsignedString(KreyliSteam.getSteamID64());}
    public static boolean steamLinked(){return KreyliSteam.getSteamID64()!=0;}
    public static String nick(){
        if(Gdx.app==null)return "Player";
        String custom=prefs().getString(account()+".nick","");
        if(!custom.isEmpty())return custom;
        String steam=KreyliIdentity.cleanNickname(KreyliSteam.profileName());
        if(!steam.isEmpty())return steam;
        String offline=prefs().getString("offlineName","");
        if(offline.isEmpty()){
            offline="Player-"+UUID.randomUUID().toString().substring(0,8);
            prefs().putString("offlineName",offline).flush();
        }
        return offline;
    }
    public static String displayName(){return MP.isOn()&&!MP.nick.isBlank()?MP.nick:nick();}
    public static void setNickname(String value){
        String clean=KreyliIdentity.cleanNickname(value);
        if(clean.length()<2){MP.toast("Ник должен содержать от 2 до 24 символов",false);return;}
        if(MP.isClient()){
            if(peer!=null){requestedName=clean;peer.send("kreyli_profile_name",clean);}
            return;
        }
        if(MP.clients.stream().anyMatch(c->clean.equalsIgnoreCase(c.nick))){
            MP.toast("Этот ник уже занят",false);return;
        }
        saveName(clean);
        if(MP.isHost()){KreyliLobby.profileChanged();nextSend=0;}
    }
    private static void saveName(String name){
        prefs().putString(account()+".nick",name).flush();MP.nick=name;
        KreyliIdentity.clearAlias();
    }
    public static void cycleAvatar(){
        int current=prefs().getInteger(account()+".avatar",-1);
        int next=current+1>=COLORS.length?-1:current+1;
        prefs().putInteger(account()+".avatar",next).flush();
        refreshAvatar();nextSend=0;
    }
    private static void refreshAvatar(){
        int choice=prefs().getInteger(account()+".avatar",-1);
        if(choice<0||choice>=COLORS.length){localAvatar=steamAvatar==null?new byte[0]:steamAvatar;return;}
        byte[] pixels=new byte[AVATAR_BYTES];int bg=COLORS[choice];
        for(int y=0;y<32;y++)for(int x=0;x<32;x++){
            int distance=Math.abs(x-15)+Math.abs(y-15);
            int color=distance>=8&&distance<=12?0xf0dfb3ff:bg;
            int offset=(y*32+x)*4;
            for(int i=0;i<4;i++)pixels[offset+i]=(byte)(color>>>(24-i*8));
        }
        localAvatar=pixels;
    }
    public static TextureRegion avatar(){return portrait("local",localAvatar);}
    public static TextureRegion avatar(String name){
        if(name==null)return null;
        if(name.equals(displayName()))return avatar();
        return name.equals(remoteName)?portrait("remote",remoteAvatar):null;
    }
    private record Portrait(byte[] data,TextureRegion image){}
    private static TextureRegion portrait(String slot,byte[] data){
        Portrait old=portraits.get(slot);
        if(old!=null&&Arrays.equals(old.data,data))return old.image;
        if(old!=null){old.image.getTexture().dispose();portraits.remove(slot);}
        if(data.length!=AVATAR_BYTES)return null;
        Pixmap pix=new Pixmap(32,32,Pixmap.Format.RGBA8888);
        try{
            pix.getPixels().put(data).rewind();
            TextureRegion region=new TextureRegion(new Texture(pix));
            portraits.put(slot,new Portrait(data,region));
            return region;
        }finally{pix.dispose();}
    }
    public static List<Player> player(int civ){
        if(civ<=0||!MP.isOn())return List.of();
        var players=new ArrayList<Player>(2);
        for(int i=0;i<Math.min(MP.rosterCivs.length,MP.rosterNicks.length);i++)
            if(MP.rosterCivs[i]==civ)players.add(new Player(MP.rosterNicks[i],avatar(MP.rosterNicks[i])));
        return players;
    }
    private static boolean playing(){return CFG.menus!=null&&CFG.menus.getInGameView()&&CFG.core!=null&&CFG.core.getPlayersSize()>0;}
    private static int localCiv(){return MP.isClient()?MP.myCiv:playing()?CFG.core.getPlayer(CFG.PLAYER_TURN_ID).getCivId():-1;}
    public static long playSeconds(){return prefs().getLong("playSeconds",0)+playNanos/1_000_000_000L-flushedSeconds;}
    public static long conquered(){return stats()[0];}
    public static long earned(){return stats()[1];}
    public static long recruited(){return stats()[2];}
    private static long[] stats(){return MP.isClient()?remoteStats:totals(localCiv());}
    private static long[] totals(int civ){
        long[] values=counters.getOrDefault(civ,new long[2]);
        long soldiers=CFG.core!=null&&MP.validCiv(civ)?CFG.core.getCiv(civ).civGD.recruitedArmy:0;
        return new long[]{values[0],values[1],soldiers};
    }
    private static boolean tracks(int civ){
        if(!inCampaign||!playing()||MP.isClient()||MP.applying||civ<=0)return false;
        return civ==localCiv()||MP.clients.stream().anyMatch(c->c.civ==civ);
    }
    public static void gold(Civilization civ,long amount){
        if(!inCampaign)return;
        int id=civ.getCivId();
        if(amount>civ.getGold()&&tracks(id))counters.computeIfAbsent(id,k->new long[2])[1]+=amount-civ.getGold();
    }
    public static void capture(Province province,int civ){
        if(!tracks(civ))return;
        int old=province.getCivId();
        if(old>0&&old!=civ&&CFG.core.getCivsAtWar(old,civ))
            counters.computeIfAbsent(civ,k->new long[2])[0]++;
    }
    private static void flushTime(){
        long seconds=playNanos/1_000_000_000L;
        if(seconds>flushedSeconds){
            prefs().putLong("playSeconds",prefs().getLong("playSeconds",0)+seconds-flushedSeconds).flush();
            flushedSeconds=seconds;
        }
    }
    public static void tick(){
        if(Gdx.app==null)return;
        long now=System.nanoTime();
        boolean playing=playing();
        if(inCampaign&&playing&&lastTick!=0)playNanos+=Math.min(now-lastTick,2_000_000_000L);
        if(inCampaign&&!playing||playNanos/1_000_000_000L-flushedSeconds>=15)flushTime();
        inCampaign=playing;lastTick=now;
        if(!initialized){refreshAvatar();initialized=true;}
        if(steamLinked()&&steamAvatar==null&&now>=nextAvatar){
            steamAvatar=KreyliSteam.profileAvatar();nextAvatar=now+5_000_000_000L;
            if(steamAvatar!=null){refreshAvatar();nextSend=0;}
        }
        if(MP.isHost()){
            MP_Net.Conn member=MP.clients.isEmpty()?null:MP.clients.get(0);
            if(member!=peer){peer=member;remoteName="";remoteAvatar=new byte[0];nextSend=0;}
            if(peer!=null&&peer.nick!=null&&now>=nextSend){
                long[] stats=totals(peer.civ);
                peer.send("kreyli_profile_state",MP.nick,localAvatar,stats[0],stats[1],stats[2]);
                nextSend=now+1_000_000_000L;
            }
        }else if(MP.isClient()&&peer!=null&&now>=nextSend){
            peer.send("kreyli_profile_avatar",localAvatar);
            nextSend=now+5_000_000_000L;
        }
    }
    static void connected(MP_Net.Conn connection){peer=connection;nextSend=0;}
    static void reset(){
        if(initialized&&Gdx.app!=null)flushTime();
        peer=null;remoteName="";remoteAvatar=new byte[0];remoteStats=new long[3];
        requestedName=null;counters.clear();inCampaign=false;nextSend=0;
    }
    private static boolean validAvatar(byte[] data){return data.length==0||data.length==AVATAR_BYTES;}
    static boolean hostMessage(MP_Net.Conn sender,Object[] data){
        if(data.length==0||!(data[0] instanceof String op)||!op.startsWith("kreyli_profile_"))return false;
        if(!MP.isHost()||sender.nick==null||!MP.clients.contains(sender))return true;
        if(op.equals("kreyli_profile_avatar")&&data.length==2&&data[1] instanceof byte[] picture&&validAvatar(picture)){
            peer=sender;remoteName=sender.nick;remoteAvatar=picture;
        }else if(op.equals("kreyli_profile_name")&&data.length==2&&data[1] instanceof String name){
            boolean allowed=name.length()>=2&&name.equals(KreyliIdentity.cleanNickname(name))&&!name.equalsIgnoreCase(MP.nick)
                    &&MP.clients.stream().noneMatch(c->c!=sender&&name.equalsIgnoreCase(c.nick));
            if(allowed){sender.nick=name;remoteName=name;KreyliLobby.profileChanged();}
            sender.send("kreyli_profile_result",name,allowed);
        }
        return true;
    }
    static boolean clientMessage(Object[] data){
        if(data.length==0||!(data[0] instanceof String op)||!op.startsWith("kreyli_profile_"))return false;
        if(!MP.isClient())return true;
        if(op.equals("kreyli_profile_state")&&data.length==6&&data[1] instanceof String name
                &&data[2] instanceof byte[] image&&validAvatar(image)&&data[3] instanceof Long captures
                &&data[4] instanceof Long money&&data[5] instanceof Long troops){
            remoteName=name;remoteAvatar=image;remoteStats=new long[]{captures,money,troops};
        }else if(op.equals("kreyli_profile_result")&&data.length==3&&data[1] instanceof String name
                &&data[2] instanceof Boolean allowed&&name.equals(requestedName)){
            requestedName=null;
            if(allowed)saveName(name);else MP.toast("Этот ник уже занят",false);
        }
        return true;
    }
}
