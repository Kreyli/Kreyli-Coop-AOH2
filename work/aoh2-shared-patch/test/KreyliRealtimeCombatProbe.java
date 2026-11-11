package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.CFG;
import age.of.civilizations2.jakowski.lukasz.GameCalendar;
import age.of.civilizations2.jakowski.lukasz.Menus.NewGame.Menu_CreateNewGame;
import age.of.civilizations2.jakowski.lukasz.MoveUnitsB.MoveUnits;
import aoc.kingdoms.lukasz.jakowski.AA_Game;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Exercises timed attack resolution in the pinned game on an isolated local host. */
public final class KreyliRealtimeCombatProbe {
    private static void require(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }

    public static void main(String[] args)throws Exception{
        require(Files.exists(Path.of(".coop-test-copy")),"Disposable test copy required");
        var remote=MP.class.getDeclaredField("remoteRequested");
        remote.setAccessible(true);
        remote.setBoolean(null,true);
        var starts=KreyliRealtimeMarch.class.getDeclaredField("starts");
        starts.setAccessible(true);
        var watchdog=new Thread(()->{
            try{Thread.sleep(120_000);}
            catch(InterruptedException ignored){return;}
            Runtime.getRuntime().halt(8);
        });
        watchdog.setDaemon(true);
        watchdog.start();

        var config=new Lwjgl3ApplicationConfiguration();
        config.setWindowedMode(1280,720);
        config.setInitialVisible(false);
        config.disableAudio(true);
        new Lwjgl3Application(new AA_Game(){
            int phase,attacker,defender,from,to;

            @Override public void render(){
                try{
                    super.render();
                    if(phase==0&&CFG.menus.getIn_MainMenu()){
                        findBorder();
                        MP.nick="RealtimeCombatHost";
                        MP.port=Integer.parseInt(args[0]);
                        MP.password="combat-probe";
                        MP.hostDirect=true;
                        MP.listInLobby=false;
                        MP.turnSeconds=0;
                        require(MP.startHost(),"Local host starts");
                        CFG.core.getPlayer(0).setCivId(attacker);
                        Menu_CreateNewGame.newGame();
                        phase=1;
                    }
                    if(phase==1&&MP.started&&MP.inputOrders()&&CFG.menus.getInGameView()){
                        checkAttack();
                        checkAiAttacks();
                        MP.stop();
                        System.out.println("KREYLI_REALTIME_COMBAT: PASS hostile marches resolve once and AI captures are staggered");
                        Runtime.getRuntime().halt(0);
                    }
                }catch(Throwable error){
                    error.printStackTrace();
                    Runtime.getRuntime().halt(3);
                }
            }

            private void findBorder(){
                for(int civ=1;civ<CFG.core.getCivsSize();civ++){
                    var country=CFG.core.getCiv(civ);
                    for(int i=0;i<country.getNumOfProvs();i++){
                        int origin=country.getProvID(i);
                        var province=CFG.core.getProv(origin);
                        if(province.getSeaProv())continue;
                        for(int j=0;j<province.getNeighProvincesSize();j++){
                            int adjacent=province.getNeighProvinces(j);
                            var target=CFG.core.getProv(adjacent);
                            int owner=target.getCivId();
                            if(owner<=0||owner==civ||target.getSeaProv()
                                    ||CFG.core.getCivsInAlliance(civ,owner)
                                    ||CFG.core.getMilitaryAccess(civ,owner)>0
                                    ||CFG.core.getCiv(owner).getPuppetOfCiv()==civ
                                    ||country.getPuppetOfCiv()==owner)continue;
                            attacker=civ;
                            defender=owner;
                            from=origin;
                            to=adjacent;
                            return;
                        }
                    }
                }
                throw new AssertionError("No two countries share a land border");
            }

            @SuppressWarnings("unchecked")
            private void checkAiAttacks()throws Exception{
                for(int civ=1;civ<CFG.core.getCivsSize();civ++){
                    if(civ==attacker)continue;
                    var country=CFG.core.getCiv(civ);
                    for(int i=0;i<country.getNumOfProvs();i++){
                        int from=country.getProvID(i);
                        var origin=CFG.core.getProv(from);
                        if(origin.getSeaProv())continue;
                        int first=-1,second=-1;
                        for(int j=0;j<origin.getNeighProvincesSize();j++){
                            int adjacent=origin.getNeighProvinces(j);
                            var target=CFG.core.getProv(adjacent);
                            int owner=target.getCivId();
                            if(owner<=0||owner==civ||target.getSeaProv()
                                    ||CFG.core.getCivsInAlliance(civ,owner)
                                    ||CFG.core.getMilitaryAccess(civ,owner)>0
                                    ||CFG.core.getCiv(owner).getPuppetOfCiv()==civ
                                    ||country.getPuppetOfCiv()==owner)continue;
                            if(first<0)first=adjacent;
                            else if(first!=adjacent){second=adjacent;break;}
                        }
                        if(second<0)continue;
                        int firstOwner=CFG.core.getProv(first).getCivId();
                        int secondOwner=CFG.core.getProv(second).getCivId();
                        if(!CFG.core.getCivsAtWar(civ,firstOwner))CFG.core.addWarData(civ,firstOwner);
                        if(!CFG.core.getCivsAtWar(civ,secondOwner))CFG.core.addWarData(civ,secondOwner);
                        int amount=Math.max(10_000,CFG.MIN_ARMY_REQUIRED_TO_ATTACK);
                        country.setMovementPoints(100);
                        origin.updateArmy4(civ,amount*2+10);
                        CFG.core.getProv(first).updateArmy4(firstOwner,1);
                        CFG.core.getProv(second).updateArmy4(secondOwner,1);
                        int count=country.moveUnitsSize(),turn=GameCalendar.TURNID;
                        require(CFG.gameAction.moveArmyAction(from,first,amount,civ,true,false),"Native AI order accepted");
                        require(CFG.gameAction.moveArmyAction(from,second,amount,civ,true,false),"Second native AI order accepted");
                        require(origin.getArmyCivID1(civ)==10,"Native AI reserves both forces immediately");
                        KreyliRealtimeMarch.finishAiOrders(CFG.oAI);
                        require(country.moveUnitsSize()==count,"Both AI orders detached before native refund");
                        require(origin.getArmyCivID1(civ)==10,"Detaching preserves the original reservation");
                        KreyliRealtimeMarch.afterTurn();
                        require(country.moveUnitsSize()==count+2,"Detached AI orders are restored");
                        var timings=(Map<MoveUnits,Long>)starts.get(null);
                        for(int order=count;order<country.moveUnitsSize();order++)
                            timings.put(country.getMoveUnits(order),System.nanoTime()-6_000_000_000L);
                        var nextCheck=KreyliRealtimeMarch.class.getDeclaredField("nextCheck");
                        nextCheck.setAccessible(true);
                        nextCheck.setLong(null,0);
                        KreyliRealtimeMarch.tick();
                        require(GameCalendar.TURNID==turn&&MP.inputOrders(),"AI attack preserves the input phase and turn");
                        require(CFG.core.getProv(first).getCivId()==civ&&CFG.core.getProv(second).getCivId()==secondOwner,
                                "Only the first AI attack arrives when both orders are due");
                        require(origin.getArmyCivID1(civ)==10,"AI attack does not double debit");
                        nextCheck.setLong(null,0);
                        KreyliRealtimeMarch.tick();
                        require(CFG.core.getProv(second).getCivId()==secondOwner&&country.moveUnitsSize()==count+1,
                                "AI cooldown leaves the second order queued");
                        return;
                    }
                }
                throw new AssertionError("No AI origin has two hostile land neighbors");
            }

            @SuppressWarnings("unchecked")
            private void checkAttack()throws IllegalAccessException{
                KreyliLobby.readMode(true);
                CFG.core.addWarData(attacker,defender);
                require(CFG.core.getCivsAtWar(attacker,defender),"Countries are at war");
                int turn=GameCalendar.TURNID;
                var origin=CFG.core.getProv(from);
                var target=CFG.core.getProv(to);
                var country=CFG.core.getCiv(attacker);
                int amount=Math.max(10_000,CFG.MIN_ARMY_REQUIRED_TO_ATTACK);
                origin.updateArmy4(attacker,amount+10);
                target.updateArmy4(defender,1);
                require(CFG.gameAction.moveArmyAction(from,to,amount,attacker,false,true),"Native attack order accepted");
                require(origin.getArmyCivID1(attacker)==10,"Native order reserves troops once");
                MoveUnits order=country.getMoveUnits(country.moveUnitsSize()-1);
                ((Map<MoveUnits,Long>)starts.get(null)).put(order,System.nanoTime()-6_000_000_000L);
                KreyliRealtimeMarch.tick();
                require(GameCalendar.TURNID==turn&&MP.inputOrders(),"Battle does not advance or switch phase");
                require(origin.getArmyCivID1(attacker)==10,"No second deduction at arrival");
                require(country.moveUnitsSize()==0,"Order consumed once");
                require(target.getCivId()==attacker&&target.getArmyCivID1(attacker)>0,"Battle captures the target province");
                require(CFG.gameAction.getCurrentMoveunits()==null,"Native battle scratch state cleared");
            }
        },config);
    }
}