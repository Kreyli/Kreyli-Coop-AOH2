package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.CFG;
import age.of.civilizations2.jakowski.lukasz.GameCalendar;
import age.of.civilizations2.jakowski.lukasz.Menu;
import age.of.civilizations2.jakowski.lukasz.MenuManager;
import age.of.civilizations2.jakowski.lukasz.Menus.Messages.Menu_InGame_Messages;
import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import age.of.civilizations2.jakowski.lukasz.Core.Core;
import age.of.civilizations2.jakowski.lukasz.Province_Animation_MoveUnits2;

/** Preserve the local inspection UI while the authoritative campaign advances. */
public final class KreyliRealtimeUi {
    private record Panel(int index, Class<?> type) {}
    private record State(int view,int province,int country,boolean countryVisible,boolean choosingProvince,int moveTarget,int moveAmount,List<Panel> panels,boolean startAnimation,
                         boolean inAnimation,long animationTimer,Highlight highlight) {}
    /** Animation is local presentation, never host campaign state. */
    private record Highlight(float percentage,long borderTime,boolean backwards,boolean updating,
                             long cityTime,boolean citiesFading,long lineTime,int lineOffset) {}
    private static final Field BORDER_TIME=field(Core.class,"lDashedLineTime_Percentage_HighlitedProvinceBorder"),
            BACKWARDS=field(Core.class,"highlightedProvinceBorder_BackAnimation"),
            UPDATING=field(Core.class,"highlightedProvinceBorder_Update"),
            CITY_TIME=field(Core.class,"lTIME_HIGHLIGHTED_CITIES"),
            CITIES_FADING=field(Core.class,"HIGHLIGHTED_CITIES_DISABLE_ANIMATION"),
            LINE_TIME=field(Province_Animation_MoveUnits2.class,"lTimeLine"),
            LINE_OFFSET=field(Province_Animation_MoveUnits2.class,"iLineOffset");
    private static final Method CLASSIC=method(Core.class,"enableHighlightedProvinces_Classic");
    private static Field field(Class<?> owner,String name){try{Field f=owner.getDeclaredField(name);f.setAccessible(true);return f;}catch(ReflectiveOperationException e){throw new ExceptionInInitializerError(e);}}
    private static Method method(Class<?> owner,String name){try{Method m=owner.getDeclaredMethod(name);m.setAccessible(true);return m;}catch(ReflectiveOperationException e){throw new ExceptionInInitializerError(e);}}
    private static Highlight captureHighlight(Core core){
        try{var animation=core.getProvinceAnimation_Highlighted_Data();
            return new Highlight(core.fDashedLine_Percentage_HighlitedProvinceBorder,BORDER_TIME.getLong(core),BACKWARDS.getBoolean(core),UPDATING.getBoolean(core),CITY_TIME.getLong(core),CITIES_FADING.getBoolean(core),LINE_TIME.getLong(animation),LINE_OFFSET.getInt(animation));
        }catch(IllegalAccessException e){throw new IllegalStateException(e);}
    }
    private static void restoreHighlight(Core core,Highlight state){
        if(state==null)return;
        try{var animation=core.getProvinceAnimation_Highlighted_Data();
            core.fDashedLine_Percentage_HighlitedProvinceBorder=state.percentage();
            BORDER_TIME.setLong(core,state.borderTime());BACKWARDS.setBoolean(core,state.backwards());UPDATING.setBoolean(core,state.updating());
            CITY_TIME.setLong(core,state.cityTime());CITIES_FADING.setBoolean(core,state.citiesFading());
            LINE_TIME.setLong(animation,state.lineTime());LINE_OFFSET.setInt(animation,state.lineOffset());
            if(!state.updating()&&!state.backwards()&&state.percentage()>=100)CLASSIC.invoke(core);
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    public static void refreshMovementHighlights(Core core,int province){
        Highlight phase=backgroundUpdate()&&CFG.chooseProvinceMode&&core.getActiveProvID()==province?captureHighlight(core):null;
        // Recalculate valid neighbours after borders/diplomacy change, retaining only visual phase.
        core.updateHighlight_MoveUnitsProvinces(province);
        restoreHighlight(core,phase);
    }
    private static State snapshot, hostTurn, clientTurn, automatic;
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

    public static void showTurnMessages(MenuManager manager,boolean visible){
        if(visible||!KreyliLobby.realTime()||!MP.playing())manager.setVisibleInGame_Messages(visible);
    }

    public static boolean holdProvinceActions(boolean visible){
        return !visible&&KreyliLobby.realTime()&&MP.playing()&&!MP.inputOrders();
    }

    public static boolean backgroundUpdate(){
        return KreyliLobby.realTime()&&(automatic!=null||hostTurn!=null||snapshot!=null||clientTurn!=null);
    }

    public static void resetTurnSelection(age.of.civilizations2.jakowski.lukasz.Core.Core core){
        if(!backgroundUpdate())core.resetChooseProvinceData();
    }

    public static void showTurnView(MenuManager manager,age.of.civilizations2.jakowski.lukasz.View view){
        if(!backgroundUpdate())manager.setMenuIDWithoutAnim(view);
    }

    public static void updateTurnActions(MenuManager manager,int civ){
        if(!backgroundUpdate())manager.updateInGame_Top_All_NextTurnActions(civ);
    }

    private KreyliRealtimeUi() {}

    public static void resumeMarches(){KreyliRealtimeMarch.afterTurn();}
    static void beforeRemoteAdvance(){clientTurn=capture();}
    public static void beforeSnapshot(){
        snapshot=clientTurn!=null?clientTurn:capture();
        clientTurn=null;
    }
    public static void applySnapshotProvince(age.of.civilizations2.jakowski.lukasz.Core.Core core,int nativeProvince){
        int province=snapshot==null?nativeProvince:snapshot.province();
        if(province< -1||province>=core.getProvinSize())province=nativeProvince;
        if(snapshot==null||core.getActiveProvID()!=province)core.setActiveProvID(province);
    }
    public static void showAutomaticSave(MenuManager manager){
        if(automatic==null&&hostTurn==null&&snapshot==null)manager.rebuildMenu_InGame_SavedGame();
    }
    public static void afterSnapshot(){State state=snapshot;snapshot=null;restore(state);}
    public static void beforeHostTurn(){if(MP.isHost()){hostTurn=capture();KreyliRealtimeMarch.beforeTurn();}}
    public static void afterHostTurn(){if(MP.isHost()){KreyliRealtimeMarch.afterTurn();State state=hostTurn;hostTurn=null;restore(state);}}

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
                manager.INGAME_OPTIONS,manager.INGAME_PROVINCE_MORE,manager.INGAME_MESSAGE,
                manager.INGAME_ALLIANCE,manager.INGAME_MILITARY_ALLIANCES,manager.INGAME_HRE};
        List<Menu> menus=manager.getActiveMenu();
        var visible=new ArrayList<Panel>();
        for(int index:indices){
            if(index>=0&&index<menus.size()){
                Menu menu=menus.get(index);
                if(menu!=null&&menu.getVisibleM())visible.add(new Panel(index,menu.getClass()));
            }
        }
        boolean moving=manager.getInGame_ProvinceMoveUnits_Visible();
        int target=moving?CFG.chosenProvinceID:-1;
        int amount=moving?manager.getInGame_ProvinceMoveUnits_Slider().getCurr():0;
        return new State(manager.getViewID(),CFG.core.getActiveProvID(),CFG.getActiveCivInfoId(),manager.getVisible_InGame_CivInfo(),CFG.chooseProvinceMode,target,amount,visible,
                Menu_InGame_Messages.START_ANIMATION,Menu_InGame_Messages.IN_ANIMATION,
                Menu_InGame_Messages.ANIMATION_TIMER,CFG.chooseProvinceMode?captureHighlight(CFG.core):null);
    }

    private static void restore(State state){
        MenuManager manager=CFG.menus;
        if(state==null||manager==null||!manager.getInGameView()||manager.getViewID()!=state.view())return;
        int province=state.province();
        if(province>=-1&&province<CFG.core.getProvinSize()&&CFG.core.getActiveProvID()!=province){
            CFG.core.setActiveProvID(province);
            CFG.gameAction.updateInGame_ProvinceInfo();
        }
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
        if(state.choosingProvince()&&province>=0&&CFG.gameAction.controlsArmyInProvince(province)){
            int target=state.moveTarget();
            boolean restoreMove=target>=0&&target<CFG.core.getProvinSize()
                    &&(!manager.getInGame_ProvinceMoveUnits_Visible()||CFG.chosenProvinceID!=target);
            if(!CFG.chooseProvinceMode){
                CFG.core.chooseProvinceMode();
            }
            if(restoreMove){
                    CFG.chosenProvinceID=-1;
                    CFG.core.setChosenProvinceID(target);
                    var slider=manager.getInGame_ProvinceMoveUnits_Slider();
                    slider.setCurr(Math.min(state.moveAmount(),slider.getCurr()));
                    manager.updateInGame_ActionInfo_Move();
            }
            restoreHighlight(CFG.core,state.highlight());
        }
        Menu_InGame_Messages.START_ANIMATION=state.startAnimation();
        Menu_InGame_Messages.IN_ANIMATION=state.inAnimation();
        Menu_InGame_Messages.ANIMATION_TIMER=state.animationTimer();
    }

    public static void turnMessageAnimation(boolean requested){
        if(!KreyliLobby.realTime())Menu_InGame_Messages.START_ANIMATION=requested;
    }

    public static void reset(){snapshot=hostTurn=clientTurn=automatic=null;KreyliRealtimeMarch.reset();KreyliRealtimeMessages.reset();}
}
