package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.*;
import age.of.civilizations2.jakowski.lukasz.Menus.Load.Scenario.Menu_LoadScenario;
import age.of.civilizations2.jakowski.lukasz.Menus.NewGame.Menu_CreateNewGame;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import java.lang.reflect.Method;
import java.util.*;
import java.util.Map;

import static age.of.civilizations2.jakowski.lukasz.mp.KreyliText.t;

/** Host-owned match built on the normal multiplayer session transport. */
public final class KreyliQuickGame {
    private static final Set<String> EXCLUDED=Set.of("usa","us","can","sov","ussr","rus","russia");
    private static final Method ASSIGN=method("assign",MP_Net.Conn.class,int.class);
    private static final Method SYSTEM=method("broadcastSystem",String.class,String.class,String.class,String.class);
    private static final Map<Integer,Seat> seats=new LinkedHashMap<>();
    private static final Map<MP_Net.Conn,Integer> assignments=new LinkedHashMap<>();
    private static int expected,seconds,lastSent=-1,lastTurn=-1;
    private static long connectionDeadline,matchDeadline;
    private static boolean loading,launching;
    private static String results="", winner="";
    private static int exitX,exitY,exitWidth,exitHeight;
    private static boolean exitPressed;

    private static final class Seat {
        final String nick;
        int provinces, defeated, lastConquest=-1, eliminated=-1, conqueror;
        Seat(String nick){this.nick=nick;}
    }

    private KreyliQuickGame() {}
    private static Method method(String name,Class<?>... parameters){
        try{Method m=MP.class.getDeclaredMethod(name,parameters);m.setAccessible(true);return m;}
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static void invoke(Method method,Object... args){
        try{method.invoke(null,args);}
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    public static boolean active(){return expected>=KreyliQuickQueue.MIN_PLAYERS;}
    public static boolean running(){return active()&&matchDeadline!=0&&results.isEmpty();}
    public static String host(int players){
        if(MP.isOn()||players<KreyliQuickQueue.MIN_PLAYERS||players>KreyliQuickQueue.MAX_PLAYERS)return null;
        MP.hostDirect=false;
        MP.listInLobby=false;
        MP.password=UUID.randomUUID().toString().replace("-","").substring(0,16);
        MP.turnSeconds=20;
        if(!MP.startHost()||MP.roomCode()==null){MP.stop();return null;}
        armHost(players);
        MP_UI.open(View.eMP_HOST);
        return MP.roomCode()+"\n"+MP.selectedRegion()[0]+"\n"+MP.password;
    }
    static void armHost(int players){
        expected=players;
        connectionDeadline=System.nanoTime()+75_000_000_000L;
    }
    public static void join(String roomCode,String relay,String password,int players){
        if(MP.isOn()||players<KreyliQuickQueue.MIN_PLAYERS||players>KreyliQuickQueue.MAX_PLAYERS)return;
        if(!KreyliSteam.joinQuickRoom(roomCode,relay,password))return;
        expected=players;
        connectionDeadline=System.nanoTime()+75_000_000_000L;
        MP_UI.open(View.eMP_JOINING);
    }
    static void reset(){
        expected=seconds=0;
        lastSent=lastTurn=-1;
        connectionDeadline=matchDeadline=0;
        loading=launching=exitPressed=false;
        results=winner="";
        seats.clear();
        assignments.clear();
    }
    static void tick(){
        if(!active())return;
        if(matchDeadline==0&&System.nanoTime()>=connectionDeadline){cancel(t("Не все игроки подключились","Some players did not connect"));return;}
        if(running())pruneRoster();
        if(!MP.isHost())return;
        if(!loading&&!launching&&!MP.started&&MP.clients.stream().filter(c->c.nick!=null).count()==expected-1){
            loading=true;
            Gdx.app.postRunnable(KreyliQuickGame::loadScenario);
        }
        if(loading&&CFG.menus.getInCreateNewGame()){
            loading=false;
            launching=true;
            Gdx.app.postRunnable(KreyliQuickGame::startScenario);
        }
        if(launching&&MP.started&&MP.inputOrders()){
            launching=false;
            for(var entry:assignments.entrySet())if(MP.clients.contains(entry.getKey()))
                invoke(ASSIGN,entry.getKey(),entry.getValue());
            matchDeadline=System.nanoTime()+KreyliQuickRules.MATCH_MILLIS*1_000_000L;
            MP.requestSync();
        }
        if(!running())return;
        int remaining=(int)Math.max(0,(matchDeadline-System.nanoTime()+999_999_999L)/1_000_000_000L);
        if(remaining!=lastSent){
            lastSent=seconds=remaining;
            MP.broadcast("kreyli_quick_clock",expected,remaining);
            CFG.setRenderO(true);
        }
        if(GameCalendar.TURNID!=lastTurn||remaining==0){
            lastTurn=GameCalendar.TURNID;
            updateStandings(remaining);
        }
    }

    private static void loadScenario(){
        if(!active()||!MP.isHost())return;
        var scenarios=CFG.core.getGameScenars();
        var available=new ArrayList<Integer>();
        for(int i=0;i<Game_Scenarios.SCENARIOS_SIZE;i++){
            String tag=scenarios.getScenarioTagID(i);
            if(Set.of("worldwar2","modernworld","1749546282755cynjoyka","1749547484508lojlsbqk").contains(tag))available.add(i);
        }
        if(available.isEmpty()){cancel(t("На этой карте нет сценариев быстрой игры","This map has no Quick Game scenarios"));return;}
        Collections.shuffle(available);
        CFG.core.setScenarioID(available.getFirst());
        Menu_LoadScenario.editor=false;
        Menu_LoadScenario.goToView=View.eCREATE_NEW_GAME;
        Menu_LoadScenario.loadActionEND=2;
        CFG.menus.setMenuIDWithoutAnim(View.eLOAD_SCENARIO);
    }

    private static void startScenario(){
        if(!active()||!MP.isHost())return;
        if(MP.clients.size()!=expected-1){cancel(t("Игрок покинул комнату","A player left the room"));return;}
        var byContinent=new TreeMap<Integer,List<Integer>>();
        int ocean=CFG.map.getMapContinents().getOceanContinentID();
        for(int civ=1;civ<CFG.core.getCivsSize();civ++){
            Civilization country=CFG.core.getCiv(civ);
            String tag=country.getCivTag().toLowerCase(Locale.ROOT).split("_",2)[0];
            int capital=country.getCapitalProvID();
            if(!MP.pickable(civ)||capital<0||EXCLUDED.contains(tag)||country.getNumOfProvs()>300)continue;
            int continent=CFG.core.getProv(capital).getContinent();
            if(continent>=0&&continent!=ocean)byContinent.computeIfAbsent(continent,k->new ArrayList<>()).add(civ);
        }
        var eligible=new ArrayList<>(byContinent.values().stream().filter(countries->countries.size()>=expected).toList());
        if(eligible.isEmpty()){cancel(t("Для матча не хватает стран","Not enough eligible countries"));return;}
        Collections.shuffle(eligible);
        List<Integer> countries=eligible.getFirst();
        Collections.shuffle(countries);
        int hostCountry=countries.getFirst();
        seats.put(hostCountry,new Seat(MP.nick));
        for(int i=0;i<MP.clients.size();i++){
            var guest=MP.clients.get(i);
            int civ=countries.get(i+1);
            seats.put(civ,new Seat(guest.nick));
            assignments.put(guest,civ);
        }
        for(int civ=1;civ<CFG.core.getCivsSize();civ++){
            Civilization country=CFG.core.getCiv(civ);
            country.setAlliance(0);
            if(country.getPuppetOfCiv()>0&&country.getPuppetOfCiv()!=civ)CFG.core.resetVassal_CivID(civ);
            for(int other=1;other<CFG.core.getCivsSize();other++)if(civ!=other)country.setRelationD(other,0);
        }
        CFG.core.getPlayer(0).setCivId(hostCountry);
        KreyliCountrySeats.configureHost(hostCountry,1);
        Menu_CreateNewGame.newGame();
    }

    public static boolean allowDiplomacy(int first,int second){
        return !active();
    }
    static void capture(Province province,int civ){
        if(!running()||!MP.isHost())return;
        int old=province.getCivId();
        if(old==civ||old<=0||!MP.validCiv(civ)||!CFG.core.getCivsAtWar(old,civ))return;
        Seat attacker=seats.get(civ);
        if(attacker!=null)attacker.provinces++;
        Seat defender=seats.get(old);
        if(defender!=null)defender.conqueror=civ;
    }
    public static boolean allowColonization(){return !active();}
    public static boolean allowMovement(int destination,int civ){
        if(!active())return true;
        if(blocksOrders(civ))return false;
        if(!running()||!MP.isHost()||destination<0||destination>=CFG.core.getProvinSize())return true;
        return allowWar(civ,CFG.core.getProv(destination).getCivId());
    }
    public static boolean allowWar(int attacker,int target){
        if(!running()||!MP.isHost()||!seats.containsKey(target))return true;
        Seat seat=seats.get(attacker);
        return seat==null||KreyliQuickRules.canConquerPlayer(GameCalendar.TURNID,seat.lastConquest);
    }

    private static void pruneRoster(){
        int length=Math.min(MP.rosterCivs.length,Math.min(MP.rosterNicks.length,MP.rosterReady.length));
        int count=0;
        for(int i=0;i<length;i++)if(MP.rosterCivs[i]>0&&!MP.eliminated(MP.rosterCivs[i]))count++;
        if(count==length)return;
        int[] countries=new int[count];
        String[] names=new String[count];
        boolean[] ready=new boolean[count];
        int write=0;
        for(int i=0;i<length;i++)if(MP.rosterCivs[i]>0&&!MP.eliminated(MP.rosterCivs[i])){
            countries[write]=MP.rosterCivs[i];
            names[write]=MP.rosterNicks[i];
            ready[write++]=MP.rosterReady[i];
        }
        MP.rosterCivs=countries;
        MP.rosterNicks=names;
        MP.rosterReady=ready;
        MP_UI.onRosterChanged();
    }

    static int localCountry(){
        if(MP.isClient())return MP.myCiv;
        return CFG.core!=null&&CFG.core.getPlayersSize()>0?CFG.core.getPlayer(0).getCivId():0;
    }

    private static void updateStandings(int remaining){
        var standings=new ArrayList<KreyliQuickRules.Standing>();
        for(var entry:seats.entrySet()){
            int civ=entry.getKey();
            Seat seat=entry.getValue();
            if(seat.eliminated<0&&MP.eliminated(civ)){
                seat.eliminated=GameCalendar.TURNID;
                Seat attacker=seats.get(seat.conqueror);
                if(attacker!=null){attacker.defeated++;attacker.lastConquest=GameCalendar.TURNID;}
                String victor=attacker!=null?attacker.nick:seat.conqueror>0&&MP.validCiv(seat.conqueror)?CFG.core.getCiv(seat.conqueror).getCivName():t("ИИ","AI");
                invoke(SYSTEM,"Kreyli_QuickDefeat",t("{0} завоевал {1}","{0} conquered {1}"),victor,seat.nick);
            }
        }
        for(var entry:seats.entrySet()){
            Seat seat=entry.getValue();
            int rank=MP.validCiv(entry.getKey())?CFG.core.getCiv(entry.getKey()).getRankPos():Integer.MAX_VALUE;
            standings.add(new KreyliQuickRules.Standing(seat.nick,rank,seat.eliminated<0,seat.eliminated,seat.provinces,seat.defeated));
        }
        if(!KreyliQuickRules.shouldFinish(standings,(KreyliQuickRules.MATCH_MILLIS/1000-remaining)*1000))return;
        var ordered=KreyliQuickRules.results(standings);
        if(!ordered.isEmpty()&&ordered.getFirst().alive()&&ordered.getFirst().player().equals(MP.nick))KreyliProfile.recordQuickWin();
        var rows=new StringBuilder();
        for(var player:ordered){
            if(!rows.isEmpty())rows.append('\n');
            rows.append(player.player()).append('\t').append(player.provinces()).append('\t').append(player.playersDefeated());
        }
        results=rows.toString();
        winner=ordered.isEmpty()||!ordered.getFirst().alive()?"":ordered.getFirst().player();
        MP.broadcast("kreyli_quick_result",winner,results);
        CFG.setRenderO(true);
    }

    static boolean clientMessage(Object[] data){
        if(!MP.isClient()||data.length==0)return false;
        if("kreyli_quick_clock".equals(data[0])&&data.length==3&&data[1] instanceof Integer count&&data[2] instanceof Integer time
                &&count>=3&&count<=10&&time>=0&&time<=3600){
            expected=count;
            seconds=time;
            matchDeadline=1;
            CFG.setRenderO(true);
            return true;
        }
        if(active()&&"kreyli_quick_result".equals(data[0])&&data.length==3&&data[1] instanceof String winnerName&&data[2] instanceof String rows&&rows.length()<=4096){
            if(results.isEmpty()&&winnerName.equals(MP.nick))KreyliProfile.recordQuickWin();
            winner=winnerName;
            results=rows;
            CFG.setRenderO(true);
            return true;
        }
        return false;
    }

    private static void cancel(String reason){
        MP.stop();
        MP_UI.open(View.eMAINMENU);
        MP.toast(reason,false);
    }

    public static boolean blocksOrders(int civ){return active()&&(finished()||MP.validCiv(civ)&&MP.eliminated(civ));}
    public static boolean finished(){return !results.isEmpty();}
    public static void draw(SpriteBatch batch){
        if(!active()||!CFG.menus.getInGameView())return;
        float color=batch.getPackedColor();
        try{
            int width=Math.min(530,CFG.GAMEWIDTH-CFG.PADD*2),x=(CFG.GAMEWIDTH-width)/2;
            if(running()){
                String time=String.format(Locale.ROOT,"%02d:%02d",seconds/60,seconds%60);
                MP_UI.box(batch,x+width/2-56,CFG.BUTTON_H+CFG.PADD,112,28,.85f);
                line(batch,time,x,width,CFG.BUTTON_H+CFG.PADD+5,Color.GOLD);
                if(MP.eliminated(localCountry()))
                    line(batch,t("Наблюдатель","Spectator"),x,width,CFG.BUTTON_H+CFG.PADD+36,Color.WHITE);
            }else if(finished()){
                var rows=results.split("\n");
                int row=Math.max(25,CFG.TEXT_HEIGHT_DEFAULT_SMALL+8);
                int height=(rows.length+4)*row,y=(CFG.GAMEHEIGHT-height)/2;
                MP_UI.box(batch,x,y,width,height,.96f);
                line(batch,winner.isBlank()?t("Итоги быстрой игры","Quick Game results"):t("Победитель: ","Winner: ")+winner,x,width,y+5,Color.GOLD);
                line(batch,t("Игрок · провинции · побеждённые игроки","Player · provinces · opponents defeated"),x,width,y+row+5,Color.WHITE);
                for(int i=0;i<rows.length;i++)line(batch,(i+1)+". "+rows[i].replace("\t","   ·   "),x,width,y+(i+2)*row+5,Color.WHITE);
                exitWidth=width/2;exitHeight=row;exitX=x+(width-exitWidth)/2;exitY=y+(rows.length+3)*row-5;
                MP_UI.box(batch,exitX,exitY,exitWidth,exitHeight,1);
                line(batch,t("Выйти","Exit"),exitX,exitWidth,exitY+4,Color.GOLD);
            }
        }finally{batch.setPackedColor(color);}
    }
    private static void line(SpriteBatch batch,String value,int x,int width,int y,Color color){
        String fit=MP_UI.fit(value,width-CFG.PADD*2);
        Renderer.drawTextWithShadow(batch,MP_UI.font(),fit,x+(width-MP_UI.textWidth(fit))/2,y,color);
    }
    public static boolean touchDown(int x,int y,int pointer,int button){
        if(!finished())return false;
        exitPressed=pointer==0&&button==0&&exitContains(x,y);
        return true;
    }
    public static boolean touchUp(int x,int y,int pointer,int button){
        if(!finished())return false;
        if(exitPressed&&pointer==0&&button==0&&exitContains(x,y)){
            MP.stop();
            MP_UI.open(View.eMAINMENU);
        }
        exitPressed=false;
        return true;
    }
    private static boolean exitContains(int x,int y){return x>=exitX&&y>=exitY&&x<exitX+exitWidth&&y<exitY+exitHeight;}
}
