package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.CFG;
import age.of.civilizations2.jakowski.lukasz.Menu;
import age.of.civilizations2.jakowski.lukasz.Button.Classic.Button_Classic_LR_Main;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/** Keep the game's menu actions while positioning the main controls in one centered column. */
final class KreyliMainMenu {
    private static final int LOGO_INDEX=6;
    private static final int QUICK_INDEX=14;

    private KreyliMainMenu(){}

    static void layout(Menu menu){
        if(menu.menuElem.size()<=QUICK_INDEX)return;
        int gap=CFG.PADD;
        int width=Math.min(CFG.GAMEWIDTH-gap*4,menu.getMenuElem(0).getWidthE());
        int logoHeight=Math.min(CFG.GAMEHEIGHT/4,CFG.BUTTON_H*3);
        int row=Math.max(1,Math.min(CFG.BUTTON_H,(CFG.GAMEHEIGHT-logoHeight-gap*10)/6));
        int x=(CFG.GAMEWIDTH-width)/2;
        int y=(CFG.GAMEHEIGHT-logoHeight-row*6-gap*6)/2;

        if(!(menu.getMenuElem(LOGO_INDEX) instanceof Logo))
            menu.menuElem.set(LOGO_INDEX,new Logo(x,y,width,logoHeight));
        var logo=menu.getMenuElem(LOGO_INDEX);
        logo.setPosX(x);
        logo.setPosY(y);
        logo.setWidthE(width);
        logo.setHeightE(logoHeight);

        int buttonTop=y+logoHeight+gap;
        int half=(width-gap)/2;
        for(int index=0;index<LOGO_INDEX;index++){
            var button=menu.getMenuElem(index);
            button.setPosX(x);
            button.setPosY(buttonTop+index*(row+gap));
            button.setWidthE(index==1?half:width);
            button.setHeightE(row);
        }
        var quick=menu.getMenuElem(QUICK_INDEX);
        quick.setPosX(x+half+gap);
        quick.setPosY(buttonTop+row+gap);
        quick.setWidthE(width-half-gap);
        quick.setHeightE(row);
        menu.updateMenuElements_IsInView();
        menu.updateMenuElements_IsInView_X();
    }

    private static final class Logo extends Button_Classic_LR_Main {
        Logo(int x,int y,int width,int height){super("",-1,x,y,width,height,true);}
        @Override public void drawButtonBGE(SpriteBatch batch,int x,int y,boolean selected){
            KreyliSplash.drawLogo(batch,x+getPosXE(),y+getPosY(),getWidthE(),getHeightE(),selected?.90f:1f);
        }
        @Override public void drawTextE(SpriteBatch batch,int x,int y,boolean active){
        }
    }
}
