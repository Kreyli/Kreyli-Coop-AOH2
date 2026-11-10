package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.*;
import age.of.civilizations2.jakowski.lukasz.Button.MenuElemUI;
import age.of.civilizations2.jakowski.lukasz.Button.NewGame.Button_InGameBox;
import age.of.civilizations2.jakowski.lukasz.Button.NewGame.Button_In_Game_Box_Special;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import java.util.ArrayList;
import java.util.function.Supplier;

/** A native AoH2 room with a roster, live settings and multiplayer actions. */
public final class KreyliLobbyRoom extends Menu {
    public static final int READY=4, INVITE=5, DISCORD=6, LEAVE=7;
    public static final int PASSWORD=8, TIMER=9, LISTED=10, CONNECTION=11, PORT=12;
    private static final int[] TIMERS={0,10,20,30,45,60,90};
    private final boolean host=MP.isHost();

    public KreyliLobbyRoom(){
        int gap=CFG.PADD*2;
        int width=Math.min(CFG.GAMEWIDTH-gap*4,Math.max(CFG.BUTTON_W*5,CFG.GAMEWIDTH*3/4));
        int row=Math.max(1,Math.min(CFG.BUTTON_H,(CFG.GAMEHEIGHT-CFG.BUTTON_H*3-gap*4)/9));
        int height=row*9+gap*4;
        int left=(width-gap*3)/2;
        int rightX=left+gap*2;
        int right=width-rightX-gap;
        int first=gap+row;
        int bottom=height-gap-row;
        int quarter=(width-gap*5)/4;
        var elements=new ArrayList<MenuElemUI>();

        elements.add(new RoomButton(()->KreyliText.nativeLabel("Players","Players")+"  "+KreyliLobby.roster().size()+" / "+KreyliSteam.MAX_PLAYERS,gap,gap,left,row,null));
        elements.add(new RosterTable(gap,first,left,bottom-row-first));
        elements.add(new RoomButton(this::status,gap,bottom-row,quarter,row,null));
        elements.add(new RoomButton(()->KreyliText.t("Настройки лобби","Lobby settings"),rightX,gap,right,row,null));
        elements.add(new RoomButton(this::readyText,gap*4+quarter*3,bottom,width-gap*5-quarter*3,row,KreyliLobby::toggleReady));
        elements.add(new RoomButton(()->KreyliText.t("Пригласить через Steam","Invite via Steam"),gap*3+quarter*2,bottom,quarter,row,KreyliSteam::inviteFriends));
        elements.add(new RoomButton(()->"Discord",gap*2+quarter,bottom,quarter,row,KreyliLobby::discord));
        elements.add(new RoomButton(()->KreyliText.nativeLabel("Back","Back"),gap,bottom,quarter,row,this::leave));

        if(host){
            elements.add(new SettingInput(PASSWORD,rightX,first,right,row));
            elements.add(new RoomButton(this::timer,rightX,first+row,right,row,()->change(TIMER)));
            elements.add(new RoomButton(()->KreyliText.t("Публичный список: ","Public listing: ")+MP_Form.yesNo(MP.listInLobby),rightX,first+row*2,right,row,()->change(LISTED)));
            elements.add(new RoomButton(this::connection,rightX,first+row*3,right,row,()->change(CONNECTION)));
            elements.add(new SettingInput(PORT,rightX,first+row*4,right,row));
        }else{
            elements.add(new RoomButton(this::connection,rightX,first,right,row,null));
            elements.add(new RoomButton(this::timer,rightX,first+row,right,row,null));
        }
        elements.add(new RoomCode((width-quarter*2-gap)/2,bottom-row,quarter*2+gap,row));

        initMenu(MP_Form.titleBar(KreyliText.t("Мультиплеер · Лобби","Multiplayer · Lobby")),(CFG.GAMEWIDTH-width)/2,(CFG.GAMEHEIGHT-height)/2,width,height,elements,true,false);
    }

    private String timer(){return KreyliText.t("Ожидание игроков: ","Wait for players: ")+KreyliLobby.timerLabel();}
    private String connection(){
        boolean lan=host?MP.hostDirect:MP.roomCode()==null;
        return lan?KreyliText.t("Подключение: LAN / VPN","Connection: LAN / VPN"):KreyliText.t("Подключение: Steam","Connection: Steam");
    }
    private String status(){
        if(KreyliLobby.choosingGame())return KreyliText.t("Хост выбирает кампанию","Host is choosing a campaign");
        if(KreyliLobby.countdown()>0)return KreyliText.t("Старт через ","Starting in ")+KreyliLobby.countdown()+KreyliText.t(" с"," s");
        long ready=KreyliLobby.roster().stream().filter(KreyliLobby.Member::ready).count();
        return KreyliText.t("Готовы: ","Ready: ")+ready+" / "+KreyliLobby.roster().size();
    }
    private String readyText(){
        if(KreyliLobby.choosingGame())return host?KreyliText.t("Выбрать игру","Choose campaign"):KreyliText.t("Ожидаем хоста","Waiting for host");
        return (host?KreyliLobby.hostIsReady():KreyliLobby.guestIsReady())?KreyliText.t("Отменить готовность","Cancel ready"):KreyliText.t("Готов","Ready");
    }
    private void leave(){Gdx.app.postRunnable(()->{MP.stop();MP_UI.open(View.eMP);});}
    @Override public void onBackPressed(){leave();}
    @Override public void actionCloseMenu(){leave();}
    @Override public void actionEL(int index){
        if(index>=0&&index<menuElem.size()&&menuElem.get(index).getIsClickable())super.actionEL(index);
    }
    @Override public void draw(SpriteBatch batch,int offsetX,int offsetY,boolean active){
        float color=batch.getPackedColor();
        batch.setColor(Color.WHITE);
        MP_Form.drawFrame(this,batch,offsetX,offsetY);
        super.draw(batch,offsetX,offsetY,active);
        batch.setPackedColor(color);
    }
    private boolean canEdit(){return MP.isHost()&&!MP.started;}
    private boolean canChangeEndpoint(){return canEdit()&&MP.clients.isEmpty();}
    private void change(int action){
        if(!canEdit())return;
        boolean listed=MP.listInLobby, direct=MP.hostDirect;
        int seconds=MP.turnSeconds;
        String region=MP.selectedRegion()[1];
        switch(action){
            case TIMER -> {
                int index=0;
                for(int i=0;i<TIMERS.length;i++)if(TIMERS[i]==seconds)index=i;
                seconds=TIMERS[(index+1)%TIMERS.length];
            }
            case LISTED -> listed=!listed;
            case CONNECTION -> direct=!direct;
            default -> {return;}
        }
        SharedHostForm.apply(MP.password,seconds,listed,direct,MP.port,region);
    }

    private final class SettingInput extends Button_In_Game_Box_Special {
        private final int field;
        SettingInput(int field,int x,int y,int width,int height){
            super(field==PASSWORD?MP.password:Integer.toString(MP.port),CFG.PADD*2,x,y,width,true);
            this.field=field;
            setHeightE(height);
        }
        @Override public String getTextToDrawElem(){
            String value=getTextE();
            return field==PASSWORD?KreyliText.t("Пароль: ","Password: ")+(value.isEmpty()?KreyliText.t("без пароля","none"):"•".repeat(Math.min(20,value.length()))):KreyliText.t("Порт LAN: ","LAN port: ")+value;
        }
        @Override public boolean getVisibleE(){return super.getVisibleE()&&(field!=PORT||MP.hostDirect);}
        @Override public boolean getIsClickable(){return field==PORT?MP.hostDirect&&canChangeEndpoint():canEdit();}
        @Override public void actionElem(int index){
            if(!getIsClickable())return;
            CFG.showKeyboard(field);
            CFG.keyboardSave=()->{
                String value=CFG.keybMess;
                Gdx.app.postRunnable(()->commit(value));
            };
        }
        void commit(String value){
            if(!getIsClickable()||!CFG.menus.getActiveMenu().contains(KreyliLobbyRoom.this))return;
            int port=MP.port;
            String password=MP.password;
            if(field==PASSWORD){
                if(value.length()>256||value.chars().anyMatch(Character::isISOControl)){
                    MP.toast("Пароль должен содержать не более 256 символов без переносов строк",false);
                    return;
                }
                password=value;
            }else{
                port=MP_Form.parse(value,-1,1,65535);
                if(port<1){
                    MP.toast("Укажите порт от 1 до 65535",false);
                    return;
                }
            }
            SharedHostForm.apply(password,MP.turnSeconds,MP.listInLobby,MP.hostDirect,port,MP.selectedRegion()[1]);
            setTextE(field==PASSWORD?MP.password:Integer.toString(MP.port));
        }
    }

    private static final class RoomCode extends Button_InGameBox {
        RoomCode(int x,int y,int width,int height){super("",-1,x,y,width,height,true);}
        private static String code(){return MP.roomCode();}
        @Override public boolean getVisibleE(){return code()!=null&&!code().isBlank();}
        @Override public boolean getIsClickable(){return getVisibleE();}
        @Override public void drawButtonBGE(SpriteBatch batch,int x,int y,boolean active){}
        @Override public void drawTextE(SpriteBatch batch,int x,int y,boolean active){
            if(!getVisibleE())return;
            String text=MP_UI.fit(MP.t("MP_Code","Code: {0}",code()),getWidthE()-CFG.PADD*2);
            Renderer.drawTextWithShadow(batch,MP_UI.font(),text,x+getPosXE()+(getWidthE()-MP_UI.textWidth(text))/2,
                    y+getPosY()+(getHeightE()-CFG.TEXT_HEIGHT_DEFAULT_SMALL)/2,getIsHovered()?Color.WHITE:Color.GOLD);
        }
        @Override public void actionElem(int index){
            if(!getIsClickable())return;
            Gdx.app.getClipboard().setContents(code());
            MP.toast(KreyliText.t("Код комнаты скопирован","Room code copied"),true);
        }
    }

    private static final class RosterTable extends Button_InGameBox {
        private static final Color HEADER=new Color(0.10f,0.15f,0.22f,0.96f);
        private static final Color EVEN_ROW=new Color(0.055f,0.085f,0.13f,0.93f);
        private static final Color ODD_ROW=new Color(0.075f,0.11f,0.16f,0.93f);
        private static final Color DIVIDER=new Color(0.40f,0.34f,0.23f,0.85f);
        private static final Color EMPTY=new Color(0.57f,0.62f,0.69f,1f);

        RosterTable(int x,int y,int width,int height){
            super("",-1,x,y,width,height,false);
        }

        @Override public void drawButtonBGE(SpriteBatch batch,int x,int y,boolean active){}

        @Override public void drawTextE(SpriteBatch batch,int x,int y,boolean active){
            int left=x+getPosXE(),top=y+getPosY();
            int width=getWidthE(),height=getHeightE(),padding=Math.max(2,CFG.PADD);
            int slots=KreyliSteam.MAX_PLAYERS;
            int header=height/(slots+1);
            int body=height-header;
            if(header<1||body<slots)return;
            var members=KreyliLobby.roster();
            int rows=Math.min(members.size(),slots);
            int tableHeight=header+body*rows/slots;
            int statusWidth=Math.min(width/2,Math.max(width/4,
                    MP_UI.textWidth(KreyliText.t("Не готов","Not ready"))+padding*2));
            int split=left+width-statusWidth;
            float old=batch.getPackedColor();
            try{
                rectangle(batch,left,top,width,header,HEADER);
                cellText(batch,KreyliText.t("Игрок","Player"),left+padding,top,split-left-padding*2,header,CFG.COLOR_TEXT_CIV_INFO_TITLE);
                cellText(batch,KreyliText.t("Статус","Status"),split+padding,top,statusWidth-padding*2,header,CFG.COLOR_TEXT_CIV_INFO_TITLE);

                for(int index=0;index<rows;index++){
                    int rowY=top+header+body*index/slots;
                    int rowHeight=top+header+body*(index+1)/slots-rowY;
                    rectangle(batch,left,rowY,width,rowHeight,index%2==0?EVEN_ROW:ODD_ROW);
                    var member=members.get(index);
                    int icon=Math.min(rowHeight-4,width/8);
                    int nameX=left+padding;
                    if(icon>0){
                        var avatar=KreyliProfile.avatar(member.name());
                        int iconY=rowY+(rowHeight-icon)/2;
                        if(avatar!=null||icon>=CFG.TEXT_HEIGHT_DEFAULT_SMALL)
                            KreyliProfileUi.drawAvatar(batch,nameX,iconY,icon,avatar);
                        else rectangle(batch,nameX,iconY,icon,icon,HEADER);
                        nameX+=icon+padding;
                    }
                    cellText(batch,member.name(),nameX,rowY,split-nameX-padding,rowHeight,Color.WHITE);
                    cellText(batch,member.ready()?KreyliText.t("Готов","Ready"):KreyliText.t("Не готов","Not ready"),
                            split+padding,rowY,statusWidth-padding*2,rowHeight,member.ready()?MP.GOOD:EMPTY);
                    rectangle(batch,left,rowY,width,1,DIVIDER);
                }
                rectangle(batch,left,top,width,1,DIVIDER);
                rectangle(batch,left,top+tableHeight-1,width,1,DIVIDER);
                rectangle(batch,left,top,1,tableHeight,DIVIDER);
                rectangle(batch,split,top,1,tableHeight,DIVIDER);
                rectangle(batch,left+width-1,top,1,tableHeight,DIVIDER);
            }finally{batch.setPackedColor(old);}
        }

        private static void rectangle(SpriteBatch batch,int x,int y,int width,int height,Color color){
            batch.setColor(color);
            Images.pix.draw(batch,x,y,width,height);
        }

        private static void cellText(SpriteBatch batch,String value,int x,int y,int width,int height,Color color){
            if(width<=0)return;
            batch.setColor(Color.WHITE);
            Renderer.drawTextWithShadow(batch,MP_UI.font(),MP_UI.fit(value,width),
                    x,y+Math.max(0,(height-CFG.TEXT_HEIGHT_DEFAULT_SMALL)/2),color);
        }
    }

    private final class RoomButton extends Button_InGameBox {
        private final Supplier<String> label;
        private final Runnable action;
        RoomButton(Supplier<String> label,int x,int y,int width,int height,Runnable action){
            super(label.get(),-1,x,y,width,height,action!=null);
            this.label=label;
            this.action=action;
        }
        @Override public void drawTextE(SpriteBatch batch,int x,int y,boolean active){
            if(action==null){
                int index=menuElem.indexOf(this);
                Color color=switch(index){
                    case 0,3 -> CFG.COLOR_TEXT_CIV_INFO_TITLE;
                    case 2 -> Color.GOLD;
                    default -> Color.WHITE;
                };
                int left=CFG.PADD;
                String value=MP_UI.fit(label.get(),getWidthE()-left-CFG.PADD);
                Renderer.drawTextWithShadow(batch,MP_UI.font(),value,
                        x+getPosXE()+left,y+getPosY()+(getHeightE()-CFG.TEXT_HEIGHT_DEFAULT_SMALL)/2,color);
                return;
            }
            String value=MP_UI.fit(label.get(),getWidthE()-CFG.PADD*2);
            if(!value.equals(getTextE()))setTextE(value);
            super.drawTextE(batch,x,y,active);
        }
        @Override public void drawButtonBGE(SpriteBatch batch,int x,int y,boolean active){
            if(action!=null)super.drawButtonBGE(batch,x,y,active);
            else if(menuElem.indexOf(this)==0||menuElem.indexOf(this)==3){
                batch.setColor(CFG.COLOR_NEW_GAME_EDGE_LINE);
                Images.pix.draw(batch,x+getPosXE(),y+getPosY()+getHeightE()-1,getWidthE(),1);
                batch.setColor(Color.WHITE);
            }
        }
        @Override public boolean getIsClickable(){
            if(action==null)return false;
            int index=menuElem.indexOf(this);
            if(index==READY)return host||!KreyliLobby.choosingGame();
            if(index==CONNECTION||(index==LISTED&&MP.hostDirect&&!MP.listInLobby))return canChangeEndpoint();
            return index<PASSWORD||canEdit();
        }
        @Override public void actionElem(int index){if(getIsClickable())action.run();}
    }
}
