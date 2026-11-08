package age.of.civilizations2.jakowski.lukasz.mp;
import java.util.*;
public class KreyliNoticeProbe {
 public static void main(String[] args)throws Exception{
  System.setProperty("kreyli.discovery.disabled","true");
  var config=MP.class.getDeclaredField("remoteConfig");config.setAccessible(true);config.set(null,new String[]{"test.example:1234,Europe","9.9","LeoC update announcement","https://ageofhistory.mp/"});
  var update=MP.class.getDeclaredMethod("updateRemoteConfig");update.setAccessible(true);update.invoke(null);
  var notices=MP.class.getDeclaredField("notices");notices.setAccessible(true);
  if(!((List<?>)notices.get(null)).isEmpty())throw new AssertionError("Upstream notices leaked");
  System.out.println("KREYLI_NOTICES: PASS injected old update/announcement ignored");
 }
}
