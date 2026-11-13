package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.CFG;
import age.of.civilizations2.jakowski.lukasz.Menu;
import age.of.civilizations2.jakowski.lukasz.MenuManager;
import age.of.civilizations2.jakowski.lukasz.Messages.Message;
import age.of.civilizations2.jakowski.lukasz.Messages.MessageBox_GameData;
import age.of.civilizations2.jakowski.lukasz.Messages.MessageType;
import java.util.List;

public final class KreyliRealtimeMessages {
    private record Row(int sender,int icon,int background,int first,int second,Class<?> type,MessageType kind,String tag,boolean response) {}
    private static final int MAX_VISIBLE=8;
    private static Menu menu;
    private static List<Row> rows;
    private static boolean slowAging;
    private static final java.util.Map<Integer,Long> nextAge=new java.util.HashMap<>();

    private KreyliRealtimeMessages(){}

    private static List<Row> current(){
        if(!KreyliLobby.realTime()||!MP.playing()||CFG.core.getPlayersSize()<=CFG.PLAYER_TURN_ID||CFG.PLAYER_TURN_ID<0)return null;
        int civ=CFG.core.getPlayer(CFG.PLAYER_TURN_ID).getCivId();
        if(!MP.validCiv(civ)||civ<=0)return null;
        return CFG.core.getCiv(civ).getCivDiploGD().messageBox.lMessages.stream()
            .map(m->new Row(m.fromCivID,m.getImageID(),m.getBGImageID(),m.iValue,m.iValue2,m.getClass(),m.messageType,m.TAG,m.requestsResponse)).toList();
    }

    public static boolean keepMenu(MenuManager manager){
        return rows!=null&&manager.getInGameView()&&menu==manager.getInGame_Messages()&&rows.equals(current());
    }

    public static void rebuilt(MenuManager manager){
        rows=manager.getInGameView()?current():null;
        menu=rows==null?null:manager.getInGame_Messages();
        if(menu!=null)limitButtons();
    }

    private static void limitButtons(){
        int responses=(int)rows.stream().filter(Row::response).count();
        int passiveSlots=Math.max(0,MAX_VISIBLE-responses);
        int displayed=0,x=0;
        for(int i=0;i<menu.getMenuElemsSize()&&i<rows.size();i++){
            var button=menu.getMenuElem(i);
            Row row=rows.get(rows.size()-1-i);
            boolean visible=displayed<MAX_VISIBLE&&(row.response()||passiveSlots>0&&!passiveRelationship(row.kind()))
                    &&x+button.getWidthE()<=menu.getWidthM();
            button.setVisibleE(visible);
            button.setClickable(visible);
            if(visible){
                button.setPosX(x);
                x+=button.getWidthE()+CFG.PADD;
                displayed++;
                if(!row.response())passiveSlots--;
            }
        }
        menu.updateMenuElements_IsInView();
        menu.updateMenuElements_IsInView_X();
    }

    private static boolean passiveRelationship(MessageType kind){
        return kind==MessageType.RELATIONS_INCREASE
                ||kind==MessageType.RELATIONS_INCREASE_ENDED
                ||kind==MessageType.RELATIONS_FRIENDLY;
    }

    public static void ageBox(MessageBox_GameData box,int civ){
        boolean player=MP.isHost()&&MP.started&&KreyliLobby.realTime()&&civ>0
            &&(CFG.core.getPlayer(0).getCivId()==civ||MP.clients.stream().anyMatch(conn->conn.civ==civ));
        if(player){
            long now=KreyliRealtimePause.clock();
            long due=nextAge.computeIfAbsent(civ,id->now+30_000_000_000L);
            if(now>=due)nextAge.put(civ,now+30_000_000_000L);
            else slowAging=true;
        }
        try{box.updateNextTurn(civ);}finally{slowAging=false;}
    }

    public static boolean ageMessage(Message message){
        if(slowAging&&(message.messageType==null||!message.messageType.name().equals("GIFT")||message.requestsResponse))return false;
        return message.updateNextTurn();
    }

    static void reset(){menu=null;rows=null;nextAge.clear();}
}
