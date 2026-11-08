package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.CFG;
import age.of.civilizations2.jakowski.lukasz.Menu;
import age.of.civilizations2.jakowski.lukasz.Button.MenuElemUI;
import age.of.civilizations2.jakowski.lukasz.Button.NewGame.Button_InGameBox;
import java.util.Map;
import java.util.WeakHashMap;

/** Adds an invite action to each freshly rebuilt native player table. */
public final class KreyliMenus {
    private static final Map<Menu,Integer> inviteIndexes=new WeakHashMap<>();
    private KreyliMenus(){}

    public static void appendInvite(Menu menu){
        if(inviteIndexes.containsKey(menu))return;
        fitPlayerColumns(menu);
        int bottom=CFG.PADD;
        for(MenuElemUI item:menu.menuElem)bottom=Math.max(bottom,item.getPosY()+item.getHeightE()+CFG.PADD);
        int index=menu.menuElem.size();
        MenuElemUI button=new Button_InGameBox("Пригласить друзей (Steam)",-1,CFG.PADD,bottom,menu.getWidthM()-CFG.PADD*2,true);
        menu.menuElem.add(button);
        bottom+=button.getHeightE()+CFG.PADD;
        MenuElemUI discord=new Button_InGameBox("Discord проекта",-1,CFG.PADD,bottom,menu.getWidthM()-CFG.PADD*2,true);
        menu.menuElem.add(discord);
        bottom+=discord.getHeightE()+CFG.PADD;
        for(String text:new String[]{KreyliTurns.skipChoice(),KreyliTurns.skipStatus(),"Отменить пропуск"}){
            MenuElemUI item=new Button_InGameBox(text,-1,CFG.PADD,bottom,menu.getWidthM()-CFG.PADD*2,true);menu.menuElem.add(item);bottom+=item.getHeightE()+CFG.PADD;
        }
        menu.iMenuElemsSize=menu.menuElem.size();
        inviteIndexes.put(menu,index);
        menu.setHeight(Math.min(bottom,CFG.GAMEHEIGHT-menu.getPosY()-CFG.BUTTON_H));
        menu.updateScrollable();
        menu.updateMenuElements_IsInView();
        menu.updateMenuElements_IsInView_X();
        menu.getTitleM().setText("Kreyli Coop AOH2 | Игроки: "+MP.rosterNicks.length+" / 2");
    }

    private static void fitPlayerColumns(Menu menu){
        int count=menu.menuElem.size();if(count<5||count%5!=0)return;
        int available=CFG.GAMEWIDTH-CFG.PADD*8;
        int[] widths=new int[5];
        for(int i=0;i<count;i++)widths[i%5]=Math.max(widths[i%5],MP_UI.textWidth(menu.menuElem.get(i).getTextE())+CFG.PADD*4+(i>=5&&i%5==0?CFG.TEXT_HEIGHT_DEFAULT*2:0));
        int numeric=0;for(int c=1;c<5;c++)numeric+=widths[c];
        widths[0]=Math.max(120,Math.min(widths[0],available-numeric));
        int total=0;for(int w:widths)total+=w;
        if(total>available)return;
        menu.setWidth(total+CFG.PADD*2);menu.setPosX((CFG.GAMEWIDTH-menu.getWidthM())/2);
        for(int i=0;i<count;i++){
            int col=i%5,x=CFG.PADD;for(int c=0;c<col;c++)x+=widths[c];
            var item=menu.menuElem.get(i);item.setPosX(x);item.setWidthE(widths[col]);
            if(col==0){String name=item.getTextE();while(name.length()>1&&MP_UI.textWidth(name+"…")>widths[0]-CFG.PADD*4-(i>=5?CFG.TEXT_HEIGHT_DEFAULT*2:0))name=name.substring(0,name.length()-1);if(!name.equals(item.getTextE()))item.setTextE(name+"…");}
        }
    }

    /** Returns true only for the new button, preserving all native player actions. */
    public static boolean inviteAction(Menu menu,int index){
        Integer expected=inviteIndexes.get(menu);
        if(expected==null)return false;
        if(index==expected+2){KreyliTurns.chooseSkip();updateSkipLabels();return true;}
        if(index==expected+3){KreyliTurns.voteAction();updateSkipLabels();return true;}
        if(index==expected+4){KreyliTurns.cancelSkip();updateSkipLabels();return true;}
        if(index==expected+1){KreyliLobby.discord();return true;}
        if(expected!=index)return false;
        KreyliSteam.inviteFriends();
        return true;
    }
    public static void updateSkipLabels(){for(var entry:inviteIndexes.entrySet()){
        Menu menu=entry.getKey();int start=entry.getValue();if(menu.menuElem.size()<start+5)continue;
        menu.menuElem.get(start+2).setTextE(KreyliTurns.skipChoice());menu.menuElem.get(start+3).setTextE(KreyliTurns.skipStatus());
    }}
}

