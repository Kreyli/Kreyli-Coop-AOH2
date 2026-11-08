package age.of.civilizations2.jakowski.lukasz.mp;
import age.of.civilizations2.jakowski.lukasz.*;
import age.of.civilizations2.jakowski.lukasz.Save.SaveGameManager;
import java.nio.file.*;
import java.util.*;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Host-local save metadata. No arbitrary save names become filesystem paths. */
public final class KreyliSaveSeats {
 private static final Map<String,Integer> saved=new LinkedHashMap<>();
 private static Path file(){try{
  String key=CFG.map.getActiveMapIDN()+":"+SaveGameManager.saveTag;
  String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));
  return Path.of("saves","kreyli-seats",digest+".properties");
 }catch(Exception e){throw new IllegalStateException(e);}}
 public static void restoreConnected(){
  if(!MP.isHost()||!MP.started)return;
  try{var method=MP.class.getDeclaredMethod("assign",MP_Net.Conn.class,int.class);method.setAccessible(true);var free=MP.class.getDeclaredMethod("civFree",int.class,MP_Net.Conn.class);free.setAccessible(true);for(var c:MP.clients){Integer civ=saved.get(c.nick);if(civ!=null&&c.civ<=0&&Boolean.TRUE.equals(free.invoke(null,civ,c)))method.invoke(null,c,civ);}}catch(Exception e){throw new IllegalStateException(e);}
 }
 @SuppressWarnings("unchecked") public static void save(){
  if(!MP.isHost()||!MP.started||SaveGameManager.saveTag==null)return;
  Properties p=new Properties();p.setProperty("shared",Boolean.toString(SharedSupport.shared));
  p.setProperty("host",MP.nick);p.setProperty("hostCiv",Integer.toString(CFG.core.getPlayer(0).getCivId()));
  try{var f=MP.class.getDeclaredField("nickCiv");f.setAccessible(true);for(var entry:((Map<String,Integer>)f.get(null)).entrySet())if(entry.getKey()!=null&&entry.getValue()>0)p.setProperty("seat."+entry.getKey(),entry.getValue().toString());}catch(Exception e){throw new IllegalStateException(e);}
  for(var c:MP.clients)if(c.nick!=null&&c.civ>0)p.setProperty("seat."+c.nick,Integer.toString(c.civ));
  try{Path f=file();Files.createDirectories(f.getParent());Path temp=f.resolveSibling(f.getFileName()+".tmp");try(var out=Files.newOutputStream(temp)){p.store(out,"Kreyli save countries");}Files.move(temp,f,StandardCopyOption.REPLACE_EXISTING);}catch(Exception e){System.err.println("Kreyli seat metadata save: "+e.getClass().getSimpleName());}
 }
 /** Before native onEnterInGame, which subsequently builds JOIN snapshots. */
 @SuppressWarnings("unchecked") public static void prepare(){
  if(!MP.isHost()||MP.started||SaveGameManager.saveTag==null)return;
  saved.clear();Properties p=new Properties();try{Path f=file();if(!Files.exists(f))return;try(var in=Files.newInputStream(f)){p.load(in);}
   int host=Integer.parseInt(p.getProperty("hostCiv","0"));if(MP.pickable(host)){CFG.core.getPlayer(0).setCivId(host);SharedSupport.shared=Boolean.parseBoolean(p.getProperty("shared","false"));}
   for(String key:p.stringPropertyNames())if(key.startsWith("seat.")){int civ=Integer.parseInt(p.getProperty(key));if(MP.pickable(civ))saved.put(key.substring(5),civ);}
   var field=MP.class.getDeclaredField("nickCiv");field.setAccessible(true);((Map<String,Integer>)field.get(null)).putAll(saved);
  }catch(Exception e){System.err.println("Kreyli seat metadata load: "+e.getClass().getSimpleName());}
 }
}




