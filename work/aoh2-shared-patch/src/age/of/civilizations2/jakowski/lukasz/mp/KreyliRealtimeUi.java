package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.CFG;
import age.of.civilizations2.jakowski.lukasz.GameCalendar;
import age.of.civilizations2.jakowski.lukasz.Menu;
import age.of.civilizations2.jakowski.lukasz.MenuManager;
import age.of.civilizations2.jakowski.lukasz.Menus.Messages.Menu_InGame_Messages;
import java.util.ArrayList;
import java.util.List;

/** Preserve the local inspection UI while the authoritative campaign advances. */
public final class KreyliRealtimeUi {
    private record Panel(int index, Class<?> type) {}
    private record State(int view,int country,boolean countryVisible,List<Panel> panels,boolean startAnimation,
                         boolean inAnimation,long animationTimer) {}
    private static State snapshot, hostTurn, automatic;
    private static int automaticStartTurn;
    public static void beforeAutomaticTurn(){
        automatic=capture();
        automaticStartTurn=GameCalendar.TURNID;
    }
    public static void finishAutomaticTurn(){
        if(automatic!=null&&MP.inputOrders()&&GameCalendar.TURNID!=automaticStartTurn){
            State state=automatic;
            automatic=null;
            restore(state);
        }
    }

    public static void hideTurnViews(age.of.civilizations2.jakowski.lukasz.GameAction action){
        if(!KreyliLobby.realTime()||!MP.playing())action.hideExtraViews();
    }

    private KreyliRealtimeUi() {}

    public static void beforeSnapshot(){snapshot=capture();}
    public static void afterSnapshot(){State state=snapshot;snapshot=null;restore(state);}
    public static void beforeHostTurn(){if(MP.isHost())hostTurn=capture();}
    public static void afterHostTurn(){if(MP.isHost()){State state=hostTurn;hostTurn=null;restore(state);}}

    private static State capture(){
        MenuManager manager=CFG.menus;
        if(!KreyliLobby.realTime()||manager==null||!manager.getInGameView())return null;
        int[] indices={manager.INGAME_CIV_INFO,manager.INGAME_CIV_INFO_STATS,
                manager.INGAME_CIV_DIPLOMACY,manager.INGAME_CIV_DIPLO_ORACTIONS,
                manager.INGAME_CIV_ACTIONS,manager.INGAME_CIV_OPINIONS,manager.INGAME_CIV_DECISIONS,
                manager.INGAME_OUTLINER,manager.INGAME_BUDGET,manager.INGAME_WORLD_ECONOMY,
                manager.INGAME_RANK,manager.INGAME_CONQURED_PROVINCES,manager.INGAME_VICOTORY_CONDITIONS,
                manager.INGAME_BUILDINGS_CONSTRUCTED,manager.INGAME_RECRUITED_ARMY,manager.INGAME_ARMY,
                manager.INGAME_TRIBUTE,manager.INGAME_TECHNOLOGY,manager.INGAME_WARS,
                manager.INGAME_WAR_DETAILS,manager.INGAME_WORLD_POPULATION,manager.INGAME_STATS,
                manager.INGAME_HISTORY,manager.INGAME_MAP_MODES,manager.INGAME_MP_PLAYERS,
                manager.INGAME_OPTIONS};
        List<Menu> menus=manager.getActiveMenu();
        var visible=new ArrayList<Panel>();
        for(int index:indices){
            if(index>=0&&index<menus.size()){
                Menu menu=menus.get(index);
                if(menu!=null&&menu.getVisibleM())visible.add(new Panel(index,menu.getClass()));
            }
        }
        return new State(manager.getViewID(),CFG.getActiveCivInfoId(),manager.getVisible_InGame_CivInfo(),visible,
                Menu_InGame_Messages.START_ANIMATION,Menu_InGame_Messages.IN_ANIMATION,
                Menu_InGame_Messages.ANIMATION_TIMER);
    }

    private static void restore(State state){
        MenuManager manager=CFG.menus;
        if(state==null||manager==null||!manager.getInGameView()||manager.getViewID()!=state.view())return;
        if(state.countryVisible()&&!manager.getVisible_InGame_CivInfo())manager.setVisible_InGame_CivInfo(true);
        if(state.country()>0&&MP.validCiv(state.country())&&CFG.getActiveCivInfoId()!=state.country()
                &&state.countryVisible()){
            CFG.setActiveCivInfoId(state.country());
            CFG.updateActiveCivilizationInfoInGame();
        }
        List<Menu> menus=manager.getActiveMenu();
        for(Panel panel:state.panels()){
            if(panel.index()>=menus.size())continue;
            Menu menu=menus.get(panel.index());
            if(menu!=null&&menu.getClass()==panel.type()&&!menu.getVisibleM())menu.setVisibleM(true);
        }
        Menu_InGame_Messages.START_ANIMATION=state.startAnimation();
        Menu_InGame_Messages.IN_ANIMATION=state.inAnimation();
        Menu_InGame_Messages.ANIMATION_TIMER=state.animationTimer();
    }

    public static void turnMessageAnimation(boolean requested){
        if(!KreyliLobby.realTime())Menu_InGame_Messages.START_ANIMATION=requested;
    }

    public static void reset(){snapshot=hostTurn=automatic=null;}
}
