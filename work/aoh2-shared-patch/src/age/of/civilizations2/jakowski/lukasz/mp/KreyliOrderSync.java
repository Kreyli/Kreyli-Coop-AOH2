package age.of.civilizations2.jakowski.lukasz.mp;
import age.of.civilizations2.jakowski.lukasz.GameCalendar;

/** Coalesced transient military orders; deliberately never reloads a save or moves a camera. */
public final class KreyliOrderSync {
 private static boolean dirty;
 private static long lastSent,sequence,received;
 private static int receivedTurn=-1;
 private static int sentTurn=-1;
 private static final java.util.Map<MP_Net.Conn,Integer> sentCountries=new java.util.WeakHashMap<>();
 private KreyliOrderSync(){}
 public static void mark(){if(MP.isHost()&&MP.playing())dirty=true;}
 public static void request(){
  if(!MP.isClient()||!MP.playing())return;
  try{var f=MP.class.getDeclaredField("host");f.setAccessible(true);var host=(MP_Net.Conn)f.get(null);if(host!=null)host.send("kreyli_orders_request");}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
 }
 public static boolean hostMessage(MP_Net.Conn connection,Object[] message){
  if(message.length==0||!"kreyli_orders_request".equals(message[0]))return false;
  if(message.length==1&&MP.isHost()&&MP.clients.contains(connection)&&connection.civ>0)mark();return true;
 }
 public static void reset(){dirty=false;lastSent=sequence=received=0;receivedTurn=sentTurn=-1;sentCountries.clear();}
 public static void tick(){
  if(!MP.isHost()||!MP.playing()||!MP.inputOrders())return;
  if(sentTurn!=GameCalendar.TURNID)dirty=true;
  for(var connection:MP.clients)if(connection.civ>0&&!java.util.Objects.equals(sentCountries.get(connection),connection.civ))dirty=true;
  if(!dirty)return;
  long now=System.nanoTime();if(now-lastSent<150_000_000L)return;
  dirty=false;lastSent=now;long id=++sequence;String state=SharedState.capture();
  sentTurn=GameCalendar.TURNID;
  String clock=KreyliLobby.realTime()?KreyliRealtimeMarch.clock():"";
  for(var connection:MP.clients)if(connection.civ>0){
   connection.send("kreyli_orders",GameCalendar.TURNID,id,state,connection.cmds);
   if(!clock.isEmpty())connection.send("kreyli_march_clock",GameCalendar.TURNID,id,clock);
   sentCountries.put(connection,connection.civ);
  }
 }
 public static boolean receive(Object[] message){
  if(message.length==0)return false;
  if("kreyli_march_clock".equals(message[0])){
   if(MP.isClient()&&MP.playing()&&KreyliLobby.realTime()&&message.length==4
    &&message[1] instanceof Integer turn&&turn==GameCalendar.TURNID&&turn==receivedTurn
    &&message[2] instanceof Long id&&id==received&&message[3] instanceof String clock)
    KreyliRealtimeMarch.applyClock(clock);
   return true;
  }
  if(!"kreyli_orders".equals(message[0]))return false;
  if(!MP.isClient()||!MP.playing()||!MP.inputOrders()||message.length!=5||
    !(message[1] instanceof Integer turn)||!(message[2] instanceof Long id)||!(message[3] instanceof String state)||
    !(message[4] instanceof Integer commands)||commands<sentCommands()||
    turn!=GameCalendar.TURNID||id<=0||state.length()>16_000_000)return true;
  if(receivedTurn==turn&&id<=received)return true;
  SharedState.restore(state);receivedTurn=turn;received=id;SharedSupport.ordersAcknowledged(commands);
  return true;
 }
 private static int sentCommands(){try{var f=MP.class.getDeclaredField("sentCmds");f.setAccessible(true);return f.getInt(null);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
}
