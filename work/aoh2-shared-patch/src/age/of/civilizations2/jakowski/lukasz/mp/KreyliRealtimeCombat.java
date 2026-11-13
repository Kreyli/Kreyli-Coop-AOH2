package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.CFG;
import age.of.civilizations2.jakowski.lukasz.Province;
import age.of.civilizations2.jakowski.lukasz.Report_Data;
import age.of.civilizations2.jakowski.lukasz.MoveUnitsB.MoveUnits;

/** Resolve a timed land attack using troop strength and home defense. */
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

    static long strength(long troops,boolean defendingHome){
        return troops*(defendingHome?110:100);
    }

    static int survivors(int troops,long winner,long loser){
        if(loser==0)return troops;
        return Math.max(1,troops-(int)(troops*(double)loser/winner*0.65));
    }

    static boolean resolve(int civ,int index){
        if(!MP.inputOrders())return false;
        var country=CFG.core.getCiv(civ);
        var move=country.getMoveUnits(index);
        var target=CFG.core.getProv(move.getToProvID());
        int defender=target.getCivId(), attackers=move.getNumberOfUnits();
        if(!hostile(civ,target,attackers))return false;
        var report=new Report_Data();
        report.iBattleOfProvinceID=move.getToProvID();
        long defense=0;
        for(int i=0;i<target.getCivsSize();i++){
            var army=target.getArmyObject(i);
            int armyCiv=army.getCivID(),troops=army.getArmy();
            if(troops<=0||armyCiv==civ||!CFG.core.getCivsAtWar(civ,armyCiv))continue;
            report.lDefenders_IDs.add(armyCiv);
            report.lDefenders_Armies.add(troops);
            defense+=strength(troops,armyCiv==target.getTrueOwnerOfProv());
        }
        long attack=strength(attackers,civ==target.getTrueOwnerOfProv());
        boolean won=attack>defense;
        int remaining=won?survivors(attackers,attack,defense):0;
        country.removeMove(index);
        for(int i=0;i<report.lDefenders_IDs.size();i++){
            int troops=report.lDefenders_Armies.get(i);
            int left=won?0:survivors(troops,defense,attack);
            target.updateArmy4(report.lDefenders_IDs.get(i),left);
            report.lDefenders_ArmiesLost.add(troops-left);
            casualties(civ,report.lDefenders_IDs.get(i),troops-left);
        }
        if(won){
            target.setCivId(civ,false,true);
            CFG.core.updateWarStatistics_ConqueredProvinces(CFG.core.getWarID(civ,defender),civ,defender);
            addTroops(target,civ,remaining);
        }
        target.updateDrawArmyInProv();
        report.lAttackers_IDs.add(civ);
        report.lAttackers_Armies.add(attackers);
        report.lAttackers_Armies_Lost.add(attackers-remaining);
        casualties(defender,civ,attackers-remaining);
        report.attackersWon=won;
        CFG.gameAction.battleReports.add(report);
        KreyliOrderSync.worldChanged();
        return true;
    }

    static int crossing(int civ,MoveUnits first,int other,MoveUnits second){
        int a=first.getNumberOfUnits(),b=second.getNumberOfUnits();
        // A meeting on the border is neutral ground. On an exact tie neither side advances.
        int leftA=a>=b?survivors(a,a,b):0,leftB=b>=a?survivors(b,b,a):0;
        if(a==b){
            addTroops(CFG.core.getProv(first.getFromProviID()),civ,leftA);
            addTroops(CFG.core.getProv(second.getFromProviID()),other,leftB);
        }else{
            var winner=a>b?first:second;
            int owner=a>b?civ:other;
            var country=CFG.core.getCiv(owner);
            country.newMove(winner.getFromProviID(),winner.getToProvID(),Math.max(leftA,leftB),true);
        }
        var report=new Report_Data();
        report.iBattleOfProvinceID=first.getToProvID();
        report.lAttackers_IDs.add(civ);
        report.lAttackers_Armies.add(a);
        report.lAttackers_Armies_Lost.add(a-leftA);
        report.lDefenders_IDs.add(other);
        report.lDefenders_Armies.add(b);
        report.lDefenders_ArmiesLost.add(b-leftB);
        report.attackersWon=a>b;
        casualties(other,civ,a-leftA);
        casualties(civ,other,b-leftB);
        CFG.gameAction.battleReports.add(report);
        KreyliOrderSync.worldChanged();
        return a==b?0:a>b?civ:other;
    }

    private static void casualties(int opponent,int civ,int losses){
        if(losses>0)CFG.core.updateWarStatistics_Casualties(CFG.core.getWarID(opponent,civ),opponent,civ,losses);
    }

    private static void addTroops(Province province,int civ,int troops){
        province.updateArmy4(civ,(int)Math.min(Integer.MAX_VALUE,(long)province.getArmyCivID1(civ)+troops));
        province.updateDrawArmyInProv();
    }
}
