package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.CFG;
import age.of.civilizations2.jakowski.lukasz.Menu;
import age.of.civilizations2.jakowski.lukasz.Renderer;
import age.of.civilizations2.jakowski.lukasz.SFXManager;
import age.of.civilizations2.jakowski.lukasz.Button.Classic.Button_Classic_LR_Main;
import age.of.civilizations2.jakowski.lukasz.Button.NewGame.Button_InGameBox;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import java.util.Locale;

import static age.of.civilizations2.jakowski.lukasz.mp.KreyliText.t;

/** Native menu controls for Steam Quick Match discovery. */
public final class KreyliQuickSearch {
    private static final Color GOLD=new Color(.83f,.69f,.42f,1);
    private static Button_InGameBox skin;
    private static InputProcessor previous,modal;
    private static boolean open,rang;

    private KreyliQuickSearch(){}

    public static void installMainButton(Menu menu){
        if(menu.menuElem.size()<15)return;
        int index=menu.menuElem.size()-1;
        if(menu.menuElem.get(index) instanceof MainButton button){
            button.setTextE(title());
            return;
        }
        var solo=menu.menuElem.get(1);
        int whole=solo.getWidthE(), left=(whole-CFG.PADD)/2;
        solo.setWidthE(left);
        menu.menuElem.set(index,new MainButton(solo.getPosXE()+left+CFG.PADD,solo.getPosY(),
                whole-left-CFG.PADD,solo.getHeightE()));
        menu.updateMenuElements_IsInView();
        menu.updateMenuElements_IsInView_X();
    }

    private static String title(){return t("Быстрая игра","Quick Match");}

    private static final class MainButton extends Button_Classic_LR_Main {
        MainButton(int x,int y,int w,int h){super(title(),-1,x,y,w,h,true);}
        @Override public void actionElem(int index){begin();}
    }

    private static void begin(){
        if(open)return;
        if(!KreyliSteam.available()){
            MP.toast(t("Для быстрой игры нужен запущенный Steam","Quick Match requires Steam"),false);
            return;
        }
        if(!KreyliSteam.startQuick(players->{
            try{
                String route=KreyliQuickGame.host(players);
                if(route!=null)close(false);
                return route;
            }catch(RuntimeException failure){
                if(MP.isHost())MP.stop();
                throw failure;
            }
        },KreyliQuickSearch::join)){
            MP.toast(t("Поиск уже запущен или игра занята","A search or session is already active"),false);
            return;
        }
        open=true;rang=false;
        previous=Gdx.input.getInputProcessor();
        modal=new InputAdapter(){
            private int pressed;
            @Override public boolean keyDown(int key){
                if(key==Input.Keys.ESCAPE)close(true);
                else if(key==Input.Keys.ENTER&&canAccept())KreyliSteam.acceptQuick();
                return true;
            }
            @Override public boolean keyUp(int key){return true;}
            @Override public boolean touchDown(int x,int y,int pointer,int button){
                pressed=pointer==0&&button==Input.Buttons.LEFT?target(x,y):0;
                return true;
            }
            @Override public boolean touchUp(int x,int y,int pointer,int button){
                int action=target(x,y);
                if(pointer==0&&button==Input.Buttons.LEFT&&action==pressed){
                    if(action==1&&canAccept())KreyliSteam.acceptQuick();
                    else if(action==2)close(true);
                }
                pressed=0;
                return true;
            }
            @Override public boolean touchDragged(int x,int y,int pointer){return true;}
            @Override public boolean scrolled(float x,float y){return true;}
        };
        Gdx.input.setInputProcessor(modal);
        CFG.setRenderO(true);
    }

    private static void join(String route){
        String[] fields=route.split("\n",-1);
        if(fields.length!=4){close(true);return;}
        try{
            int players=Integer.parseInt(fields[3]);
            if(players<KreyliQuickQueue.MIN_PLAYERS||players>KreyliQuickQueue.MAX_PLAYERS){close(true);return;}
            close(false);
            KreyliQuickGame.join(fields[0],fields[1],fields[2],players);
        }catch(NumberFormatException badRoute){close(true);}
    }

    private static boolean canAccept(){
        var status=KreyliSteam.quickStatus();
        return "accept".equals(status.phase())&&!status.localAccepted()&&status.remaining()>0;
    }

    private static void close(boolean cancel){
        if(cancel)KreyliSteam.cancelQuick();
        if(modal!=null&&Gdx.input.getInputProcessor()==modal)Gdx.input.setInputProcessor(previous);
        modal=previous=null;
        open=false;
        CFG.setRenderO(true);
    }

    private record Box(int x,int y,int w,int h){
        boolean contains(int px,int py){return px>=x&&py>=y&&px<x+w&&py<y+h;}
    }
    private static Box panel(){
        int width=Math.min(CFG.GAMEWIDTH-CFG.PADD*4,Math.max(400,CFG.BUTTON_W*4));
        int height=CFG.BUTTON_H*4+CFG.PADD*8;
        return new Box((CFG.GAMEWIDTH-width)/2,(CFG.GAMEHEIGHT-height)/2,width,height);
    }
    private static Box button(Box p,int action){
        int gap=CFG.PADD*2, width=(p.w-gap*3)/2;
        return new Box(p.x+gap+(action-1)*(width+gap),p.y+p.h-CFG.BUTTON_H-gap,width,CFG.BUTTON_H);
    }
    private static int target(int rawX,int rawY){
        int x=rawX*CFG.GAMEWIDTH/Math.max(1,Gdx.graphics.getWidth());
        int y=rawY*CFG.GAMEHEIGHT/Math.max(1,Gdx.graphics.getHeight());
        Box p=panel();
        if(button(p,1).contains(x,y))return 1;
        if(button(p,2).contains(x,y))return 2;
        return 0;
    }

    public static void draw(SpriteBatch batch){
        if(!open)return;
        if(!CFG.menus.getIn_MainMenu()){close(true);return;}
        var status=KreyliSteam.quickStatus();
        boolean found="accept".equals(status.phase()),failed="failed".equals(status.phase());
        if(found&&!rang){
            rang=true;
            if(CFG.SFXManager!=null)CFG.SFXManager.playSound(SFXManager.SFX_EVENT);
        }
        CFG.setRenderO(true);
        float previousColor=batch.getPackedColor();
        try{
            Box p=panel();
            MP_UI.box(batch,0,0,CFG.GAMEWIDTH,CFG.GAMEHEIGHT,.58f);
            MP_UI.box(batch,p.x,p.y,p.w,p.h,.96f);
            int h=CFG.BUTTON_H;
            text(batch,new Box(p.x,p.y,p.w,h),title(),GOLD);
            if(failed){
                String message="relay".equals(status.error())?t("Не удалось открыть сетевую комнату","Could not open an online room"):
                        t("Поиск завершён без матча","Search ended without a match");
                text(batch,new Box(p.x,p.y+h,p.w,h),message,Color.WHITE);
            }else{
                String state=found?t("Матч найден","Match found"):t("Поиск игроков","Finding players");
                int count=found?status.accepted():status.members();
                int total=found?status.members():KreyliQuickQueue.MAX_PLAYERS;
                String timer=clock(found||status.remaining()>0?status.remaining():status.elapsed());
                text(batch,new Box(p.x,p.y+h,p.w,h),state+"   "+timer,Color.WHITE);
                text(batch,new Box(p.x,p.y+h*2,p.w,h),t("Игроки","Players")+"   "+count+" / "+total,GOLD);
            }
            if(found)drawButton(batch,button(p,1),status.localAccepted()?t("Ожидаем игроков","Waiting for players"):t("Принять","Accept"),canAccept());
            drawButton(batch,button(p,2),failed?t("Закрыть","Close"):t("Отмена","Cancel"),true);
        }finally{batch.setPackedColor(previousColor);}
    }
    private static String clock(int seconds){return String.format(Locale.ROOT,"%02d:%02d",seconds/60,seconds%60);}
    private static void drawButton(SpriteBatch batch,Box box,String label,boolean enabled){
        if(skin==null)skin=new Button_InGameBox("",-1,0,0,1,1,true);
        skin.setWidthE(box.w);skin.setHeightE(box.h);skin.setClickable(enabled);
        batch.setColor(Color.WHITE);
        skin.drawButtonBGE(batch,box.x,box.y,false);
        text(batch,box,label,enabled?Color.WHITE:Color.GRAY);
    }
    private static void text(SpriteBatch batch,Box box,String label,Color color){
        label=MP_UI.fit(label,box.w-CFG.PADD*2);
        batch.setColor(Color.WHITE);
        Renderer.drawTextWithShadow(batch,MP_UI.font(),label,box.x+(box.w-MP_UI.textWidth(label))/2,
                box.y+(box.h-CFG.TEXT_HEIGHT_DEFAULT_SMALL)/2,color);
    }
}
