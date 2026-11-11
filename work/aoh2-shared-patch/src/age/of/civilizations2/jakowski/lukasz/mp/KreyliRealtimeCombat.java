package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.CFG;
import age.of.civilizations2.jakowski.lukasz.GameAction;
import age.of.civilizations2.jakowski.lukasz.MoveUnitsB.MoveUnits_TurnData;
import age.of.civilizations2.jakowski.lukasz.Province;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Resolve a timed land attack with the campaign's existing combat rules. */
final class KreyliRealtimeCombat {
    private KreyliRealtimeCombat(){}

    static boolean hostile(int civ,Province target,int troops){
        int defender=target.getCivId();
        return defender>0&&defender!=civ&&troops>=CFG.MIN_ARMY_REQUIRED_TO_ATTACK
                &&CFG.core.getCivsAtWar(civ,defender)
                &&!CFG.core.getCivsInAlliance(civ,defender)
                &&CFG.core.getMilitaryAccess(civ,defender)<=0
                &&CFG.core.getCiv(civ).getPuppetOfCiv()!=defender
                &&CFG.core.getCiv(defender).getPuppetOfCiv()!=civ;
    }

    static boolean resolve(int civ,int index){
        GameAction action=CFG.gameAction;
        if(action.getCurrentMoveunits()!=null||!MP.inputOrders())return false;
        var country=CFG.core.getCiv(civ);
        var move=country.getMoveUnits(index);
        if(!hostile(civ,CFG.core.getProv(move.getToProvID()),move.getNumberOfUnits()))return false;
        var origin=CFG.core.getProv(move.getFromProviID());
        int garrison=origin.getArmyCivID1(civ),amount=move.getNumberOfUnits();
        if(garrison>Integer.MAX_VALUE-amount)return false;
        var battle=new MoveUnits_TurnData(civ);
        battle.addMoveUnits(move,civ);
        var report=action.battleReportSave;
        boolean showReport=action.SHOW_REPORT,saveReport=action.SAVE_REPORT;
        int attackerDice=action.diceAggressors,defenderDice=action.diceDefenders;
        boolean consumed=false;
        try{
            Native.current.set(action,battle);
            Native.roll.invoke(action);
            // The order reserved these units, but the native battle removes them from the source itself.
            origin.updateArmy4(civ,garrison+amount);
            country.removeMove(index);
            consumed=true;
            action.SHOW_REPORT=false;
            action.SAVE_REPORT=true;
            Native.resolve.invoke(action);
        }catch(IllegalAccessException e){
            throw new IllegalStateException("Pinned combat callback is inaccessible",e);
        }catch(InvocationTargetException e){
            // Native combat may have changed the world before failing. Never replay this order.
            CFG.exceptionStack(e.getCause());
        }finally{
            action.resetCurrentMoveUnits();
            action.SHOW_REPORT=showReport;
            action.SAVE_REPORT=saveReport;
            action.battleReportSave=report;
            action.diceAggressors=attackerDice;
            action.diceDefenders=defenderDice;
        }
        if(consumed)KreyliOrderSync.worldChanged();
        return consumed;
    }

    private static final class Native {
        static final Field current;
        static final Method resolve,roll;
        static {
            try{
                current=GameAction.class.getDeclaredField("currentMoveUnits");
                resolve=GameAction.class.getDeclaredMethod("turnMoves_MoveCurrentArmy");
                roll=GameAction.class.getDeclaredMethod("rollDices");
                current.setAccessible(true);
                resolve.setAccessible(true);
                roll.setAccessible(true);
            }catch(ReflectiveOperationException e){
                throw new ExceptionInInitializerError(e);
            }
        }
    }
}