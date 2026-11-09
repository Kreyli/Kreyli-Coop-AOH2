package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.*;
import age.of.civilizations2.jakowski.lukasz.Button.MenuElemUI;
import age.of.civilizations2.jakowski.lukasz.Button.NewGame.Button_InGameBox;
import age.of.civilizations2.jakowski.lukasz.Button.NewGame.Button_In_Game_Box_Special;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import java.util.ArrayList;

/** Native menu hit targets with a purpose-built, two-column room presentation matching AoH2 style. */
public final class KreyliLobbyRoom extends Menu {
    public static final int READY=5, INVITE=6, DISCORD=7, LEAVE=8, MODE=9, PASSWORD=10, TIMER=11, LISTED=12, CONNECTION=13, PORT=14, REGION=15, APPLY=16;
    private static final Color PANEL=new Color(.055f,.071f,.086f,.82f), ROW=new Color(.12f,.16f,.19f,.82f);
    private static final Color LINE=new Color(.18f,.20f,.25f,1), WHITE=new Color(.94f,.95f,.98f,1), MUTED=new Color(.55f,.60f,.68f,1);
    private static final Color RED=new Color(.78f,.075f,.18f,1), GREEN=new Color(.35f,.82f,.61f,1), GOLD=new Color(.93f,.68f,.36f,1);
    private final int padding, left, rightX, right, bodyY, bodyH, bottom, buttonH;
    private final float scale;
    private final Color blend=new Color();
    private final boolean host=MP.isHost();
    private boolean shared=SharedSupport.shared, listed=MP.listInLobby, direct=MP.hostDirect;
    private int seconds=MP.turnSeconds;
    private final java.util.List<String[]> regions=java.util.List.copyOf(MP.regions);
    private int region=Math.max(0,regions.indexOf(MP.selectedRegion()));
    private static final int[] TIMERS={0,10,20,30,45,60,90};

    public KreyliLobbyRoom(){
        int width=Math.min(1100,CFG.GAMEWIDTH-48), height=Math.min(570,CFG.GAMEHEIGHT-96);
        scale=Math.min(1f,Math.min(width/1100f,height/570f));
        padding=px(26);int gap=px(20);left=(int)((width-padding*2-gap)*.61f);right=width-padding*2-gap-left;rightX=padding+left+gap;
        bodyY=px(114);buttonH=px(54);bottom=height-padding-buttonH;bodyH=bottom-bodyY-px(22);
        var elements=new ArrayList<MenuElemUI>();
        elements.add(new Section(0,0,width,px(100),0));
        elements.add(new Section(padding,bodyY,left,bodyH,1));
        elements.add(new Section(padding+px(16),bodyY+px(74),left-px(32),px(83),2));
        elements.add(new Section(padding+px(16),bodyY+px(171),left-px(32),px(83),3));
        elements.add(new Section(rightX,bodyY,right,host?px(59):bodyH,4));
        elements.add(new ActionButton(rightX,bottom,right,buttonH,READY));
        int inviteWidth=left-px(226);
        elements.add(new ActionButton(padding+left-inviteWidth,bottom,inviteWidth,buttonH,INVITE));
        elements.add(new ActionButton(padding,bottom,px(98),buttonH,DISCORD));
        elements.add(new ActionButton(padding+px(108),bottom,px(108),buttonH,LEAVE));
        if(host){
            for(int action=MODE;action<=APPLY;action++){
                int x=rightX+px(8),y=bodyY+px(60)+(action-MODE)*px(35);
                int w=right-px(16),h=px(32);
                if(action==PASSWORD||action==PORT)elements.add(new SettingInput(action,x,y,w,h));
                else elements.add(new ActionButton(x,y,w,h,action));
            }
        }
        initMenu(MP_Form.titleBar("Kreyli Coop AOH2"),(CFG.GAMEWIDTH-width)/2,(CFG.GAMEHEIGHT-height)/2,width,height,elements,true,false);
    }
    private int px(int value){return Math.max(1,Math.round(value*scale));}
    private boolean guestPresent(){return !KreyliLobby.guestDisplay().isEmpty();}
    private boolean localReady(){return MP.isHost()?KreyliLobby.hostIsReady():KreyliLobby.guestIsReady();}
    private String label(int action){return switch(action){
        case READY -> KreyliLobby.choosingGame()?(MP.isHost()?"Выбрать игру":"Ожидаем хоста"):(localReady()?"Отменить готовность":"Готов");
        case INVITE -> "Пригласить через Steam";
        case DISCORD -> "Discord";
        case MODE -> shared?"Страны: общая страна":"Страны: разные страны";
        case TIMER -> "Ожидание второго игрока: "+(seconds==0?"выкл.":seconds+" с");
        case LISTED -> "Публичный список: "+(listed?"да":"нет");
        case CONNECTION -> direct?"Подключение: LAN / VPN":"Подключение: код комнаты / Steam";
        case REGION -> "Регион: "+regions.get(region)[1];
        case APPLY -> "Применить настройки";
        default -> "Выйти";
    };}
    private void act(int action){switch(action){
        case READY -> KreyliLobby.toggleReady();
        case INVITE -> KreyliSteam.inviteFriends();
        case DISCORD -> KreyliLobby.discord();
        case LEAVE -> Gdx.app.postRunnable(()->{MP.stop();MP_UI.open(View.eMP);});
        case MODE -> shared=!shared;
        case TIMER -> {
            int index=0;
            for(int i=0;i<TIMERS.length;i++)if(TIMERS[i]==seconds)index=i;
            seconds=TIMERS[(index+1)%TIMERS.length];
        }
        case LISTED -> listed=!listed;
        case CONNECTION -> direct=!direct;
        case REGION -> region=(region+1)%regions.size();
        case APPLY -> applySettings();
        default -> {}
    }}
    @Override public void onBackPressed(){act(LEAVE);}
    @Override public void actionCloseMenu(){act(LEAVE);}
    @Override public void actionEL(int index){
        if(index>=READY&&index<menuElem.size()&&menuElem.get(index).getIsClickable())super.actionEL(index);
    }
    private void applySettings(){
        String password=menuElem.get(PASSWORD).getTextE();
        if(password.length()>256||password.chars().anyMatch(Character::isISOControl)){
            MP.toast("Пароль должен содержать не более 256 символов без переносов строк",false);
            return;
        }
        int port=MP_Form.parse(menuElem.get(PORT).getTextE(),-1,1,65535);
        if(port<1){MP.toast("Укажите порт от 1 до 65535",false);return;}
        String regionName=regions.get(region)[1];
        Gdx.app.postRunnable(()->SharedHostForm.apply(shared,password,seconds,listed,direct,port,regionName));
    }
    private final class SettingInput extends Button_In_Game_Box_Special {
        private final int field;
        SettingInput(int field,int x,int y,int w,int h){
            super(field==PASSWORD?MP.password:Integer.toString(MP.port),CFG.PADD*2,x,y,w,true);
            this.field=field;
            setHeightE(h);
        }
        @Override public String getTextToDrawElem(){
            String value=getTextE();
            return field==PASSWORD?"Пароль: "+(value.isEmpty()?"без пароля":"•".repeat(Math.min(20,value.length()))):"Порт LAN: "+value;
        }
        @Override public void actionElem(int index){if(MP.isHost()&&!MP.started)CFG.showKeyboard();}
    }
    @Override public void draw(SpriteBatch batch,int offsetX,int offsetY,boolean active){
        int x=getPosX()+offsetX,y=getPosY()+offsetY;
        float previous=batch.getPackedColor();
        fill(batch,0,0,CFG.GAMEWIDTH,CFG.GAMEHEIGHT,new Color(0,0,0,.48f));
        batch.setColor(Color.WHITE);
        MP_Form.drawFrame(this,batch,offsetX,offsetY);
        for(int i=0;i<menuElem.size();i++){
            var item=menuElem.get(i);if(item.getVisibleE())item.drawE(batch,x,y,getMenuElementIsActive(active,i),false);
        }
        batch.setPackedColor(previous);
    }
    private static void fill(SpriteBatch batch,int x,int y,int w,int h,Color color){if(w<=0||h<=0)return;batch.setColor(color);Images.pix.draw(batch,x,y,w,h);}
    private static void round(SpriteBatch batch,int x,int y,int w,int h,int r,Color color){
        r=Math.min(r,Math.min(w,h)/2);fill(batch,x+r,y,w-r*2,h,color);fill(batch,x,y+r,r,h-r*2,color);fill(batch,x+w-r,y+r,r,h-r*2,color);
        for(int i=0;i<r;i++){int edge=(int)Math.ceil(r-Math.sqrt(r*r-(r-i-.5)*(r-i-.5)));fill(batch,x+edge,y+i,r-edge,1,color);fill(batch,x+w-r,y+i,r-edge,1,color);fill(batch,x+edge,y+h-i-1,r-edge,1,color);fill(batch,x+w-r,y+h-i-1,r-edge,1,color);}
    }
    private int textWidth(String value,float factor){return Math.round(MP_UI.textWidth(value)*factor*scale);}
    private String fit(String value,int max,float factor){if(textWidth(value,factor)<=max)return value;while(value.length()>1&&textWidth(value+"…",factor)>max)value=value.substring(0,value.length()-1);return value+"…";}
    private void text(SpriteBatch batch,String value,int x,int y,Color color,float factor){
        var font=CFG.fontMain.get(0);float sx=font.getData().scaleX,sy=font.getData().scaleY;Color old=new Color(font.getColor());
        font.getData().setScale(sx*factor*scale,sy*factor*scale);font.setColor(color);batch.setColor(Color.WHITE);
        font.draw(batch,value,x,-y);font.getData().setScale(sx,sy);font.setColor(old);
    }
    private void center(SpriteBatch batch,String value,int x,int y,int width,Color color,float factor){text(batch,value,x+(width-textWidth(value,factor))/2,y,color,factor);}
    private void pill(SpriteBatch batch,String value,int x,int y,int width,Color color){
        blend.set(color);blend.a=.12f;round(batch,x,y,width,px(29),px(8),blend);center(batch,value,x,y+px(7),width,color,.78f);
    }
    private final class Section extends Button_InGameBox {
        private final int kind;
        Section(int x,int y,int w,int h,int kind){super("",-1,x,y,w,h,false);this.kind=kind;setClickable(false);}
        @Override public void actionElem(int ignored){}
        @Override public void drawTextE(SpriteBatch batch,int ox,int oy,boolean active){}
        @Override public void drawButtonBGE(SpriteBatch batch,int ox,int oy,boolean active){
            int x=getPosXE()+ox,y=getPosY()+oy,w=getWidthE(),h=getHeightE();
            if(kind==0){
                text(batch,"KREYLI  /  COOP AOH2",x+padding,y+px(25),MUTED,.72f);
                text(batch,"Лобби",x+padding,y+px(50),WHITE,1.6f);
                text(batch,"Подготовьтесь к совместной кампании",x+padding+px(143),y+px(60),MUTED,.84f);
                fill(batch,x+padding,y+h-px(1),w-padding*2,1,LINE);return;
            }
            if(kind==1){
                round(batch,x,y,w,h,px(8),PANEL);
                text(batch,"Игроки",x+px(20),y+px(20),WHITE,1.08f);
                pill(batch,(guestPresent()?"2":"1")+" / 2",x+w-px(80),y+px(15),px(60),MUTED);
                text(batch,"КОМАНДА",x+px(28),y+px(55),MUTED,.65f);
                text(batch,"ГОТОВНОСТЬ",x+w-px(151),y+px(55),MUTED,.65f);
                int ready=(KreyliLobby.hostIsReady()?1:0)+(KreyliLobby.guestIsReady()?1:0);
                int total=guestPresent()?2:1, countdown=KreyliLobby.countdown();
                String hint=KreyliLobby.choosingGame()?"Хост выбирает кампанию или сохранение":countdown>0?"Старт через "+countdown:guestPresent()?"Ожидаем готовности игроков":"Можно начать одному или пригласить друга";
                fill(batch,x+px(20),y+h-px(64),w-px(40),1,LINE);
                text(batch,fit(hint,w-px(40),.76f),x+px(20),y+h-px(43),MUTED,.76f);
                text(batch,"Готовы: "+ready+"/"+total,x+px(20),y+h-px(23),ready==total?GREEN:MUTED,.7f);return;
            }
            if(kind==2||kind==3){
                boolean host=kind==2,present=host||guestPresent(),ready=host?KreyliLobby.hostIsReady():KreyliLobby.guestIsReady();
                round(batch,x,y,w,h,px(7),present?ROW:PANEL);
                if(present)fill(batch,x,y+px(14),px(2),h-px(28),host?RED:LINE);
                round(batch,x+px(14),y+px(18),px(46),px(46),px(9),host?new Color(.24f,.09f,.14f,1):new Color(.13f,.16f,.21f,1));
                String name=host?KreyliLobby.hostDisplay():KreyliLobby.guestDisplay();
                String initial=present&&!name.isEmpty()?name.substring(0,1).toUpperCase(java.util.Locale.ROOT):"+";
                center(batch,initial,x+px(14),y+px(29),px(46),present?WHITE:MUTED,1.2f);
                text(batch,fit(present?name:"Свободное место",w-px(215),.98f),x+px(75),y+px(23),present?WHITE:MUTED,.98f);
                text(batch,host?"Организатор":present?"Участник":"Ожидаем подключения",x+px(75),y+px(49),MUTED,.7f);
                pill(batch,!present?"Ожидание":ready?"Готов":"Не готов",x+w-px(121),y+px(27),px(105),!present?MUTED:ready?GREEN:GOLD);return;
            }
            round(batch,x,y,w,h,px(8),PANEL);
            text(batch,"Настройки комнаты",x+px(21),y+px(21),WHITE,1.04f);
            if(host){
                String address=MP.hostDirect?"Порт: "+MP.port:"Код комнаты: "+MP.relayCode;
                text(batch,fit(address,w-px(42),.73f),x+px(21),y+px(43),MUTED,.73f);
                return;
            }
            text(batch,"Параметры выбранного лобби",x+px(21),y+px(48),MUTED,.7f);
            setting(batch,x,y+px(90),w,"РЕЖИМ",SharedSupport.shared?"Общая страна":"Разные страны");
            setting(batch,x,y+px(149),w,"ОЖИДАНИЕ ВТОРОГО ИГРОКА",KreyliLobby.timerLabel());
            String connection=MP.isHost()?(MP.hostDirect?"Локальная сеть / VPN":"Комната по коду"):"Подключение к хосту";
            setting(batch,x,y+px(208),w,"СОЕДИНЕНИЕ",connection);
            String detail=MP.isHost()?(MP.hostDirect?"Порт: "+MP.port:"Код: "+MP.relayCode):"Настройки задаёт организатор";
            text(batch,fit(detail,w-px(42),.73f),x+px(21),y+h-px(18),MUTED,.73f);
        }
    }
    private void setting(SpriteBatch batch,int x,int y,int width,String key,String value){text(batch,key,x+px(21),y,MUTED,.65f);text(batch,fit(value,width-px(42),.94f),x+px(21),y+px(23),WHITE,.94f);fill(batch,x+px(21),y+px(51),width-px(42),1,LINE);}
    private final class ActionButton extends Button_InGameBox {
        private final int action;
        ActionButton(int x,int y,int w,int h,int action){super(label(action),-1,x,y,w,h,true);this.action=action;}
        @Override public String getTextE(){return label(action);}
        @Override public void actionElem(int index){if(getIsClickable())act(action);}
        @Override public boolean getIsClickable(){return action>=MODE?MP.isHost()&&!MP.started:(action!=READY||!KreyliLobby.choosingGame()||MP.isHost());}
        @Override public void drawTextE(SpriteBatch batch,int ox,int oy,boolean active){}
        @Override public void drawButtonBGE(SpriteBatch batch,int ox,int oy,boolean active){
            boolean enabled=getIsClickable();
            int x=getPosXE()+ox,y=getPosY()+oy,w=getWidthE(),h=getHeightE();
            batch.setColor(Color.WHITE);
            super.drawButtonBGE(batch,ox,oy,active);
            Color color=enabled?(action==READY&&localReady()?GREEN:WHITE):MUTED;
            float factor=action==LEAVE||action>=MODE?.8f:.91f;
            center(batch,fit(label(action),w-px(18),factor),x,y+(h-px(CFG.TEXT_HEIGHT_DEFAULT))/2,w,color,factor);
        }
    }
}
