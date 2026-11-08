package age.of.civilizations2.jakowski.lukasz.mp;
public final class SteamProbe {
    public static void main(String[] args)throws Exception{
        if(args.length==0){
            check(KreyliSteam.validRoom("AB12cd"));check(!KreyliSteam.validRoom("../../"));check(!KreyliSteam.validRoom("127.0.0.1"));check(!KreyliSteam.validRoom(null));
            check(KreyliSteam.validRelay("relay.example.test:8766"));check(!KreyliSteam.validRelay("host:0"));check(!KreyliSteam.validRelay("host:65536"));check(!KreyliSteam.validRelay("host/path:443"));
            check(KreyliSteam.parseLobbyArgument(new String[]{"+connect_lobby","123"})==123);
            check(KreyliSteam.parseLobbyArgument(new String[]{"+connect_lobby","-1"})==0);
            check(KreyliSteam.parseLobbyArgument(new String[]{"+connect_lobby","oops"})==0);
            check(KreyliSteam.parseLobbyArgument(new String[]{"+connect_lobby"})==0);
            System.setProperty("kreyli.steam.disabled","true");KreyliSteam.initialize(new String[0]);
            check(!KreyliSteam.available());KreyliSteam.tick();KreyliSteam.leaveSession();KreyliSteam.shutdown();
            System.out.println("PASS: room validation, launch argument validation, disabled-Steam lifecycle");return;
        }
        KreyliSteam.initialize(new String[0]);
        System.out.println("STEAM_SMOKE: available="+KreyliSteam.available()+" status="+KreyliSteam.status());
        if(KreyliSteam.available())for(int i=0;i<30;i++){KreyliSteam.tick();Thread.sleep(100);}
        System.out.println("STEAM_SMOKE: overlayEnabled="+KreyliSteam.overlayEnabled()+" (no graphics window in this smoke test)");
        KreyliSteam.shutdown();
    }
    static void check(boolean condition){if(!condition)throw new AssertionError();}
}
