package age.of.civilizations2.jakowski.lukasz.mp;
import age.of.civilizations2.jakowski.lukasz.*;
import age.of.civilizations2.jakowski.lukasz.Save.SaveGameManager;
import age.of.civilizations2.jakowski.lukasz.Menus.Load.Menu_LoadSave;
import age.of.civilizations2.jakowski.lukasz.Menus.NewGame.Menu_CreateNewGame;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.*;
import aoc.kingdoms.lukasz.jakowski.AA_Game;
import java.nio.file.*;
import java.util.*;
import java.security.MessageDigest;

public final class KreyliSeatSecurityProbe {
 static void require(boolean value, String why) { if (!value) throw new AssertionError(why); }
 public static void main(String[] args) throws Exception {
  if (!Files.exists(Path.of(".coop-test-copy")) || !Boolean.getBoolean("kreyli.steam.disabled")) {
   throw new IllegalStateException("Disposable Steam-disabled test only");
  }
  boolean host = args[0].equals("host"); Path channel = Path.of(args[1]); int port = Integer.parseInt(args[2]);
  String mode = args.length > 3 ? args[3] : "reconnect";
  System.setProperty("kreyli.test.reconnect", "0123456789abcdef0123456789abcdef");
  var r = MP.class.getDeclaredField("remoteRequested"); r.setAccessible(true); r.setBoolean(null, true);
  var watch = new Thread(() -> { try { Thread.sleep(480000); } catch (InterruptedException e) { return; } Runtime.getRuntime().halt(8); });
  watch.setDaemon(true); watch.start();
  var config = new Lwjgl3ApplicationConfiguration(); config.setWindowedMode(1000, 700); config.setInitialVisible(false); config.disableAudio(true); config.setForegroundFPS(30);
  
  new Lwjgl3Application(new AA_Game() { int phase, frames, hostCiv, guestCiv, joinedFrame; String tag;
   void signal(String name, String value) throws Exception { Files.writeString(channel.resolve(name), value); }
   boolean exists(String name) { return Files.exists(channel.resolve(name)); }
   String read(String name) throws Exception { return Files.readString(channel.resolve(name)); }
   boolean playable() { return CFG.menus.getInGameView() && MP.inputOrders(); }
   void settings(String nick) {
    MP.nick = nick; KreyliIdentity.apply(); MP.password = "security-test"; MP.port = port; MP.turnSeconds = 0; MP.hostDirect = true; MP.listInLobby = false;
   }
   public void render() { try { super.render(); frames++;
    if (frames % 300 == 0) System.out.println("SECURITY " + (host ? "host" : "client") + " mode=" + mode + " phase=" + phase);
    if (phase == 0 && frames > 300 && CFG.core.getCivsSize() > 2 && (host || exists("host-ready"))) {
     if (host) {
      settings("SecHost");
      require(MP.startHost(), "Host start");
      MP_UI.open(View.eMP_HOST);
      KreyliLobby.toggleReady();
      phase = -1;
     } else {
      settings("Sec:Client");
      MP.address = "127.0.0.1:" + port; MP.join(); phase = 1;
     }
    }
    if (host) {
     if (phase == -1 && KreyliLobby.gate.released) {
      hostCiv = 1; while (!MP.pickable(hostCiv)) hostCiv++;
      CFG.core.getPlayer(0).setCivId(hostCiv);
      Menu_CreateNewGame.newGame(); phase = 1;
     }
     if (phase == 1 && MP.started && playable()) { signal("host-ready", Integer.toString(hostCiv)); phase = 2; }
     if (phase == 2 && exists("picked") && MP.clients.size() == 1 && MP.clients.get(0).civ > 0) {
      guestCiv = MP.clients.get(0).civ;
      MP_Save.saveToDisk(); tag = SaveGameManager.saveTag;
      signal("saved", tag); phase = 3;
     }
     if (phase == 3 && MP.clients.isEmpty()) {
      MP.stop(); settings("SecHost"); require(MP.startHost(), "Restart host");
      String[] saves = Gdx.files.local("saves/games/" + CFG.map.getFileActiveMapPath() + "Age_of_Civilizations").readString().split(";");
      int index = Arrays.asList(saves).indexOf(tag); require(index >= 0, "Save in list");
      Menu_LoadSave.loadNow(index);
      if (mode.equals("oldsave")) {
       try {
        String key = CFG.map.getActiveMapIDN() + ":" + SaveGameManager.saveTag;
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        Path seatFile = Path.of("saves", "kreyli-seats", digest + ".properties");
        Properties p = new Properties();
        Files.createDirectories(seatFile.getParent());
        if (Files.exists(seatFile)) {
         try (var in = Files.newInputStream(seatFile)) { p.load(in); }
        }
        List<String> toRemove = new ArrayList<>();
        for (String propName : p.stringPropertyNames()) {
         if (propName.startsWith("seat.") && !propName.equals("seat.host")) {
          toRemove.add(propName);
         }
        }
        for (String r : toRemove) { p.remove(r); }
        p.setProperty("seat.Sec:Client", Integer.toString(guestCiv));
        try (var out = Files.newOutputStream(seatFile)) { p.store(out, "Old save test"); }
       } catch (Exception e) { e.printStackTrace(); }
      }
      Menu_LoadSave.startLoadedGame("Security test"); phase = 4;
     }
     if (phase == 4 && MP.started && playable()) {
      require(CFG.core.getPlayer(0).getCivId() == hostCiv, "Host country was not restored");
      signal("reloaded", Integer.toString(guestCiv)); phase = 5;
     }
     if (phase == 5 && (mode.equals("reconnect") ? exists("reconnect-done") : (mode.equals("stranger") ? exists("stranger-done") : exists("oldsave-done")))) {
      MP.stop(); System.out.println("SEAT_SECURITY: PASS " + mode); Runtime.getRuntime().halt(0);
     }
    } else {
     if (phase == 1 && MP.picking && exists("host-ready")) {
      hostCiv = Integer.parseInt(read("host-ready")); guestCiv = 1; while (guestCiv == hostCiv || !MP.pickable(guestCiv)) guestCiv++;
      MP.pick(guestCiv); phase = 2;
     }
     if (phase == 2 && MP.myCiv == guestCiv && playable()) {
      signal("picked", Integer.toString(guestCiv));
      if (exists("saved")) {
       MP.stop();
       phase = 3;
      }
     }
     if (phase == 3 && exists("reloaded")) {
      settings(mode.equals("reconnect") ? "RenamedClient" : "Sec:Client");
      if (mode.equals("stranger")) {
       System.setProperty("kreyli.test.reconnect", "fedcba9876543210fedcba9876543210");
      }
      MP.address = "127.0.0.1:" + port; MP.join(); phase = 4;
     }
     if (phase == 4 && (playable() || MP.picking)) {
      if (mode.equals("reconnect")) {
       if (MP.myCiv == guestCiv) {
        signal("reconnect-done", "ok"); phase = 5;
       }
      } else {
       if (joinedFrame == 0) joinedFrame = frames;
       require(MP.myCiv != guestCiv, "Nickname improperly restored the saved country");
       if (MP.picking && frames >= joinedFrame + 45) {
        if (mode.equals("stranger")) {
         MP.pick(guestCiv);
         require(MP.pickPending(), "Manual pick was sent");
         phase = 6;
        } else {
         signal("oldsave-done", "ok"); phase = 5;
        }
       }
      }
     }
     if (phase == 6 && !MP.pickPending()) {
      require(MP.picking && MP.myCiv != guestCiv, "Stranger manually claimed the reserved country");
      signal("stranger-done", "ok"); phase = 5;
     }
    }
   } catch (Throwable e) { e.printStackTrace(); Runtime.getRuntime().halt(3); } }
  }, config);
 }
}
