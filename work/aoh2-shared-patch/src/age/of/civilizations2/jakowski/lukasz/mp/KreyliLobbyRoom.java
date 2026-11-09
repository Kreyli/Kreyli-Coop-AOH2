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
    public static final int READY=5, INVITE=6, DISCORD=7, LEAVE=8;
    public static final int MODE=9, PASSWORD=10, TIMER=11, LISTED=12, CONNECTION=13, PORT=14, REGION=15;
    private static final int[] TIMERS={0,10,20,30,45,60,90};
    private final boolean host=MP.isHost();

    public KreyliLobbyRoom(){
        int gap=CFG.PADD*2;
        int width=Math.min(CFG.GAMEWIDTH-gap*4,Math.max(CFG.BUTTON_W*5,CFG.GAMEWIDTH*3/4));
        int row=Math.max(1,Math.min(CFG.BUTTON_H,(CFG.GAMEHEIGHT-CFG.BUTTON_H*3-gap*4)/9));
        int height=row*(host?9:6)+gap*4;
        int left=(width-gap*3)/2;
        int rightX=left+gap*2;
        int right=width-rightX-gap;
        int first=gap+row;
        int bottom=height-gap-row;
        int quarter=(width-gap*5)/4;
        var elements=new ArrayList<MenuElemUI>();

        elements.add(new RoomButton(()->"Игроки  "+(guestPresent()?"2 / 2":"1 / 2"),gap,gap,left,row,null));
        elements.add(new RoomButton(()->player(true),gap,first,left,row,null));
        elements.add(new RoomButton(()->player(false),gap,first+row,left,row,null));
        elements.add(new RoomButton(this::status,gap,first+row*3,left,row,null));
        elements.add(new RoomButton(()->"Настройки лобби",rightX,gap,right,row,null));
        elements.add(new RoomButton(this::readyText,gap*4+quarter*3,bottom,width-gap*5-quarter*3,row,KreyliLobby::toggleReady));
        elements.add(new RoomButton(()->"Пригласить через Steam",gap*3+quarter*2,bottom,quarter,row,KreyliSteam::inviteFriends));
        elements.add(new RoomButton(()->"Discord",gap*2+quarter,bottom,quarter,row,KreyliLobby::discord));
        elements.add(new RoomButton(()->"Назад",gap,bottom,quarter,row,this::leave));

        if(host){
            elements.add(new RoomButton(this::mode,rightX,first,right,row,()->change(MODE)));
            elements.add(new SettingInput(PASSWORD,rightX,first+row,right,row));
            elements.add(new RoomButton(this::timer,rightX,first+row*2,right,row,()->change(TIMER)));
            elements.add(new RoomButton(()->"Публичный список: "+MP_Form.yesNo(MP.listInLobby),rightX,first+row*3,right,row,()->change(LISTED)));
            elements.add(new RoomButton(this::connection,rightX,first+row*4,right,row,()->change(CONNECTION)));
            elements.add(new SettingInput(PORT,rightX,first+row*5,right,row));
            elements.add(new RoomButton(()->"Регион: "+MP.selectedRegion()[1],rightX,first+row*6,right,row,()->change(REGION)));
        }else{
            elements.add(new RoomButton(this::mode,rightX,first,right,row,null));
            elements.add(new RoomButton(this::timer,rightX,first+row,right,row,null));
        }

        initMenu(MP_Form.titleBar("Мультиплеер · Лобби"),(CFG.GAMEWIDTH-width)/2,(CFG.GAMEHEIGHT-height)/2,width,height,elements,true,false);
    }

    private String mode(){return SharedSupport.shared?"Страны: общая страна":"Страны: разные страны";}
    private String timer(){return "Ожидание второго игрока: "+KreyliLobby.timerLabel();}
    private String connection(){
        return MP.hostDirect?"Подключение: LAN / VPN":"Код комнаты: "+MP.relayCode;
    }
    private boolean guestPresent(){return !KreyliLobby.guestDisplay().isEmpty();}
    private String player(boolean organizer){
        if(!organizer&&!guestPresent())return "Ожидаем второго игрока";
        String name=organizer?KreyliLobby.hostDisplay():KreyliLobby.guestDisplay();
        boolean ready=organizer?KreyliLobby.hostIsReady():KreyliLobby.guestIsReady();
        return name+(organizer?" (хост)":"")+"  ·  "+(ready?"Готов":"Не готов");
    }
    private String status(){
        if(KreyliLobby.choosingGame())return "Хост выбирает кампанию";
        if(KreyliLobby.countdown()>0)return "Старт через "+KreyliLobby.countdown()+" с";
        int ready=(KreyliLobby.hostIsReady()?1:0)+(KreyliLobby.guestIsReady()?1:0);
        return "Готовы: "+ready+" / "+(guestPresent()?2:1);
    }
    private String readyText(){
        if(KreyliLobby.choosingGame())return host?"Выбрать игру":"Ожидаем хоста";
        return (host?KreyliLobby.hostIsReady():KreyliLobby.guestIsReady())?"Отменить готовность":"Готов";
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
        boolean shared=SharedSupport.shared, listed=MP.listInLobby, direct=MP.hostDirect;
        int seconds=MP.turnSeconds;
        String region=MP.selectedRegion()[1];
        switch(action){
            case MODE -> shared=!shared;
            case TIMER -> {
                int index=0;
                for(int i=0;i<TIMERS.length;i++)if(TIMERS[i]==seconds)index=i;
                seconds=TIMERS[(index+1)%TIMERS.length];
            }
            case LISTED -> listed=!listed;
            case CONNECTION -> direct=!direct;
            case REGION -> {
                if(!MP.regions.isEmpty()){
                    int index=MP.regions.indexOf(MP.selectedRegion());
                    region=MP.regions.get((index+1)%MP.regions.size())[1];
                }
            }
            default -> {return;}
        }
        SharedHostForm.apply(shared,MP.password,seconds,listed,direct,MP.port,region);
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
            return field==PASSWORD?"Пароль: "+(value.isEmpty()?"без пароля":"•".repeat(Math.min(20,value.length()))):"Порт LAN: "+value;
        }
        @Override public boolean getIsClickable(){return field==PORT?canChangeEndpoint():canEdit();}
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
            SharedHostForm.apply(SharedSupport.shared,password,MP.turnSeconds,MP.listInLobby,MP.hostDirect,port,MP.selectedRegion()[1]);
            setTextE(field==PASSWORD?MP.password:Integer.toString(MP.port));
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
                    case 0,4 -> CFG.COLOR_TEXT_CIV_INFO_TITLE;
                    case 1 -> KreyliLobby.hostIsReady()?MP.GOOD:Color.WHITE;
                    case 2 -> KreyliLobby.guestIsReady()?MP.GOOD:Color.WHITE;
                    case 3 -> Color.GOLD;
                    default -> Color.WHITE;
                };
                String value=MP_UI.fit(label.get(),getWidthE()-CFG.PADD*2);
                Renderer.drawTextWithShadow(batch,MP_UI.font(),value,
                        x+getPosXE()+CFG.PADD,y+getPosY()+(getHeightE()-CFG.TEXT_HEIGHT_DEFAULT_SMALL)/2,color);
                return;
            }
            String value=MP_UI.fit(label.get(),getWidthE()-CFG.PADD*2);
            if(!value.equals(getTextE()))setTextE(value);
            super.drawTextE(batch,x,y,active);
        }
        @Override public void drawButtonBGE(SpriteBatch batch,int x,int y,boolean active){
            if(action!=null)super.drawButtonBGE(batch,x,y,active);
            else if(menuElem.indexOf(this)==0||menuElem.indexOf(this)==4){
                batch.setColor(CFG.COLOR_NEW_GAME_EDGE_LINE);
                Images.pix.draw(batch,x+getPosXE(),y+getPosY()+getHeightE()-1,getWidthE(),1);
                batch.setColor(Color.WHITE);
            }
        }
        @Override public boolean getIsClickable(){
            if(action==null)return false;
            int index=menuElem.indexOf(this);
            if(index==READY)return host||!KreyliLobby.choosingGame();
            if(index==CONNECTION||index==REGION||(index==LISTED&&MP.hostDirect&&!MP.listInLobby))return canChangeEndpoint();
            return index<MODE||canEdit();
        }
        @Override public void actionElem(int index){if(getIsClickable())action.run();}
    }
}
