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
                        MP.stop();
                        System.out.println("KREYLI_REALTIME_COMBAT: PASS hostile march resolves once without advancing the turn");
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