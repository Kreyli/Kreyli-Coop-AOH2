package age.of.civilizations2.jakowski.lukasz.mp;
import age.of.civilizations2.jakowski.lukasz.*;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import java.util.*;
/** Ephemeral allied-only map annotations, routed and authorized by the host. */
public final class KreyliPings {
 record Ping(int civ,boolean fromHost,int x,int y,long until){}
 static final ArrayDeque<Ping> marks=new ArrayDeque<>();
 static int localCiv(){return MP.isHost()?CFG.core.getPlayer(0).getCivId():MP.myCiv;}
 static boolean allied(int a,int b){return MP.validCiv(a)&&MP.validCiv(b)&&(a==b||CFG.core.getCiv(a).getAlliance()>0&&CFG.core.getCiv(a).getAlliance()==CFG.core.getCiv(b).getAlliance());}
 public static void tick(){
  if(!MP.playing()||!CFG.menus.getInGameView()||KreyliSteam.overlayVisible())return;
  if(!Gdx.input.isKeyJustPressed(Input.Keys.ALT_LEFT)||CFG.menus.getActiveMenuElemeID()>=0)return;
  placeAt(Gdx.input.getX(),Gdx.input.getY());
 }
 static void placeAt(int x,int y){
  float scale=CFG.map.getMpS().getCurrSc();
  int wx=Math.floorMod((int)(x/scale)-CFG.map.getMpC().getPX(),CFG.map.getMpB().getWidthM()),wy=(int)(y/scale)-CFG.map.getMpC().getPY();
  if(MP.isHost())route(localCiv(),true,wx,wy);else try{var f=MP.class.getDeclaredField("host");f.setAccessible(true);var host=(MP_Net.Conn)f.get(null);if(host!=null)host.send("kreyli_ping_request",wx,wy);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
 }
 static boolean coords(Object[] m){return m.length==3&&m[1] instanceof Integer x&&m[2] instanceof Integer y&&x>=0&&x<CFG.map.getMpB().getWidthM()&&y>=0&&y<10_000_000;}
 static void route(int civ,boolean fromHost,int x,int y){
  if(allied(civ,localCiv()))add(civ,fromHost,x,y);
  for(var c:MP.clients)if(allied(civ,c.civ))c.send("kreyli_ping",civ,x,y,fromHost);
 }
 static void add(int civ,boolean fromHost,int x,int y){
  marks.removeIf(mark->mark.fromHost()==fromHost);
  marks.addLast(new Ping(civ,fromHost,x,y,System.nanoTime()+8_000_000_000L));
 }
 public static boolean hostMessage(MP_Net.Conn c,Object[] m){if(m.length==0||!"kreyli_ping_request".equals(m[0]))return false;if(MP.isHost()&&MP.playing()&&MP.clients.contains(c)&&MP.validCiv(c.civ)&&coords(m))route(c.civ,false,(Integer)m[1],(Integer)m[2]);return true;}
 public static boolean clientMessage(Object[] m){if(m.length==0||!"kreyli_ping".equals(m[0]))return false;if(MP.isClient()&&MP.playing()&&(m.length==4||m.length==5)&&m[1] instanceof Integer civ&&m[2] instanceof Integer x&&m[3] instanceof Integer y&&allied(civ,localCiv())&&coords(new Object[]{"",x,y}))add(civ,m.length==4||Boolean.TRUE.equals(m[4]),x,y);return true;}
 public static void reset(){marks.clear();}
 public static void draw(SpriteBatch batch){if(!MP.playing()||!CFG.menus.getInGameView())return;long now=System.nanoTime();marks.removeIf(p->p.until<now);float old=batch.getPackedColor(),s=CFG.map.getMpS().getCurrSc();int width=CFG.map.getMpB().getWidthM();for(var p:marks){if(!allied(p.civ,localCiv()))continue;for(int wrap=-1;wrap<=1;wrap++){int x=Math.round((p.x+wrap*width+CFG.map.getMpC().getPX())*s),y=Math.round((p.y+CFG.map.getMpC().getPY())*s);if(x<0||y<0||x>CFG.GAMEWIDTH||y>CFG.GAMEHEIGHT)continue;batch.setColor(Color.GOLD);Images.pix.draw(batch,x-12,y-2,24,4);Images.pix.draw(batch,x-2,y-12,4,24);CFG.drawTextDefaultWithShadow(batch,MP.civName(p.civ),x+16,y,Color.GOLD);}}batch.setPackedColor(old);}
}


