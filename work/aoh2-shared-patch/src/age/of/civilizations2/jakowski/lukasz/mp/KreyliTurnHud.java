package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.*;
import age.of.civilizations2.jakowski.lukasz.Button.MenuElemUI;
import age.of.civilizations2.jakowski.lukasz.Button.Game.Button_Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import java.util.EnumMap;

import static age.of.civilizations2.jakowski.lukasz.mp.KreyliText.t;

/** Turn controls anchored to the rendered native Ready button and date. */
public final class KreyliTurnHud {
    private enum Action { OPEN, FEWER, MORE, PROPOSE, ACCEPT, DECLINE, CANCEL }
    private record Box(int x,int y,int width,int height) {
        boolean contains(int px,int py){return px>=x&&px<x+width&&py>=y&&py<y+height;}
    }

    private static final Color PANEL=new Color(0x101b2af6), BORDER=new Color(0xc39648ff);
    private static final Color BUTTON=new Color(0x17253bff), BUTTON_HOVER=new Color(0x2b405aff);
    private static final EnumMap<Action,Box> hits=new EnumMap<>(Action.class);
    private static Box nativeReady,nativeDate;
    private static Button_Game skin;
    private static boolean choosing;
    private static Action pressed;
    private static float slide;
    private static String lastReadiness="",lastProposal="";
    private static int lastReadySoundTurn=-1;
    private static long lastVoteSound;
    private static String shownReadiness="",shownVote="";

    private KreyliTurnHud(){}

    public static void anchorReady(MenuElemUI button,int x,int y){
        if(MP.playing())nativeReady=absolute(button,x,y);
    }

    public static void anchorDate(Menu menu,int x,int y){
        if(!MP.playing())return;
        x+=menu.getMenuPosX();
        y+=menu.getMenuPosY();
        Box date=absolute(menu.getMenuElem(4),x,y);
        Box turn=absolute(menu.getMenuElem(5),x,y);
        nativeDate=date.x<turn.x?date:turn;
    }

    private static Box absolute(MenuElemUI element,int x,int y){
        return new Box(x+element.getPosXE(),y+element.getPosY(),element.getWidthE(),element.getHeightE());
    }

    public static void update(){
        String readiness=MP.playing()?KreyliTurns.readinessStatus():"";
        String vote=MP.playing()?KreyliTurns.skipStatus():"";
        if(!shownReadiness.equals(readiness)||!shownVote.equals(vote)){
            shownReadiness=readiness;
            shownVote=vote;
            CFG.setRenderO(true);
        }
    }

    public static void draw(SpriteBatch batch){
        hits.clear();
        Box ready=nativeReady,date=nativeDate;
        nativeReady=nativeDate=null;
        if(!MP.playing()||!CFG.menus.getInGameView()){
            choosing=false;
            slide=0;
            lastReadySoundTurn=-1;
            lastVoteSound=0;
            return;
        }
        float color=batch.getPackedColor();
        try{
            boolean canChoose=ready!=null&&MP.inputOrders()&&!MP.hasBattleReports()&&!KreyliTurns.hasSkipVote();
            if(canChoose)drawReadyControls(batch,ready);
            else choosing=false;
            if(date!=null)drawNotice(batch,date);
        }finally{batch.setPackedColor(color);}
    }

    private static void drawReadyControls(SpriteBatch batch,Box ready){
        int gap=Math.max(3,CFG.PADD);
        int width=Math.min(ready.width/2,ready.height*2/3);
        if(ready.x<width||width<8)return;
        Box arrow=new Box(ready.x-width,ready.y,width,ready.height);
        button(batch,arrow,"",Action.OPEN);
        int center=arrow.x+arrow.width/2,middle=arrow.y+arrow.height/2;
        int size=Math.max(3,arrow.width/7);
        for(int i=0;i<=size;i++){
            fill(batch,center-i,middle-size/2+i,2,2,Color.WHITE);
            fill(batch,center+i,middle-size/2+i,2,2,Color.WHITE);
        }
        if(!choosing)return;
        int popupWidth=Math.min(ready.x+ready.width-gap,Math.max(ready.width+width,CFG.BUTTON_W*2));
        int x=ready.x+ready.width-popupWidth;
        int height=Math.max(ready.height/2,CFG.TEXT_HEIGHT_DEFAULT_SMALL+gap*2);
        int y=Math.max(gap,ready.y-height*2-gap*4);
        panel(batch,new Box(x,y,popupWidth,height*2+gap*3));
        int innerWidth=popupWidth-gap*2;
        int counterY=y+gap;
        button(batch,new Box(x+gap,counterY,height,height),"-",Action.FEWER);
        label(batch,new Box(x+gap+height,counterY,innerWidth-height*2,height),
                t("Ходов: ","Turns: ")+KreyliTurns.selectedSkipCount()+" / "+KreyliSkipVote.MAX_TURNS,Color.WHITE);
        button(batch,new Box(x+popupWidth-gap-height,counterY,height,height),"+",Action.MORE);
        button(batch,new Box(x+gap,counterY+height+gap,innerWidth,height),t("Предложить","Propose"),Action.PROPOSE);
    }

    private static void drawNotice(SpriteBatch batch,Box date){
        String readiness=KreyliTurns.readinessStatus();
        String proposal=KreyliTurns.skipStatus();
        boolean responding=KreyliTurns.skipCanRespond(),cancelling=KreyliTurns.skipCanCancel();
        long vote=KreyliTurns.activeSkipId();
        if(responding&&vote!=lastVoteSound){
            lastVoteSound=vote;
            chime();
        }else if(!readiness.isEmpty()&&lastReadySoundTurn!=GameCalendar.TURNID){
            lastReadySoundTurn=GameCalendar.TURNID;
            chime();
        }
        boolean visible=!readiness.isEmpty()||!proposal.isEmpty();
        if(visible){lastReadiness=readiness;lastProposal=proposal;}
        float step=Math.min(0.3f,Gdx.graphics.getDeltaTime())*5;
        slide=Math.max(0,Math.min(1,slide+(visible?step:-step)));
        if(slide==0)return;
        if(slide<1)CFG.setRenderO(true);
        int gap=Math.max(4,CFG.PADD);
        int width=Math.min(date.x-gap*2,Math.max(CFG.BUTTON_W*3,MP_UI.textWidth(lastProposal)+gap*4));
        if(width<CFG.BUTTON_W)return;
        int row=Math.max(24,CFG.TEXT_HEIGHT_DEFAULT_SMALL+gap*2);
        int y=Math.max(gap,date.y);
        int x=Math.max(gap,date.x-gap-width-Math.round((1-slide)*(1-slide)*row*2));
        boolean voting=responding||cancelling;
        int rows=(lastReadiness.isEmpty()?0:1)+(lastProposal.isEmpty()?0:1)+(voting?2:0);
        panel(batch,new Box(x,y,width,rows*row+gap*2));
        int textY=y+gap;
        if(!lastReadiness.isEmpty()){
            label(batch,new Box(x+gap,textY,width-gap*2,row),lastReadiness,Color.GOLD);
            textY+=row;
        }
        if(voting){
            label(batch,new Box(x+gap,textY,width-gap*2,row),t("Голосование за пропуск ходов","Vote to skip turns"),Color.GOLD);
            textY+=row;
        }
        if(!lastProposal.isEmpty()){
            label(batch,new Box(x+gap,textY,width-gap*2,row),lastProposal,Color.WHITE);
            textY+=row;
        }
        if(responding){
            int half=(width-gap*3)/2;
            button(batch,new Box(x+gap,textY,half,row),t("Принять","Accept"),Action.ACCEPT);
            button(batch,new Box(x+half+gap*2,textY,half,row),t("Отклонить","Decline"),Action.DECLINE);
        }else if(cancelling){
            button(batch,new Box(x+gap,textY,width-gap*2,row),t("Отменить предложение","Cancel proposal"),Action.CANCEL);
        }
    }

    private static void chime(){
        if(CFG.SFXManager!=null)CFG.SFXManager.playSound(SFXManager.SFX_EVENT);
    }

    private static void panel(SpriteBatch batch,Box box){
        fill(batch,box.x,box.y,box.width,box.height,BORDER);
        fill(batch,box.x+1,box.y+1,box.width-2,box.height-2,PANEL);
    }

    private static void fill(SpriteBatch batch,int x,int y,int width,int height,Color color){
        batch.setColor(color);
        Images.pix.draw(batch,x,y,width,height);
    }

    private static void label(SpriteBatch batch,Box box,String text,Color color){
        if(text.isEmpty())return;
        String fitted=MP_UI.fit(text,box.width-CFG.PADD*2);
        batch.setColor(Color.WHITE);
        Renderer.drawTextWithShadow(batch,MP_UI.font(),fitted,box.x+(box.width-MP_UI.textWidth(fitted))/2,
                box.y+(box.height-CFG.TEXT_HEIGHT_DEFAULT_SMALL)/2,color);
    }

    private static void button(SpriteBatch batch,Box box,String text,Action action){
        hits.put(action,box);
        boolean hovered=box.contains(Gdx.input.getX(),Gdx.input.getY());
        if(action==Action.OPEN){
            if(skin==null)skin=new Button_Game("",0,0,1,1,true);
            skin.setWidthE(box.width);
            skin.setHeightE(box.height);
            skin.setIsHovered(hovered);
            batch.setColor(Color.WHITE);
            skin.drawButtonBGE(batch,box.x,box.y,action==pressed);
        }else{
            fill(batch,box.x,box.y,box.width,box.height,BORDER);
            fill(batch,box.x+1,box.y+1,box.width-2,box.height-2,hovered||action==pressed?BUTTON_HOVER:BUTTON);
        }
        label(batch,box,text,Color.WHITE);
    }

    public static boolean touchDown(int x,int y,int pointer,int button){
        if(pointer!=0||button!=Input.Buttons.LEFT||!MP.playing())return false;
        pressed=null;
        for(var hit:hits.entrySet())if(hit.getValue().contains(x,y)){pressed=hit.getKey();break;}
        if(pressed!=null)CFG.setRenderO(true);
        return pressed!=null;
    }

    public static boolean touchUp(int x,int y,int pointer,int button){
        if(pointer!=0||pressed==null)return false;
        Action action=pressed;
        pressed=null;
        Box box=hits.get(action);
        if(button==Input.Buttons.LEFT&&MP.playing()&&box!=null&&box.contains(x,y)){
            switch(action){
                case OPEN -> choosing=!choosing;
                case FEWER -> KreyliTurns.changeSkip(-1);
                case MORE -> KreyliTurns.changeSkip(1);
                case PROPOSE -> {choosing=false;KreyliTurns.proposeSkip();}
                case ACCEPT -> KreyliTurns.acceptSkip();
                case DECLINE -> KreyliTurns.rejectSkip();
                case CANCEL -> KreyliTurns.cancelSkip();
            }
        }
        CFG.setRenderO(true);
        return true;
    }
}
