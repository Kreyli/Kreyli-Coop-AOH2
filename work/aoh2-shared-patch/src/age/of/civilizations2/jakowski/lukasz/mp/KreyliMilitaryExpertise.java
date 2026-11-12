package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.CFG;
import age.of.civilizations2.jakowski.lukasz.Core.Core;
import age.of.civilizations2.jakowski.lukasz.Menus.ZRest.Exp.Menu_InGame_MilitaryExp;

/** Applies military upgrades on the host, using the same point and cap checks as solo play. */
public final class KreyliMilitaryExpertise {
    private static final String SPEND="kreyli_expertise_spend",STATE="kreyli_expertise_state";

    private KreyliMilitaryExpertise(){}

    public static boolean attack(Core core,int civ){return spend(core,civ,true);}
    public static boolean defense(Core core,int civ){return spend(core,civ,false);}

    private static boolean spend(Core core,int civ,boolean attack){
        if(!MP.playing())return apply(core,civ,attack);
        if(!MP.inputOrders()||KreyliRealtimePause.paused()||!MP.validCiv(civ))return false;
        if(MP.isClient()){
            if(civ!=MP.myCiv||MP.ready)return false;
            var host=host();
            if(host!=null)host.send(SPEND,attack);
            return false;
        }
        if(!MP.isHost()||core.getPlayer(0).getCivId()!=civ)return false;
        boolean changed=apply(core,civ,attack);
        if(changed)publish(civ);
        return changed;
    }

    private static boolean apply(Core core,int civ,boolean attack){
        return attack?core.addArmyExpertiseAttack(civ):core.addArmyExpertiseDefense(civ);
    }

    static boolean hostMessage(MP_Net.Conn connection,Object[] message){
        if(message.length==0||!SPEND.equals(message[0]))return false;
        if(message.length!=2||!(message[1] instanceof Boolean attack)||!MP.isHost()||!MP.started
                ||!MP.clients.contains(connection)||connection.civ<=0||!MP.validCiv(connection.civ)
                ||MP.eliminated(connection.civ))return true;
        int civ=connection.civ;
        if(MP.inputOrders()&&!connection.ready&&!KreyliRealtimePause.paused()&&apply(CFG.core,civ,attack)){
            publish(civ);
            refresh(civ);
        }else sendState(connection,civ);
        return true;
    }

    private static void publish(int civ){
        for(var connection:MP.clients)if(connection.civ==civ)sendState(connection,civ);
        KreyliOrderSync.worldChanged();
    }

    private static void sendState(MP_Net.Conn connection,int civ){
        var data=CFG.core.getCiv(civ).civGD;
        connection.send(STATE,civ,data.armyExpertiseLevel,data.armyExpertiseAttack,data.armyExpertiseDefense);
    }

    static boolean clientMessage(Object[] message){
        if(message.length==0||!STATE.equals(message[0]))return false;
        if(message.length==5&&MP.isClient()&&MP.playing()&&message[1] instanceof Integer civ
                &&civ>0&&civ==MP.myCiv&&MP.validCiv(civ)&&message[2] instanceof Integer level
                &&message[3] instanceof Integer attack&&message[4] instanceof Integer defense
                &&level>=0&&attack>=0&&defense>=0&&(long)attack+defense<=level){
            var data=CFG.core.getCiv(civ).civGD;
            data.armyExpertiseLevel=level;
            data.armyExpertiseAttack=attack;
            data.armyExpertiseDefense=defense;
            refresh(civ);
        }
        return true;
    }

    private static void refresh(int civ){
        if(CFG.menus==null||!CFG.menus.getInGameView())return;
        for(var menu:CFG.menus.getActiveMenu()){
            if(menu instanceof Menu_InGame_MilitaryExp&&menu.getVisibleM()){
                CFG.menus.rebuildInGame_MilitaryExp(civ);
                break;
            }
        }
        CFG.setRenderO(true);
    }

    private static MP_Net.Conn host(){
        try{
            var field=MP.class.getDeclaredField("host");
            field.setAccessible(true);
            return (MP_Net.Conn)field.get(null);
        }catch(ReflectiveOperationException error){
            throw new IllegalStateException("Pinned host connection is unavailable",error);
        }
    }
}