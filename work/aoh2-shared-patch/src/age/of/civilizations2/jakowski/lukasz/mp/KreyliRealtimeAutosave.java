package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.CFG;
import age.of.civilizations2.jakowski.lukasz.NewTurn;
import age.of.civilizations2.jakowski.lukasz.Save.SaveGameManager;

/** Keep the campaign view while the native worker writes a complete save. */
public final class KreyliRealtimeAutosave {
    private KreyliRealtimeAutosave(){}

    public static void saveFromWorldUpdate(){
        if(!MP.isHost()||!MP.started||!KreyliLobby.realTime()){
            SaveGameManager.trySaveGame();
            return;
        }
        try{
            SaveGameManager.saveGame_0();
            SaveGameManager.saveGame_1();
            SaveGameManager.saveGame_2();
            for(int part=0;SaveGameManager.saveGame_3(part);part++){}
            SaveGameManager.saveGame_4();
            for(int part=0;SaveGameManager.saveGame_5(part);part++){}
            SaveGameManager.saveGame_6();
            SaveGameManager.saveGame_7();
            SaveGameManager.saveGame_8();
            SaveGameManager.saveGame_9();
            SaveGameManager.saveGame_10();
            SaveGameManager.saveGame_10_B();
            SaveGameManager.saveGame_11();
            SaveGameManager.saveGame_12();
            SaveGameManager.saveGame_13();
            SaveGameManager.saveGame_14();
            SaveGameManager.saveGame_15();
            SaveGameManager.saveGame_15_1();
            SaveGameManager.saveGame_15A();
            SaveGameManager.saveGame_15B();
            SaveGameManager.saveGame_15C();
            SaveGameManager.saveGame_15D();
            SaveGameManager.saveGame_15E();
            SaveGameManager.saveGame_16();
        }catch(RuntimeException error){
            CFG.exceptionStack(error);
        }finally{
            SaveGameManager.gameSaved=false;
            SaveGameManager.forceShowNextPlayerTurnView=false;
            NewTurn.doAction_End();
        }
    }
}