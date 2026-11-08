package age.of.civilizations2.jakowski.lukasz.mp;
import age.of.civilizations2.jakowski.lukasz.*;
import aoc.kingdoms.lukasz.jakowski.AA_Game;
import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.ScreenUtils;
import java.nio.file.*;
import java.nio.file.Files;
public class VendorUiProbe {
 public static void main(String[] args)throws Exception{
  if(!Files.exists(Path.of(".coop-test-copy")))throw new IllegalStateException("Test copy required");
  var field=MP.class.getDeclaredField("remoteRequested");field.setAccessible(true);field.setBoolean(null,true);
  var config=new Lwjgl3ApplicationConfiguration();config.setWindowedMode(1280,720);config.setInitialVisible(false);config.disableAudio(true);config.setForegroundFPS(30);
  Thread timer=new Thread(()->{try{Thread.sleep(150000);}catch(Exception e){}Runtime.getRuntime().halt(2);});timer.setDaemon(true);timer.start();
  new Lwjgl3Application(new AA_Game(){int frame;public void render(){try{
   super.render();frame++;
   if(frame==300){MP.listInLobby=false;MP.hostDirect=true;SharedSupport.shared=true;MP_UI.open(View.eMP_HOST);}
   if(frame==330||frame==360){var pixmap=ScreenUtils.getFrameBufferPixmap(0,0,Gdx.graphics.getBackBufferWidth(),Gdx.graphics.getBackBufferHeight());PixmapIO.writePNG(Gdx.files.local(frame==330?"shared-host-form.png":"separate-host-form.png"),pixmap,6,true);pixmap.dispose();SharedSupport.shared=false;}
   if(frame==361){System.out.println("VENDOR_UI: PASS two form states rendered");Runtime.getRuntime().halt(0);}
  }catch(Throwable t){t.printStackTrace();Runtime.getRuntime().halt(3);}}},config);
 }
}
