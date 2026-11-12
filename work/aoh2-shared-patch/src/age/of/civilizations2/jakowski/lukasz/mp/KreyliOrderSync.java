package age.of.civilizations2.jakowski.lukasz.mp;
import age.of.civilizations2.jakowski.lukasz.GameCalendar;

/** Lightweight military orders and coalesced snapshots for changes to the world. */
public final class KreyliOrderSync {
 private static final long WORLD_INTERVAL_NANOS=1_250_000_000L;
 private static boolean dirty, worldDirty;
 private static long lastWorldSync;
 private static long lastSent,sequence,received;
 private static int receivedTurn=-1;
 private static int sentTurn=-1;
 private static final java.util.Map<MP_Net.Conn,Integer> sentCountries=new java.util.WeakHashMap<>();
 private KreyliOrderSync(){}
 public static void mark(){if(MP.isHost()&&MP.playing())dirty=true;}
 public static void request(){
  if(!MP.isClient()||!MP.playing())return;
  var host=host();
  if(host!=null)host.send("kreyli_orders_request");
 }
 static void requestRoute(int from,int destination,int troops){
  var host=host();
  if(host!=null)host.send("kreyli_regroup",from,destination,troops);
 }
 private static MP_Net.Conn host(){
  try{var f=MP.class.getDeclaredField("host");f.setAccessible(true);return (MP_Net.Conn)f.get(null);}
  catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
 }
 public static boolean hostMessage(MP_Net.Conn connection,Object[] message){
  if(message.length==0)return false;
  if("kreyli_regroup".equals(message[0])){
   if(message.length==4&&MP.isHost()&&MP.clients.contains(connection)&&connection.civ>0
      &&message[1] instanceof Integer from&&message[2] instanceof Integer destination&&message[3] instanceof Integer troops)
    KreyliRealtimeMarch.startGuestRoute(connection.civ,from,destination,troops);
   return true;
  }
  if(!"kreyli_orders_request".equals(message[0]))return false;
  if(message.length==1&&MP.isHost()&&MP.clients.contains(connection)&&connection.civ>0)mark();return true;
 }
 public static void reset(){dirty=worldDirty=false;lastWorldSync=lastSent=sequence=received=0;receivedTurn=sentTurn=-1;sentCountries.clear();}
 public static void worldChanged(){
  if(MP.isHost()&&MP.started)worldDirty=true;
 }
 public static void tick(){
  if(!MP.isHost()||!MP.playing()||!MP.inputOrders())return;
  long now=System.nanoTime();
  if(worldDirty&&(lastWorldSync==0||now-lastWorldSync>=WORLD_INTERVAL_NANOS)){
   worldDirty=false;
   lastWorldSync=now;
   MP.requestSync();
  }
  if(sentTurn!=GameCalendar.TURNID)dirty=true;
  for(var connection:MP.clients)if(connection.civ>0&&!java.util.Objects.equals(sentCountries.get(connection),connection.civ))dirty=true;
  if(!dirty)return;
  if(now-lastSent<150_000_000L)return;
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
