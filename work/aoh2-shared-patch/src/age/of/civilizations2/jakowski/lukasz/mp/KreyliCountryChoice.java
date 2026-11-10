package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.CFG;
import age.of.civilizations2.jakowski.lukasz.Images;
import age.of.civilizations2.jakowski.lukasz.Renderer;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

import static age.of.civilizations2.jakowski.lukasz.mp.KreyliText.t;

/** Confirmation dialog for the map's country-selection action. */
public final class KreyliCountryChoice {
    private static final Color PANEL=new Color(0x111c2cf9), BUTTON=new Color(0x26374cff), SHADE=new Color(0,0,0,.5f);
    private static final int SOLO=0,SHARED=1,LESS=2,MORE=3,CONFIRM=4,BACK=5;
    private static int target=-1,selected=1,pressed=-1;
    private static boolean hostChoice;
    private static InputProcessor previousInput,modalInput;

    private KreyliCountryChoice(){}
    static void open(int civ,boolean isHost){
        close();
        target=civ;hostChoice=isHost;selected=1;
        previousInput=Gdx.input.getInputProcessor();
        InputAdapter keys=new InputAdapter(){
            @Override public boolean keyDown(int key){
                if(key==Input.Keys.ESCAPE)close();
                if(key==Input.Keys.ENTER)activate(CONFIRM);
                return true;
            }
            @Override public boolean keyUp(int key){return true;}
        };
        modalInput=previousInput==null?keys:new InputMultiplexer(keys,previousInput);
        Gdx.input.setInputProcessor(modalInput);
        CFG.setRenderO(true);
    }
    static void close(){
        if(target<0)return;
        target=-1;pressed=-1;
        if(Gdx.input!=null&&Gdx.input.getInputProcessor()==modalInput)Gdx.input.setInputProcessor(previousInput);
        previousInput=modalInput=null;
        CFG.setRenderO(true);
    }
    private static boolean existing(){return !hostChoice&&(KreyliCountrySeats.configured(target)||KreyliCountrySeats.remoteOccupied(target)>0);}
    private static void activate(int action){
        if(target<0)return;
        if(action==BACK){close();return;}
        boolean existing=existing();
        if(action==CONFIRM){
            int civ=target,limit=existing?KreyliCountrySeats.capacity(civ):selected;
            boolean start=hostChoice;
            close();
            KreyliCountrySeats.choose(civ,limit,start);
        }else if(!existing){
            if(action==SOLO)selected=1;
            if(action==SHARED)selected=Math.max(2,selected);
            if(action==LESS&&selected>2)selected--;
            if(action==MORE&&selected>=2)selected=Math.min(KreyliCountrySeats.MAX,selected+1);
            CFG.setRenderO(true);
        }
    }
    private record Box(int x,int y,int w,int h){
        boolean contains(int px,int py){return px>=x&&px<x+w&&py>=y&&py<y+h;}
    }
    private static Box panel(){
        int width=Math.min(CFG.GAMEWIDTH-CFG.PADD*4,Math.max(400,CFG.BUTTON_W*3));
        int height=CFG.BUTTON_H*4+CFG.PADD*6;
        return new Box((CFG.GAMEWIDTH-width)/2,(CFG.GAMEHEIGHT-height)/2,width,height);
    }
    private static Box control(int index){
        Box p=panel();int gap=CFG.PADD,h=CFG.BUTTON_H;
        int half=(p.w-gap*3)/2;
        if(index==SOLO||index==SHARED)return new Box(p.x+gap+(index==SHARED?half+gap:0),p.y+h,half,h);
        if(index==LESS||index==MORE)return new Box(index==LESS?p.x+gap:p.x+p.w-gap-h,p.y+h*2+gap,h,h);
        return new Box(p.x+gap+(index==BACK?half+gap:0),p.y+h*3+gap*3,half,h);
    }
    private static int actionAt(int x,int y){
        boolean existing=existing();
        for(int i=0;i<=BACK;i++){
            if(i<=MORE&&(existing||(i>=LESS&&selected==1)))continue;
            if(control(i).contains(x,y))return i;
        }
        return -1;
    }
    public static boolean touchDown(int x,int y,int pointer,int button){
        if(target<0)return false;
        pressed=pointer==0&&button==Input.Buttons.LEFT?actionAt(x,y):-1;
        return true;
    }
    public static boolean touchUp(int x,int y,int pointer,int button){
        if(target<0)return false;
        int action=pressed;
        pressed=-1;
        if(pointer==0&&button==Input.Buttons.LEFT&&action>=0&&action==actionAt(x,y))activate(action);
        return true;
    }
    public static void draw(SpriteBatch batch){
        if(target<0)return;
        if(hostChoice?(!MP.isHost()||!CFG.menus.getInCreateNewGame()):(!MP.isClient()||!MP.picking)){close();return;}
        float color=batch.getPackedColor();
        try{
            Box p=panel();fill(batch,new Box(0,0,CFG.GAMEWIDTH,CFG.GAMEHEIGHT),SHADE);
            fill(batch,p,Color.GOLD);
            fill(batch,new Box(p.x+1,p.y+1,p.w-2,p.h-2),PANEL);
            int h=CFG.BUTTON_H;
            text(batch,new Box(p.x,p.y,p.w,h),CFG.core.getCiv(target).getCivName(),Color.GOLD);
            boolean existing=existing();
            if(existing){
                int limit=KreyliCountrySeats.capacity(target);
                text(batch,new Box(p.x,p.y+h,p.w,h),limit==1?t("Своя страна","Private country"):t("Общая страна","Shared country"),Color.WHITE);
                text(batch,new Box(p.x,p.y+h*2,p.w,h),t("Игроки: ","Players: ")+KreyliCountrySeats.remoteOccupied(target)+" / "+limit,Color.WHITE);
            }else{
                button(batch,SOLO,t("Своя страна","Private country"),selected==1);
                button(batch,SHARED,t("Общая страна","Shared country"),selected>1);
                if(selected>1){
                    button(batch,LESS,"-",false);button(batch,MORE,"+",false);
                    text(batch,new Box(p.x,p.y+h*2+CFG.PADD,p.w,h),t("Мест: ","Seats: ")+selected,Color.WHITE);
                }
            }
            button(batch,CONFIRM,existing?t("Присоединиться","Join"):t("Выбрать","Choose"),false);
            button(batch,BACK,t("Назад","Back"),false);
        }finally{batch.setPackedColor(color);}
    }
    private static void button(SpriteBatch batch,int index,String label,boolean selected){
        Box b=control(index);fill(batch,b,BUTTON);
        text(batch,b,label,selected?Color.GOLD:Color.WHITE);
    }
    private static void text(SpriteBatch batch,Box b,String label,Color color){
        label=MP_UI.fit(label,b.w-CFG.PADD*2);
        batch.setColor(Color.WHITE);
        Renderer.drawTextWithShadow(batch,MP_UI.font(),label,b.x+(b.w-MP_UI.textWidth(label))/2,b.y+(b.h-CFG.TEXT_HEIGHT_DEFAULT_SMALL)/2,color);
    }
    private static void fill(SpriteBatch batch,Box b,Color color){batch.setColor(color);Images.pix.draw(batch,b.x,b.y,b.w,b.h);}
}
