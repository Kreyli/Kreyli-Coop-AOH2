package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.CFG;
import age.of.civilizations2.jakowski.lukasz.Images;
import age.of.civilizations2.jakowski.lukasz.Menu;
import age.of.civilizations2.jakowski.lukasz.Renderer;
import age.of.civilizations2.jakowski.lukasz.View;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import java.awt.EventQueue;
import java.text.NumberFormat;
import java.util.Locale;
import javax.swing.JOptionPane;

/** Profile overlay on the same logical screen coordinates as the game UI. */
public final class KreyliProfileUi {
    private static final Color PANEL=new Color(.041f,.056f,.086f,.96f);
    private static final Color CONTROL=new Color(.12f,.17f,.23f,1);
    private static final Color SHADE=new Color(0,0,0,.56f);
    private static final Color MUTED=new Color(.7f,.77f,.84f,1);
    private static final NumberFormat NUMBERS=NumberFormat.getIntegerInstance(Locale.forLanguageTag("ru"));
    private static boolean open, captured, editing;
    private static InputProcessor previousInput;
    private static InputMultiplexer modalInput;

    private KreyliProfileUi(){}

    private record Box(int x,int y,int w,int h){
        boolean contains(int px,int py){return px>=x&&py>=y&&px<x+w&&py<y+h;}
    }

    private static boolean visible(){
        return CFG.menus!=null&&(CFG.menus.getInGameView()
                ||CFG.menus.getIn_MainMenu()||CFG.menus.getInView(View.eMP)
                ||CFG.menus.getInView(View.eMP_HOST)||CFG.menus.getInView(View.eMP_JOINING)
                ||CFG.menus.getInView(View.eMP_BROWSER));
    }

    private static int row(){return Math.max(CFG.BUTTON_H/2,CFG.TEXT_HEIGHT_DEFAULT_SMALL+CFG.PADD*2);}
    private static Box badge(){
        int y=CFG.menus.getInGameView()?CFG.BUTTON_H+CFG.PADD*2:CFG.PADD;
        return new Box(CFG.PADD,y,Math.min(CFG.GAMEWIDTH/3,Math.max(CFG.BUTTON_W*2,row()*4)),row()+CFG.PADD);
    }
    private static Box panel(){
        int width=Math.min(CFG.GAMEWIDTH-CFG.PADD*4,Math.max(460,CFG.BUTTON_W*4));
        int height=row()*9+CFG.PADD*4;
        return new Box((CFG.GAMEWIDTH-width)/2,Math.max(CFG.PADD,(CFG.GAMEHEIGHT-height)/2),width,height);
    }
    private static Box action(Box panel,int index){
        int gap=CFG.PADD*2;
        int width=(panel.w-gap*4)/3;
        return new Box(panel.x+gap+(width+gap)*index,panel.y+panel.h-row()-gap,width,row());
    }

    public static void draw(SpriteBatch batch){
        if(!visible()){close();return;}
        float previous=batch.getPackedColor();
        try{
            Box badge=badge();
            player(batch,badge,KreyliProfile.displayName(),KreyliProfile.avatar());
            if(!open)return;
            fill(batch,new Box(0,0,CFG.GAMEWIDTH,CFG.GAMEHEIGHT),SHADE);
            Box panel=panel();
            fill(batch,panel,PANEL);
            int x=panel.x+CFG.PADD*2,y=panel.y+CFG.PADD*2;
            text(batch,"Профиль игрока",x,y,Color.GOLD);
            y+=row();
            player(batch,new Box(x,y,panel.w-CFG.PADD*4,row()+CFG.PADD),KreyliProfile.displayName(),KreyliProfile.avatar());
            y+=row()*2;
            text(batch,KreyliProfile.steamLinked()?"Профиль Steam привязан":"Локальный профиль",x,y,MUTED);
            y+=row();
            long seconds=KreyliProfile.playSeconds();
            stat(batch,panel,y,"Время в игре",NUMBERS.format(seconds/3600)+" ч "+(seconds/60%60)+" мин");
            stat(batch,panel,y+row(),"Завоёвано областей",NUMBERS.format(KreyliProfile.conquered()));
            stat(batch,panel,y+row()*2,"Заработано денег",NUMBERS.format(KreyliProfile.earned()));
            stat(batch,panel,y+row()*3,"Призвано войск",NUMBERS.format(KreyliProfile.recruited()));
            button(batch,action(panel,0),"Изменить ник");
            button(batch,action(panel,1),"Сменить аватар");
            button(batch,action(panel,2),"Закрыть");
        }finally{batch.setPackedColor(previous);}
    }

    private static void stat(SpriteBatch batch,Box panel,int y,String label,String value){
        int padding=CFG.PADD*2;
        text(batch,label,panel.x+padding,y,MUTED);
        text(batch,value,panel.x+panel.w-padding-MP_UI.textWidth(value),y,Color.WHITE);
    }
    private static void button(SpriteBatch batch,Box box,String value){
        fill(batch,box,CONTROL);
        value=MP_UI.fit(value,box.w-CFG.PADD*2);
        text(batch,value,box.x+(box.w-MP_UI.textWidth(value))/2,baseline(box),Color.WHITE);
    }
    private static int baseline(Box box){return box.y+(box.h-CFG.TEXT_HEIGHT_DEFAULT_SMALL)/2;}
    private static void text(SpriteBatch batch,String value,int x,int y,Color color){
        batch.setColor(Color.WHITE);
        Renderer.drawTextWithShadow(batch,MP_UI.font(),value,x,y,color);
    }
    private static void fill(SpriteBatch batch,Box box,Color color){
        batch.setColor(color);
        Images.pix.draw(batch,box.x,box.y,box.w,box.h);
    }
    private static void player(SpriteBatch batch,Box box,String name,TextureRegion avatar){
        fill(batch,box,PANEL);
        int size=box.h-CFG.PADD*2;
        int x=box.x+CFG.PADD,y=box.y+CFG.PADD;
        drawAvatar(batch,x,y,size,avatar);
        x+=size+CFG.PADD;
        text(batch,MP_UI.fit(name,box.x+box.w-x-CFG.PADD),x,baseline(box),Color.WHITE);
    }
    public static void drawAvatar(SpriteBatch batch,int x,int y,int size,TextureRegion avatar){
        if(avatar!=null){
            batch.setColor(Color.WHITE);
            batch.draw(avatar,x,-y-size,size,size);
        }else{
            fill(batch,new Box(x,y,size,size),CONTROL);
            text(batch,"?",x+(size-MP_UI.textWidth("?"))/2,y+(size-CFG.TEXT_HEIGHT_DEFAULT_SMALL)/2,MUTED);
        }
    }

    /** The original panel keeps the flag; controller identities are drawn just above it. */
    public static void drawProvince(Menu menu,SpriteBatch batch,int offsetX,int offsetY){
        if(!MP.playing()||CFG.core==null||open||CFG.menus.getIn_Game_CivilizationView())return;
        int province=CFG.chosenProvinceID>=0?CFG.chosenProvinceID:CFG.core.getActiveProvID();
        if(province<0)return;
        drawControllers(menu,batch,offsetX,offsetY,CFG.core.getProv(province).getCivId(),false);
    }
    public static void drawCountry(Menu menu,SpriteBatch batch,int offsetX,int offsetY){
        if(!MP.playing()||open)return;
        drawControllers(menu,batch,offsetX,offsetY,CFG.getActiveCivInfoId(),true);
    }
    private static void drawControllers(Menu menu,SpriteBatch batch,int offsetX,int offsetY,int civ,boolean country){
        if(civ<=0)return;
        var players=KreyliProfile.player(civ);
        if(players.isEmpty())return;
        int width=Math.min(CFG.GAMEWIDTH/3,Math.max(CFG.BUTTON_W*2,row()*4));
        int desiredX=menu.getPosX()+offsetX+CFG.PADD+(country?menu.getWidthM():0);
        int x=Math.max(CFG.PADD,Math.min(CFG.GAMEWIDTH-width-CFG.PADD,desiredX));
        int y=menu.getPosY()+offsetY+(country?CFG.PADD:-players.size()*(row()+CFG.PADD)-CFG.PADD);
        if(y<0)return;
        float previous=batch.getPackedColor();
        try{
            for(var controller:players){
                player(batch,new Box(x,y,width,row()),controller.nick(),controller.avatar());
                y+=row()+CFG.PADD;
            }
        }finally{batch.setPackedColor(previous);}
    }

    public static boolean touchDown(int x,int y,int pointer,int button){
        if(pointer!=0||!visible())return false;
        captured=open||(button==Input.Buttons.LEFT&&badge().contains(x,y));
        return captured;
    }
    public static boolean touchUp(int x,int y,int pointer,int button){
        if(pointer!=0||!captured)return false;
        captured=false;
        if(button!=Input.Buttons.LEFT||!visible())return true;
        if(!open){if(badge().contains(x,y))open();}
        else if(action(panel(),0).contains(x,y))editNickname();
        else if(action(panel(),1).contains(x,y))KreyliProfile.cycleAvatar();
        else if(action(panel(),2).contains(x,y)||!panel().contains(x,y))close();
        CFG.setRenderO(true);
        return true;
    }
    private static void open(){
        open=true;
        previousInput=Gdx.input.getInputProcessor();
        modalInput=new InputMultiplexer();
        modalInput.addProcessor(new InputAdapter(){
            @Override public boolean keyDown(int key){return true;}
            @Override public boolean keyUp(int key){
                if(key==Input.Keys.ESCAPE&&!editing)close();
                return true;
            }
            @Override public boolean keyTyped(char key){return true;}
            @Override public boolean scrolled(float x,float y){return true;}
        });
        if(previousInput!=null)modalInput.addProcessor(previousInput);
        Gdx.input.setInputProcessor(modalInput);
    }
    private static void close(){
        if(!open)return;
        open=false;
        if(Gdx.input.getInputProcessor()==modalInput)Gdx.input.setInputProcessor(previousInput);
        previousInput=null;
        modalInput=null;
        CFG.setRenderO(true);
    }
    private static void editNickname(){
        if(editing)return;
        editing=true;
        String current=KreyliProfile.nick();
        EventQueue.invokeLater(()->{
            String name=JOptionPane.showInputDialog(null,"Новый ник в игре",current);
            Gdx.app.postRunnable(()->{
                editing=false;
                if(name!=null&&open)KreyliProfile.setNickname(name);
                CFG.setRenderO(true);
            });
        });
    }
}
