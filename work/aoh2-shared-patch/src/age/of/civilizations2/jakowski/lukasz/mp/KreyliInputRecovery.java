package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.*;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;

/** Releases map gestures whose mouse-up was lost during save restoration or focus changes. */
public final class KreyliInputRecovery {
    static long snapshotApplications;
    private KreyliInputRecovery(){}
    static boolean pointerDown(){
        return Gdx.input!=null&&(Gdx.input.isButtonPressed(Input.Buttons.LEFT)||
            Gdx.input.isButtonPressed(Input.Buttons.RIGHT)||Gdx.input.isButtonPressed(Input.Buttons.MIDDLE));
    }
    public static void tick(){
        if(CFG.map==null||CFG.menus==null||Gdx.input==null||pointerDown())return;
        if(CFG.map.getTouchMgr().getActionMap()||TouchManager.bSMD)release();
    }
    public static void afterSnapshot(){
        snapshotApplications++;
        if(CFG.map!=null&&CFG.menus!=null&&Gdx.input!=null&&!pointerDown())release();
    }
    private static void release(){
        Touch.resetAllModes();Touch.sAMD=false;TouchManager.bSMD=false;
        CFG.map.getTouchMgr().setUpdateStartMovePosX(true);
        CFG.map.getTouchMgr().setUpdateStartMovePosY(true);
    }
}
