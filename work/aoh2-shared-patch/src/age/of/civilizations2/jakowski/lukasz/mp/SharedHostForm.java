package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.*;
import com.badlogic.gdx.Gdx;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public final class SharedHostForm {
    static void openRoom(){
        MP.password="";
        MP.turnSeconds=KreyliTurns.waitSetting();
        if(MP.startHost())MP_UI.open(View.eMP_HOST);
    }

    public static Menu create(){return KreyliLobby.room();}

    static void apply(String password,int seconds,boolean listed,
                              boolean direct,int port,String region){
        if(!MP.isHost()||MP.started)return;
        boolean reconnect=direct!=MP.hostDirect||port!=MP.port||!region.equals(MP.selectedRegion()[1])
                ||(direct&&listed&&!MP.listInLobby);
        if(reconnect&&!MP.clients.isEmpty()){
            MP.toast("Для смены подключения, порта или региона гость должен выйти из комнаты",false);
            return;
        }
        boolean oldDirect=MP.hostDirect, oldListed=MP.listInLobby;
        int oldPort=MP.port,oldSeconds=MP.turnSeconds;
        String oldRegion=MP.regionName,oldPassword=MP.password;
        boolean changed=reconnect||seconds!=oldSeconds||listed!=oldListed||!password.equals(oldPassword);
        MP.hostDirect=direct;MP.port=port;MP.regionName=region;MP.password=password;
        MP.turnSeconds=seconds;MP.listInLobby=listed;
        if(reconnect&&!MP.startHost()){
            MP.hostDirect=oldDirect;MP.port=oldPort;MP.regionName=oldRegion;MP.password=oldPassword;
            MP.turnSeconds=oldSeconds;MP.listInLobby=oldListed;
            MP.startHost();
        }
        if(MP.isHost()){
            KreyliTurns.saveWaitSetting(MP.turnSeconds);
            MP.saveSettings();
            if(changed)KreyliLobby.settingsChanged();
        }
        if(reconnect)MP_UI.open(MP.isHost()?View.eMP_HOST:View.eMP);
    }

    /** Direct LAN play starts immediately while optional internet listing is negotiated. */
    public static Socket registerDirect(String host,int relayPort,int port,ServerSocket listener){
        MP_Net.daemon(()->{
            Socket connection=new Socket();
            try{
                connection.connect(new InetSocketAddress(host,relayPort),8000);
                connection.setSoTimeout(16000);
                MP_Net.writeLine(connection.getOutputStream(),"R "+port);
                if(!"OK".equals(MP_Net.readLine(connection.getInputStream())))
                    throw new IOException(KreyliText.t("порт недоступен из интернета","port is not reachable from the internet"));
                connection.setSoTimeout(0);
                Gdx.app.postRunnable(()->{
                    if(!listener.isClosed()&&MP.isHost()&&MP.hostDirect&&MP.port==port){
                        try{DirectListing.control.set(null,connection);return;}
                        catch(IllegalAccessException error){throw new IllegalStateException(error);}
                    }
                    closeListing(connection);
                });
            }catch(IOException|RuntimeException error){
                closeListing(connection);
                Gdx.app.postRunnable(()->{
                    if(!listener.isClosed()&&MP.isHost()&&MP.hostDirect&&MP.listInLobby)
                        MP.toast(MP.t("MP_NotListed","Not listed in the server browser: {0}",error.getMessage()),false);
                });
            }
        },"Kreyli-LAN-listing").start();
        return null; // Native startHost stores this until the worker publishes the socket.
    }
    private static void closeListing(Socket connection){
        try{connection.close();}catch(IOException ignored){}
    }
    private static final class DirectListing {
        static final Field control;
        static {
            try{
                control=MP.class.getDeclaredField("relayControl");
                control.setAccessible(true);
            }catch(ReflectiveOperationException error){throw new ExceptionInInitializerError(error);}
        }
    }
}

