package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.CFG;
import age.of.civilizations2.jakowski.lukasz.Menu;
import age.of.civilizations2.jakowski.lukasz.Renderer;
import age.of.civilizations2.jakowski.lukasz.Button.NewGame.Button_InGameBox;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import java.util.Map;
import java.util.WeakHashMap;

/** Multiplayer turn controls in the native menu above the minimap. */
public final class KreyliTurnHud {
    private static final Map<Menu, KreyliTurnHud> panels = new WeakHashMap<>();
    private final int chatX, chatY, chatWidth, chatHeight, gap, rowHeight, width;
    private boolean choosing;
    private final Control ready = new Control(null);
    private final Control proposal = new Control(null);
    private final Control arrow = new Control(() -> choosing=!choosing);
    private final Control fewer = new Control(() -> KreyliTurns.changeSkip(-1));
    private final Control more = new Control(() -> KreyliTurns.changeSkip(1));
    private final Control submit = new Control(() -> {choosing=false;KreyliTurns.proposeSkip();});
    private final Control accept = new Control(KreyliTurns::acceptSkip);
    private final Control decline = new Control(KreyliTurns::rejectSkip);
    private final Control cancel = new Control(KreyliTurns::cancelSkip);
    private float slide;
    private String lastPanelText="";
    private int previousLift = -1;
    private boolean previousExpanded, previousControls;

    private KreyliTurnHud(Menu menu) {
        chatX = menu.getPosX();
        chatY = menu.getPosY();
        chatWidth = menu.getWidthM();
        chatHeight = menu.getHeightM();
        gap = Math.max(3, CFG.PADD);
        rowHeight = Math.max(MP_UI.toggleSize(), CFG.TEXT_HEIGHT_DEFAULT_SMALL + gap * 2);
        width = Math.min(chatX - gap, Math.max(CFG.BUTTON_W * 2, MP_UI.textWidth(KreyliText.t("Готов к следующему ходу","Ready for the next turn")) + gap * 3));
        for (Control control : new Control[]{ready, proposal, arrow, fewer, more, submit, accept, decline, cancel}) {
            control.setHeightE(rowHeight);
            control.setVisibleE(false);
            menu.menuElem.add(control);
        }
        menu.iMenuElemsSize = menu.menuElem.size();
    }

    public static Menu attach(Menu menu) {
        panels.put(menu, new KreyliTurnHud(menu));
        return menu;
    }

    public static void update() {
        if (CFG.menus == null || !CFG.menus.getInGameView() || CFG.menus.INGAME_MP_CHAT_TOGGLE < 0) return;
        Menu menu = CFG.menus.getInGameMenu(CFG.menus.INGAME_MP_CHAT_TOGGLE);
        KreyliTurnHud panel = panels.get(menu);
        if (panel != null) panel.update(menu);
    }

    private void update(Menu menu) {
        boolean playing = MP.playing() && MP.inputOrders() && !MP.hasBattleReports();
        boolean armyMenu = CFG.menus.getInGame_ProvinceMoveUnits_Visible()
                || CFG.menus.getInGame_ProvinceRecruit_Visible()
                || CFG.menus.getInGame_ProvinceRecruitInstantly_Visible()
                || CFG.menus.getInGame_ProvinceDisband_Visible();
        String readiness = playing ? KreyliTurns.readinessStatus() : "";
        boolean activeVote = MP.playing()&&KreyliTurns.hasSkipVote();
        String voteText = MP.playing()?KreyliTurns.skipStatus():"";
        boolean showing = !voteText.isEmpty();
        if(showing)lastPanelText=voteText;
        float animationStep=Math.min(1,Gdx.graphics.getDeltaTime()*7);
        slide=Math.max(0,Math.min(1,slide+(showing?animationStep:-animationStep)));
        boolean votePanel=showing||slide>0;
        if(votePanel&&(showing?slide<1:slide>0))CFG.setRenderO(true);
        boolean respond = playing&&activeVote&&KreyliTurns.skipCanRespond();
        boolean canCancel = playing&&activeVote&&KreyliTurns.skipCanCancel();
        if(!playing||activeVote)choosing=false;
        boolean controls=playing&&!armyMenu&&!activeVote;
        boolean chooser=controls&&choosing;
        int upperRows=(readiness.isEmpty()?0:1)+(votePanel?1:0)+(chooser?1:0);
        int lift = upperRows * (rowHeight + gap);
        int baseHeight = controls||respond||canCancel ? Math.max(rowHeight + 1, chatHeight) : chatHeight;
        boolean expanded = controls || respond || canCancel || upperRows > 0;
        if (previousLift != lift || previousExpanded != expanded || previousControls != controls) {
            menu.setPosX(expanded ? chatX - width - gap : chatX);
            menu.setPosY(chatY - lift);
            menu.setWidth(expanded ? width + gap + chatWidth : chatWidth);
            menu.setHeight(baseHeight + lift);
            menu.getMenuElem(0).setPosX(expanded ? width + gap : 0);
            menu.getMenuElem(0).setPosY(lift + 1);
            previousLift = lift;
            previousExpanded = expanded;
            previousControls = controls;
            CFG.setRenderO(true);
        }

        int row = 1;
        ready.place(0, row, width, readiness, !readiness.isEmpty());
        if (!readiness.isEmpty()) row += rowHeight + gap;
        int shift=(int)((1-slide)*(1-slide)*(width+chatWidth));
        proposal.place(shift,row,width,lastPanelText,votePanel);
        if(votePanel)row+=rowHeight+gap;

        int first = width / 2 - gap;
        int small=Math.min(rowHeight,width/4);
        arrow.place(width-small,lift+1,small,choosing?"▾":"▸",controls);
        fewer.place(0,row,small,"−",chooser);
        more.place(small+gap,row,small,"+",chooser);
        submit.place(small*2+gap*2,row,width-small*2-gap*2,KreyliText.t("Пропустить: ","Skip: ")+KreyliTurns.selectedSkipCount(),chooser);
        accept.place(0,lift+1,first,KreyliText.t("Принять","Accept"),respond);
        decline.place(first+gap,lift+1,width-first-gap,KreyliText.t("Отказать","Reject"),respond);
        cancel.place(0,lift+1,width,KreyliText.t("Отменить предложение","Cancel proposal"),canCancel);
        menu.updateMenuElements_IsInView();
        menu.updateMenuElements_IsInView_X();
    }

    private static final class Control extends Button_InGameBox {
        private final Runnable action;

        Control(Runnable action) {
            super("", -1, 0, 0, 1, action != null);
            this.action = action;
        }

        void place(int x, int y, int width, String text, boolean visible) {
            if (getVisibleE() != visible) {
                setVisibleE(visible);
                CFG.setRenderO(true);
            }
            if (!visible) return;
            setPosX(x);
            setPosY(y);
            setWidthE(width);
            if (!text.equals(getTextE())) {
                setTextE(text);
                CFG.setRenderO(true);
            }
        }

        @Override public void actionElem(int index) {
            if (action != null && getVisibleE()) action.run();
        }

        @Override public void drawTextE(SpriteBatch batch, int x, int y, boolean active) {
            String text = getTextE();
            int available = getWidthE() - CFG.PADD * 2;
            int suffixAt = Math.max(text.lastIndexOf(KreyliText.t(" готов к ходу"," is ready")), text.indexOf(" · "));
            String fitted;
            if (action == null && suffixAt > 0 && MP_UI.textWidth(text) > available) {
                String suffix = text.substring(suffixAt);
                int prefixWidth = available - MP_UI.textWidth(suffix);
                fitted = prefixWidth > MP_UI.textWidth("…")
                        ? MP_UI.fit(text.substring(0, suffixAt), prefixWidth) + suffix
                        : MP_UI.fit(suffix.stripLeading(), available);
            } else {
                fitted = MP_UI.fit(text, available);
            }
            Renderer.drawTextWithShadow(batch, MP_UI.font(), fitted,
                    x + getPosXE() + (getWidthE() - MP_UI.textWidth(fitted)) / 2,
                    y + getPosY() + (getHeightE() - CFG.TEXT_HEIGHT_DEFAULT_SMALL) / 2,
                    action == null ? Color.GOLD : getColorE(active));
        }
    }
}
