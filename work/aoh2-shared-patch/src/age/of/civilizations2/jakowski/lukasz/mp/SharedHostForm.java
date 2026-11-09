package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.*;

public final class SharedHostForm {
    static void openRoom(){
        MP.password="";
        MP.turnSeconds=KreyliTurns.waitSetting();
        if(MP.startHost())MP_UI.open(View.eMP_HOST);
    }

    public static Menu create(){return KreyliLobby.room();}

    static void apply(boolean shared,String password,int seconds,boolean listed,
                              boolean direct,int port,String region){
        if(!MP.isHost()||MP.started)return;
        boolean reconnect=direct!=MP.hostDirect||port!=MP.port||!region.equals(MP.selectedRegion()[1])
                ||(direct&&listed&&!MP.listInLobby);
        if(reconnect&&!MP.clients.isEmpty()){
            MP.toast("Для смены подключения, порта или региона гость должен выйти из комнаты",false);
            return;
        }
        boolean oldDirect=MP.hostDirect, oldListed=MP.listInLobby, oldShared=SharedSupport.shared;
        int oldPort=MP.port,oldSeconds=MP.turnSeconds;
        String oldRegion=MP.regionName,oldPassword=MP.password;
        boolean changed=reconnect||shared!=oldShared||seconds!=oldSeconds||listed!=oldListed||!password.equals(oldPassword);
        MP.hostDirect=direct;MP.port=port;MP.regionName=region;MP.password=password;
        MP.turnSeconds=seconds;MP.listInLobby=listed;SharedSupport.shared=shared;
        if(reconnect&&!MP.startHost()){
            MP.hostDirect=oldDirect;MP.port=oldPort;MP.regionName=oldRegion;MP.password=oldPassword;
            MP.turnSeconds=oldSeconds;MP.listInLobby=oldListed;SharedSupport.shared=oldShared;
            MP.startHost();
        }
        if(MP.isHost()){
            KreyliTurns.saveWaitSetting(MP.turnSeconds);
            MP.saveSettings();
            if(changed)KreyliLobby.settingsChanged();
        }
        MP_UI.open(MP.isHost()?View.eMP_HOST:View.eMP);
    }

}

