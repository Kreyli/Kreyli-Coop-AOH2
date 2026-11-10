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
import static age.of.civilizations2.jakowski.lukasz.mp.KreyliText.t;

/** Local profile preferences and host-authoritative campaign counters. */
public final class KreyliProfile {
    public record Player(String nick,TextureRegion avatar) {}
    static final int UPLOAD_SIDE=128;
    private static final int MAX_AVATARS=24;
    private static final String[] STAT_KEYS={"profile.conquered","profile.earned","profile.recruited"};
    private static final int[] COLORS={0x5768bbff,0xb83a53ff,0x267f65ff,0x8a5bb3ff};
    private static final Map<String,Portrait> portraits=new HashMap<>();
    private static final Map<Integer,long[]> counters=new HashMap<>();
    private static final byte[] EMPTY_AVATAR=new byte[0];
    private static byte[] localAvatar=EMPTY_AVATAR, steamAvatar;
    private static final Map<MP_Net.Conn,byte[]> guestAvatars=new IdentityHashMap<>();
    private static final Map<String,byte[]> remoteAvatars=new HashMap<>();
    private static List<String> roster=List.of();
    private static boolean profilesDirty;
    private static MP_Net.Conn peer;
    private static long nextSend,nextAvatar,lastTick,playNanos,flushedSeconds;
    private static long[] remoteStats=new long[3];
    private static final long[] unflushedStats=new long[3];
    private static List<byte[]> history;
    private static int recruitCiv=-1;
    private static long lastRecruited;
    private static boolean remoteStatsSeen;
    private static boolean initialized,inCampaign;
    private static String requestedName;
    private static boolean requestedDefault;
    private static byte[] sentAvatar;
    private KreyliProfile(){}

    private static Preferences prefs(){return Gdx.app.getPreferences("kreyli-player-profile");}
    private static String account(){return Long.toUnsignedString(KreyliSteam.getSteamID64());}
    public static boolean steamLinked(){return KreyliSteam.getSteamID64()!=0;}
    public static String nick(){
        if(Gdx.app==null)return "Player";
        String custom=prefs().getString(account()+".nick","");
        if(!custom.isEmpty())return custom;
        return defaultNickname();
    }
    private static String defaultNickname(){
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
        changeNickname(KreyliIdentity.cleanNickname(value),false);
    }
    public static void resetNickname(){changeNickname(defaultNickname(),true);}
    private static void changeNickname(String clean,boolean useDefault){
        if(clean.length()<2){MP.toast(t("Ник должен содержать от 2 до 24 символов","Nickname must contain 2 to 24 characters"),false);return;}
        if(MP.isClient()){
            if(peer!=null){requestedName=clean;requestedDefault=useDefault;peer.send("kreyli_profile_name",clean);}
            return;
        }
        if(MP.clients.stream().anyMatch(c->clean.equalsIgnoreCase(c.nick))){
            MP.toast(t("Этот ник уже занят","This nickname is already taken"),false);return;
        }
        saveName(clean,useDefault);
        if(MP.isHost()){KreyliLobby.profileChanged();profilesDirty=true;nextSend=0;}
    }
    private static void saveName(String name,boolean useDefault){
        Preferences preferences=prefs();
        String key=account()+".nick";
        if(useDefault)preferences.remove(key);
        else preferences.putString(key,name);
        preferences.flush();
        MP.nick=name;
        KreyliIdentity.clearAlias();
    }
    public static void cycleAvatar(){
        int current=prefs().getInteger(account()+".avatar",-1);
        int next=current+1>=COLORS.length?-1:current+1;
        prefs().putInteger(account()+".avatar",next).flush();
        refreshAvatar();profilesDirty=true;nextSend=0;
    }
    private static List<byte[]> history(){
        if(history!=null)return history;
        history=new ArrayList<>();
        int count=Math.min(MAX_AVATARS,prefs().getInteger("uploaded.count",0));
        for(int i=0;i<count;i++){
            try{
                byte[] picture=Base64.getDecoder().decode(prefs().getString("uploaded."+i,""));
                if(avatarSide(picture)>0)history.add(picture);
            }catch(IllegalArgumentException ignored){}
        }
        return history;
    }
    public static int avatarCount(){return history().size();}
    public static int selectedAvatar(){
        return prefs().getInteger(account()+".avatar",-1)==-2?prefs().getInteger(account()+".upload",-1):-1;
    }
    public static TextureRegion avatarPreview(int index){
        return index>=0&&index<avatarCount()?portrait("upload:"+index,history().get(index)):null;
    }
    /** Adds a validated thumbnail to the local library. Choosing it is a separate action. */
    public static int addAvatar(byte[] picture){
        if(avatarSide(picture)==0)throw new IllegalArgumentException(t("Некорректное изображение","Invalid image"));
        for(int i=0;i<avatarCount();i++)if(Arrays.equals(history().get(i),picture))return i;
        if(avatarCount()>=MAX_AVATARS)throw new IllegalStateException(t("В истории уже 24 аватара","Avatar history already contains 24 images"));
        int index=avatarCount();
        history().add(picture.clone());
        prefs().putString("uploaded."+index,Base64.getEncoder().encodeToString(picture))
                .putInteger("uploaded.count",index+1).flush();
        return index;
    }
    public static void selectAvatar(int index){
        if(index<-1||index>=avatarCount())return;
        prefs().putInteger(account()+".avatar",index<0?-1:-2)
                .putInteger(account()+".upload",index).flush();
        refreshAvatar();profilesDirty=true;nextSend=0;
    }
    private static void refreshAvatar(){
        int choice=prefs().getInteger(account()+".avatar",-1);
        int uploaded=selectedAvatar();
        if(uploaded>=0&&uploaded<avatarCount()){localAvatar=history().get(uploaded);return;}
        if(choice<0||choice>=COLORS.length){localAvatar=steamAvatar==null?new byte[0]:steamAvatar;return;}
        byte[] pixels=new byte[32*32*4];int bg=COLORS[choice];
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
        if(MP.isHost())for(var c:MP.clients)if(name.equals(c.nick))
            return portrait("remote:"+name,guestAvatars.getOrDefault(c,EMPTY_AVATAR));
        return portrait("remote:"+name,remoteAvatars.getOrDefault(name,EMPTY_AVATAR));
    }
    private record Portrait(byte[] data,TextureRegion image){}
    private static TextureRegion portrait(String slot,byte[] data){
        Portrait old=portraits.get(slot);
        if(old!=null&&Arrays.equals(old.data,data))return old.image;
        if(old!=null){old.image.getTexture().dispose();portraits.remove(slot);}
        int side=avatarSide(data);
        if(side==0)return null;
        Pixmap pix=new Pixmap(side,side,Pixmap.Format.RGBA8888);
        try{
            pix.getPixels().put(data).rewind();
            Texture texture=new Texture(pix);
            texture.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
            TextureRegion region=new TextureRegion(texture);
            portraits.put(slot,new Portrait(data,region));
            return region;
        }finally{pix.dispose();}
    }
    public static List<Player> player(int civ){
        if(civ<=0)return List.of();
        if(!MP.isOn())return civ==localCiv()?List.of(new Player(nick(),avatar())):List.of();
        var players=new ArrayList<Player>(2);
        for(int i=0;i<Math.min(MP.rosterCivs.length,MP.rosterNicks.length);i++)
            if(MP.rosterCivs[i]==civ)players.add(new Player(MP.rosterNicks[i],avatar(MP.rosterNicks[i])));
        return players;
    }
    private static boolean playing(){return CFG.menus!=null&&CFG.menus.getInGameView()&&CFG.core!=null&&CFG.core.getPlayersSize()>0;}
    private static int localCiv(){return MP.isClient()?MP.myCiv:playing()?CFG.core.getPlayer(CFG.PLAYER_TURN_ID).getCivId():-1;}
    public static long playSeconds(){return prefs().getLong("playSeconds",0)+playNanos/1_000_000_000L-flushedSeconds;}
    public static long conquered(){return totalStat(0);}
    public static long earned(){return totalStat(1);}
    public static long recruited(){return totalStat(2);}
    private static long totalStat(int index){return add(prefs().getLong(STAT_KEYS[index],0),unflushedStats[index]);}
    private static long add(long total,long amount){return total>Long.MAX_VALUE-amount?Long.MAX_VALUE:total+amount;}
    private static void record(int index,long delta){
        if(delta>0)unflushedStats[index]=add(unflushedStats[index],delta);
    }
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
        if(amount>civ.getGold()&&tracks(id)){
            long delta=amount-civ.getGold();
            if(delta<=0)return;
            counters.computeIfAbsent(id,k->new long[2])[1]+=delta;
            if(id==localCiv())record(1,delta);
        }
    }
    public static void capture(Province province,int civ){
        if(!tracks(civ))return;
        int old=province.getCivId();
        if(old>0&&old!=civ&&CFG.core.getCivsAtWar(old,civ)){
            counters.computeIfAbsent(civ,k->new long[2])[0]++;
            if(civ==localCiv())record(0,1);
        }
    }
    private static void flushTime(){
        Preferences storage=prefs();
        boolean dirty=false;
        long seconds=playNanos/1_000_000_000L;
        if(seconds>flushedSeconds){
            storage.putLong("playSeconds",storage.getLong("playSeconds",0)+seconds-flushedSeconds);
            flushedSeconds=seconds;
            dirty=true;
        }
        for(int i=0;i<STAT_KEYS.length;i++)if(unflushedStats[i]>0){
            storage.putLong(STAT_KEYS[i],totalStat(i));unflushedStats[i]=0;dirty=true;
        }
        if(dirty)storage.flush();
    }
    public static void tick(){
        if(Gdx.app==null)return;
        long now=System.nanoTime();
        boolean playing=playing();
        if(inCampaign&&playing&&lastTick!=0)playNanos+=Math.min(now-lastTick,2_000_000_000L);
        int civ=playing&&!MP.isClient()?localCiv():-1;
        long recruited=civ>0&&MP.validCiv(civ)?CFG.core.getCiv(civ).civGD.recruitedArmy:0;
        if(civ>0&&civ==recruitCiv&&recruited>lastRecruited)record(2,recruited-lastRecruited);
        recruitCiv=civ;lastRecruited=recruited;
        if(inCampaign&&!playing||playNanos/1_000_000_000L-flushedSeconds>=15)flushTime();
        inCampaign=playing;lastTick=now;
        if(!initialized){refreshAvatar();initialized=true;}
        if(steamLinked()&&steamAvatar==null&&now>=nextAvatar){
            steamAvatar=KreyliSteam.profileAvatar();nextAvatar=now+5_000_000_000L;
            if(steamAvatar!=null){refreshAvatar();profilesDirty=true;nextSend=0;CFG.setRenderO(true);}
        }
        if(MP.isHost()){
            tickHost(now);
        }else if(MP.isClient()&&peer!=null&&sentAvatar!=localAvatar){
            peer.send("kreyli_profile_avatar",localAvatar);
            sentAvatar=localAvatar;
        }
    }
    private static void tickHost(long now){
        guestAvatars.keySet().retainAll(MP.clients);
        var names=new ArrayList<String>();
        names.add(MP.nick);
        for(var guest:MP.clients)if(guest.nick!=null)names.add(guest.nick);
        if(profilesDirty||!roster.equals(names)){
            setRoster(names);
            profilesDirty=false;
            Object[] list=new Object[names.size()+1];
            list[0]="kreyli_profile_list";
            for(int i=0;i<names.size();i++)list[i+1]=names.get(i);
            for(var recipient:MP.clients)if(recipient.nick!=null){
                recipient.send(list);
                recipient.send("kreyli_profile_picture",MP.nick,localAvatar);
                for(var guest:MP.clients)if(guest!=recipient&&guest.nick!=null)
                    recipient.send("kreyli_profile_picture",guest.nick,guestAvatars.getOrDefault(guest,EMPTY_AVATAR));
            }
        }
        if(now>=nextSend){
            for(var guest:MP.clients)if(guest.nick!=null){
                long[] stats=totals(guest.civ);
                guest.send("kreyli_profile_stats",guest.civ,stats[0],stats[1],stats[2]);
            }
            nextSend=now+1_000_000_000L;
        }
    }
    private static void setRoster(List<String> names){
        for(String old:roster)if(!names.contains(old)){
            remoteAvatars.remove(old);
            Portrait portrait=portraits.remove("remote:"+old);
            if(portrait!=null)portrait.image.getTexture().dispose();
        }
        roster=List.copyOf(names);
    }
    static void connected(MP_Net.Conn connection){peer=connection;sentAvatar=null;nextSend=0;}
    static void reset(){
        if(initialized&&Gdx.app!=null)flushTime();
        peer=null;sentAvatar=null;setRoster(List.of());guestAvatars.clear();remoteAvatars.clear();
        profilesDirty=true;remoteStats=new long[3];remoteStatsSeen=false;recruitCiv=-1;
        requestedName=null;counters.clear();inCampaign=false;nextSend=0;
    }
    private static int avatarSide(byte[] data){
        return switch(data.length){
            case 32*32*4 -> 32;
            case 64*64*4 -> 64;
            case UPLOAD_SIDE*UPLOAD_SIDE*4 -> UPLOAD_SIDE;
            case 184*184*4 -> 184;
            default -> 0;
        };
    }
    private static boolean validAvatar(byte[] data){return data.length==0||avatarSide(data)>0;}
    static boolean hostMessage(MP_Net.Conn sender,Object[] data){
        if(data.length==0||!(data[0] instanceof String op)||!op.startsWith("kreyli_profile_"))return false;
        if(!MP.isHost()||sender.nick==null||!MP.clients.contains(sender))return true;
        if(op.equals("kreyli_profile_avatar")&&data.length==2&&data[1] instanceof byte[] picture&&validAvatar(picture)){
            byte[] previous=guestAvatars.put(sender,picture);
            if(!Arrays.equals(previous,picture)){profilesDirty=true;CFG.setRenderO(true);}
        }else if(op.equals("kreyli_profile_name")&&data.length==2&&data[1] instanceof String name){
            boolean allowed=name.length()>=2&&name.equals(KreyliIdentity.cleanNickname(name))&&!name.equalsIgnoreCase(MP.nick)
                    &&MP.clients.stream().noneMatch(c->c!=sender&&name.equalsIgnoreCase(c.nick));
            if(allowed){sender.nick=name;profilesDirty=true;KreyliLobby.profileChanged();}
            sender.send("kreyli_profile_result",name,allowed);
        }
        return true;
    }
    static boolean clientMessage(Object[] data){
        if(data.length==0||!(data[0] instanceof String op)||!op.startsWith("kreyli_profile_"))return false;
        if(!MP.isClient())return true;
        if(op.equals("kreyli_profile_list")&&data.length>=2&&data.length<=11){
            var names=new ArrayList<String>();
            for(int i=1;i<data.length;i++){
                if(!(data[i] instanceof String name)||name.isEmpty()||!name.equals(KreyliIdentity.cleanNickname(name)))return true;
                names.add(name);
            }
            setRoster(names);
        }else if(op.equals("kreyli_profile_picture")&&data.length==3&&data[1] instanceof String name
                &&roster.contains(name)&&data[2] instanceof byte[] image&&validAvatar(image)){
            if(!Arrays.equals(remoteAvatars.put(name,image),image))CFG.setRenderO(true);
        }else if(op.equals("kreyli_profile_stats")&&data.length==5&&data[1] instanceof Integer civ&&data[2] instanceof Long captures
                &&data[3] instanceof Long money&&data[4] instanceof Long troops){
            long[] next={captures,money,troops};
            if(remoteStatsSeen&&MP.myCiv==civ&&civ>0)for(int i=0;i<next.length;i++)
                if(next[i]>=0&&next[i]>remoteStats[i]&&remoteStats[i]>=0)record(i,next[i]-remoteStats[i]);
            remoteStatsSeen=MP.myCiv==civ&&civ>0;
            remoteStats=next;
        }else if(op.equals("kreyli_profile_result")&&data.length==3&&data[1] instanceof String name
                &&data[2] instanceof Boolean allowed&&name.equals(requestedName)){
            requestedName=null;
            if(allowed)saveName(name,requestedDefault);else MP.toast(t("Этот ник уже занят","This nickname is already taken"),false);
        }
        return true;
    }
}
