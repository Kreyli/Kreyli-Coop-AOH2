package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.CFG;
import age.of.civilizations2.jakowski.lukasz.Menu;
import age.of.civilizations2.jakowski.lukasz.Button.NewGame.Button_InGameBox;
import age.of.civilizations2.jakowski.lukasz.Menus.Info.Menu_InGame_ProvInfo;

public final class KreyliBattleReportControls {
    private static long lastReportKey;
    private KreyliBattleReportControls(){}

    public static void addSkipAll(Menu menu){
        if(!MP.playing()||KreyliLobby.realTime()||!MP.hasBattleReports())return;
        int index=menu.getMenuElemsSize()-1;
        var close=menu.getMenuElem(index);
        int left=close.getPosXE(),width=close.getWidthE(),gap=CFG.PADD;
        int half=(width-gap)/2;
        close.setWidthE(half);
        var skip=new Button_InGameBox(KreyliText.t("Пропустить все","Skip all"),-1,
                left+half+gap,close.getPosY(),width-half-gap,close.getHeightE(),true){
            @Override public void actionElem(int ignored){
                SharedSupport.clearLocalReports();
                menu.setVisibleM(false);
                lastReportKey=System.nanoTime();
                CFG.setRenderO(true);
            }
        };
        menu.menuElem.add(index,skip);
        menu.iMenuElemsSize=menu.menuElem.size();
        menu.updateMenuElements_IsInView();
        menu.updateMenuElements_IsInView_X();
    }

    public static void onSpace(){
        if(!keyboardNext())Menu_InGame_ProvInfo.clickEndTurn();
    }
    static boolean keyboardNext(){
        if(MP.playing()&&!KreyliLobby.realTime()){
            long now=System.nanoTime();
            if(CFG.menus.getInGame_Report_Visible()||MP.hasBattleReports()){
                boolean visible=CFG.menus.getInGame_Report_Visible();
                CFG.menus.setInGame_Report_Visible(false);
                if(MP.hasBattleReports())MP.endTurnPressed();
                if(visible||CFG.menus.getInGame_Report_Visible())lastReportKey=now;
                return true;
            }
            if(lastReportKey!=0&&now-lastReportKey<750_000_000L){
                lastReportKey=now;
                return true;
            }
        }
        return false;
    }
}
