package age.of.civilizations2.jakowski.lukasz.mp;
import age.of.civilizations2.jakowski.lukasz.*;
import aoc.kingdoms.lukasz.jakowski.AA_Game;
import com.badlogic.gdx.backends.lwjgl3.*;
import java.nio.file.*;
import java.util.*;
public final class KreyliBrandingProbe {
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  if(!Files.exists(Path.of(".coop-test-copy")))throw new IllegalStateException("Disposable copy required");
  System.setProperty("kreyli.steam.disabled","true");System.setProperty("kreyli.discovery.disabled","true");System.setProperty("kreyli.nick","Branding Test");
  check(MP.class.getField("MOD_VERSION").get(null).equals("1.0"),"Version");

  var filtered=MP.parseRegions("test.example:1234,Russia;test.example:1234,Europe;test.example:1234,Россия;test.example:1234,RU");
  check(filtered.size()==1&&filtered.get(0)[1].equals("Europe"),"Russia removed from parsed regions");
  var added=new ArrayList<age.of.civilizations2.jakowski.lukasz.Button.MenuElemUI>();

  var config=new Lwjgl3ApplicationConfiguration();config.setWindowedMode(1280,720);config.setInitialVisible(false);config.disableAudio(true);config.setForegroundFPS(30);
  var watchdog=new Thread(()->{try{Thread.sleep(60000);}catch(Exception ignored){}Thread.getAllStackTraces().forEach((t,s)->{System.out.println("PROBE_THREAD: "+t.getName());for(var f:s)System.out.println("  "+f);});try{Thread.sleep(120000);}catch(Exception ignored){}Runtime.getRuntime().halt(9);});watchdog.setDaemon(true);watchdog.start();
  new Lwjgl3Application(new AA_Game(){int frame;
   @Override public void render(){try{super.render();frame++;if(frame%100==0)System.out.println("PROBE_FRAME: "+frame);
    if(frame==20){check(CFG.gLGMP().equals(CFG.gLG()),"Loading author retains original game credit without mod suffix");KreyliUiProbe.capture("kreyli-brand-loading.png");if(args.length>0&&args[0].equals("loading-only")){System.out.println("KREYLI_LOADING: PASS original author credit preserved, upstream mod suffix removed, native screenshot");Runtime.getRuntime().halt(0);}}
    if(frame==300){MP_UI.open(View.eMAINMENU);}
    if(frame==330){check(MP_UI.addCredits(added,42)==42&&added.isEmpty(),"Old mod credits removed");
     Menu main=CFG.menus.getActiveMenu().get(0);
     check(!main.getMenuElem(1).getVisibleE(),"Main-menu server button removed");
     check(!main.getMenuElem(main.getMenuElemsSize()-1).getVisibleE(),"Upstream website button removed");
     KreyliUiProbe.capture("kreyli-brand-main.png");MP_UI.open(View.eMP);
    }
    if(frame==360){
     Menu entry=CFG.menus.getActiveMenu().get(0);boolean found=false;
     for(var item:entry.menuElem){if("Список серверов".equals(item.getTextE()))found=true;}
     check(found,"Server button in multiplayer");KreyliUiProbe.capture("kreyli-brand-multiplayer.png");MP_UI.open(View.eABOUT);
    }
    if(frame==390){KreyliUiProbe.capture("kreyli-brand-about.png");System.out.println("KREYLI_BRANDING: PASS version, region filtering, credits suppression, menu placement, native renders");Runtime.getRuntime().halt(0);}
   }catch(Throwable e){e.printStackTrace();Runtime.getRuntime().halt(3);}}
  },config);
 }
}

