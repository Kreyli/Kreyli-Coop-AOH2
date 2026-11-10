package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.CFG;
import age.of.civilizations2.jakowski.lukasz.Images;
import age.of.civilizations2.jakowski.lukasz.Menu;
import age.of.civilizations2.jakowski.lukasz.Renderer;
import age.of.civilizations2.jakowski.lukasz.View;
import age.of.civilizations2.jakowski.lukasz.Menus.Civilization.Menu_InGame_Civ;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.NumberFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import static age.of.civilizations2.jakowski.lukasz.mp.KreyliText.t;

/** Profile overlay on the same logical screen coordinates as the game UI. */
public final class KreyliProfileUi {
    private static final Color PANEL=new Color(.041f,.056f,.086f,.96f);
    private static final Color CONTROL=new Color(.12f,.17f,.23f,1);
    private static final Color SHADE=new Color(0,0,0,.56f);
    private static final Color MUTED=new Color(.7f,.77f,.84f,1);
    private static final NumberFormat NUMBERS=NumberFormat.getIntegerInstance(Locale.forLanguageTag("ru"));
    private enum Page {PROFILE,NICKNAME,AVATARS}
    private static boolean open, captured, importing;
    private static Page page=Page.PROFILE;
    private static String nickname="",message="";
    private static int selectedAvatar=-1;
    private static final Map<Box,String> countryPortraits=new LinkedHashMap<>();
    private static String viewedPlayer;
    private static Box countryNext;
    private static int countryCiv=-1,countryPage;
    private static InputProcessor previousInput;
    private static InputMultiplexer modalInput;

    private KreyliProfileUi(){}

    private record Box(int x,int y,int w,int h){
        boolean contains(int px,int py){return px>=x&&py>=y&&px<x+w&&py<y+h;}
    }

    private static boolean countryVisible(){
        if(CFG.menus==null||Menu_InGame_Civ.hideAnimation||CFG.menus.getInGameView_Options())return false;
        return CFG.menus.getIn_Game_CivilizationView()||
                CFG.menus.getInGameView()&&CFG.menus.getInGame_Civ_Info().getVisibleM();
    }
    private static boolean visible(){
        return CFG.menus!=null&&(CFG.menus.getInGameView()
                ||CFG.menus.getIn_Game_CivilizationView()||CFG.menus.getIn_MainMenu()||CFG.menus.getInView(View.eMP)
                ||CFG.menus.getInView(View.eMP_HOST)||CFG.menus.getInView(View.eMP_JOINING)
                ||CFG.menus.getInView(View.eMP_BROWSER));
    }

    private static int row(){return Math.max(CFG.BUTTON_H/2,CFG.TEXT_HEIGHT_DEFAULT_SMALL+CFG.PADD*2);}
    private static Box badge(){
        return new Box(CFG.PADD,CFG.PADD,Math.min(CFG.GAMEWIDTH/3,Math.max(CFG.BUTTON_W*2,row()*4)),row()+CFG.PADD);
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
    private static Box field(Box panel){
        int gap=CFG.PADD*2;
        return new Box(panel.x+gap,panel.y+row()*3,panel.w-gap*2,row()+gap);
    }
    private static Box upload(Box panel){
        Box field=field(panel);
        return new Box(field.x,panel.y+row()+CFG.PADD*2,field.w,row());
    }
    private static Box avatarCell(Box panel,int index){
        int gap=CFG.PADD*2;
        int width=(panel.w-gap*2)/8;
        int height=Math.min(width,row()*3/2);
        return new Box(panel.x+gap+width*(index%8),panel.y+row()*3+height*(index/8),width-gap/2,height-gap/2);
    }

    public static void draw(SpriteBatch batch){
        if(!visible()){close();return;}
        float previous=batch.getPackedColor();
        try{
            if(!CFG.menus.getInGameView()&&!CFG.menus.getIn_Game_CivilizationView()){
                countryPortraits.clear();
                player(batch,badge(),KreyliProfile.displayName(),KreyliProfile.avatar());
            }else if(!countryVisible())countryPortraits.clear();
            if(!open)return;
            fill(batch,new Box(0,0,CFG.GAMEWIDTH,CFG.GAMEHEIGHT),SHADE);
            Box panel=panel();
            fill(batch,panel,PANEL);
            if(page==Page.NICKNAME){drawNickname(batch,panel);return;}
            if(page==Page.AVATARS){drawAvatars(batch,panel);return;}
            int x=panel.x+CFG.PADD*2,y=panel.y+CFG.PADD*2;
            text(batch,t("Профиль игрока","Player profile"),x,y,Color.GOLD);
            y+=row();
            String name=viewedPlayer==null?KreyliProfile.displayName():viewedPlayer;
            player(batch,new Box(x,y,panel.w-CFG.PADD*4,row()+CFG.PADD),name,KreyliProfile.avatar(name));
            y+=row()*2;
            if(viewedPlayer==null){
                text(batch,KreyliProfile.steamLinked()?t("Профиль Steam привязан","Steam profile linked"):t("Локальный профиль","Local profile"),x,y,MUTED);
                button(batch,action(panel,0),t("Изменить ник","Edit nickname"));
                button(batch,action(panel,1),t("Аватары","Avatars"));
            }
            y+=row();
            KreyliProfile.Stats stats=KreyliProfile.stats(viewedPlayer);
            if(stats==null)text(batch,t("Статистика пока недоступна","Stats are not available yet"),x,y,MUTED);
            else{
                long seconds=stats.seconds();
                stat(batch,panel,y,t("Время в игре","Time played"),NUMBERS.format(seconds/3600)+t(" ч "," h ")+(seconds/60%60)+t(" мин"," min"));
                stat(batch,panel,y+row(),t("Завоёвано областей","Provinces conquered"),NUMBERS.format(stats.conquered()));
                stat(batch,panel,y+row()*2,t("Заработано денег","Money earned"),NUMBERS.format(stats.earned()));
                stat(batch,panel,y+row()*3,t("Призвано войск","Troops recruited"),NUMBERS.format(stats.recruited()));
            }
            button(batch,action(panel,2),t("Закрыть","Close"));
        }finally{batch.setPackedColor(previous);}
    }
    private static void drawNickname(SpriteBatch batch,Box panel){
        int x=panel.x+CFG.PADD*2,y=panel.y+CFG.PADD*2;
        text(batch,t("Изменить ник","Edit nickname"),x,y,Color.GOLD);
        text(batch,t("От 2 до 24 символов","2 to 24 characters"),x,y+row(),MUTED);
        Box field=field(panel);
        fill(batch,field,CONTROL);
        text(batch,MP_UI.fit(nickname+"|",field.w-CFG.PADD*2),field.x+CFG.PADD,baseline(field),Color.WHITE);
        if(!message.isEmpty())text(batch,message,x,field.y+field.h+CFG.PADD,MUTED);
        button(batch,action(panel,0),t("Сохранить","Save"));
        button(batch,action(panel,1),t("По умолчанию","Default"));
        button(batch,action(panel,2),t("Назад","Back"));
    }
    private static void drawAvatars(SpriteBatch batch,Box panel){
        int x=panel.x+CFG.PADD*2,y=panel.y+CFG.PADD*2;
        text(batch,t("Аватары","Avatars"),x,y,Color.GOLD);
        button(batch,upload(panel),importing?t("Выбор файла…","Choosing file…"):t("Загрузить с компьютера","Upload from computer"));
        if(KreyliProfile.avatarCount()==0)text(batch,t("Загрузите PNG или JPG до 2 МБ","Upload a PNG or JPG up to 2 MB"),x,panel.y+row()*3,MUTED);
        for(int i=0;i<KreyliProfile.avatarCount();i++){
            Box cell=avatarCell(panel,i);
            fill(batch,cell,CONTROL);
            int size=Math.min(cell.w,cell.h)-CFG.PADD;
            if(size>0){
                int left=cell.x+(cell.w-size)/2,top=cell.y+(cell.h-size)/2;
                drawAvatar(batch,left,top,size,KreyliProfile.avatarPreview(i));
                if(i==selectedAvatar)outline(batch,new Box(left,top,size,size));
            }
        }
        if(!message.isEmpty())text(batch,MP_UI.fit(message,panel.w-CFG.PADD*4),x,panel.y+row()*2+CFG.PADD*2,MUTED);
        button(batch,action(panel,0),t("По умолчанию","Default"));
        button(batch,action(panel,1),t("Выбрать","Select"));
        button(batch,action(panel,2),t("Назад","Back"));
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
    private static void outline(SpriteBatch batch,Box box){
        int stroke=Math.max(1,CFG.PADD/3);
        fill(batch,new Box(box.x,box.y,box.w,stroke),Color.GOLD);
        fill(batch,new Box(box.x,box.y+box.h-stroke,box.w,stroke),Color.GOLD);
        fill(batch,new Box(box.x,box.y+stroke,stroke,box.h-stroke*2),Color.GOLD);
        fill(batch,new Box(box.x+box.w-stroke,box.y+stroke,stroke,box.h-stroke*2),Color.GOLD);
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
            float scale=(float)size/Math.max(avatar.getRegionWidth(),avatar.getRegionHeight());
            float width=avatar.getRegionWidth()*scale,height=avatar.getRegionHeight()*scale;
            batch.draw(avatar,x+(size-width)/2,-y-(size+height)/2,width,height);
        }else{
            fill(batch,new Box(x,y,size,size),CONTROL);
            text(batch,"?",x+(size-MP_UI.textWidth("?"))/2,y+(size-CFG.TEXT_HEIGHT_DEFAULT_SMALL)/2,MUTED);
        }
    }

    private static int countryRowHeight(){return row()*2+CFG.PADD;}

    public static void reserveCountryPlayers(Menu menu){
        int bottom=menu.getHeightM();
        for(var element:menu.menuElem)bottom=Math.max(bottom,element.getPosY()+element.getHeightE()+CFG.PADD*2);
        // The native stats menu paints summary tiles outside its element bounds.
        int nativeSummaryHeight=row()*4+CFG.PADD;
        menu.setHeight(bottom+nativeSummaryHeight+countryRowHeight()+CFG.PADD);
    }

    /** Player section immediately below the native country statistics. */
    public static void drawCountry(Menu menu,SpriteBatch batch,int offsetX,int offsetY){
        countryPortraits.clear();
        countryNext=null;
        if(!menu.getVisibleM()||Menu_InGame_Civ.hideAnimation)return;
        int civ=CFG.getActiveCivInfoId();
        if(civ<=0)return;
        var players=KreyliProfile.player(civ);
        int height=countryRowHeight(),padding=Math.max(3,CFG.PADD);
        int x=menu.getPosX()+offsetX,y=menu.getPosY()+offsetY+menu.getHeightM()-height;
        int width=menu.getWidthM();
        if(players.isEmpty()){
            float previous=batch.getPackedColor();
            try{
                fill(batch,new Box(x,y,width,height),PANEL);
                text(batch,t("Игрок: —","Player: —"),x+padding,y+padding,MUTED);
            }finally{batch.setPackedColor(previous);}
            return;
        }
        int pageSize=players.size()>1?2:1;
        if(countryCiv!=civ){countryCiv=civ;countryPage=0;}
        int lastPage=(players.size()-1)/pageSize;
        countryPage=Math.min(countryPage,lastPage);
        float previous=batch.getPackedColor();
        try{
            fill(batch,new Box(x,y,width,height),PANEL);
            fill(batch,new Box(x,y,width,1),CONTROL);
            String title=players.size()==1?t("Игрок:","Player:"):t("Игроки:","Players:");
            if(lastPage>0)title+=" "+(countryPage+1)+"/"+(lastPage+1);
            text(batch,title,x+padding,y+padding,Color.GOLD);
            int top=y+padding+CFG.TEXT_HEIGHT_DEFAULT_SMALL+2;
            int itemHeight=height-(top-y)-padding;
            int nextWidth=lastPage>0?Math.max(itemHeight,28):0;
            int itemWidth=(width-padding*(pageSize+1)-nextWidth)/pageSize;
            for(int column=0;column<pageSize;column++){
                int index=countryPage*pageSize+column;
                if(index>=players.size())break;
                var controller=players.get(index);
                int left=x+padding+(itemWidth+padding)*column;
                Box card=new Box(left,top,itemWidth,itemHeight);
                player(batch,card,controller.nick(),controller.avatar());
                countryPortraits.put(card,controller.nick());
            }
            if(lastPage>0){
                countryNext=new Box(x+width-padding-nextWidth,top,nextWidth,itemHeight);
                button(batch,countryNext,">");
            }
        }finally{batch.setPackedColor(previous);}
    }
    private static String profileTarget(int x,int y){
        if(!CFG.menus.getInGameView()&&!CFG.menus.getIn_Game_CivilizationView())return badge().contains(x,y)?KreyliProfile.displayName():null;
        if(countryVisible())
            for(var entry:countryPortraits.entrySet())if(entry.getKey().contains(x,y))return entry.getValue();
        return null;
    }

    private static boolean countryNextAt(int x,int y){return countryVisible()&&countryNext!=null&&countryNext.contains(x,y);}

    public static boolean touchDown(int x,int y,int pointer,int button){
        if(pointer!=0||!visible())return false;
        captured=open||(button==Input.Buttons.LEFT&&(profileTarget(x,y)!=null||countryNextAt(x,y)));
        return captured;
    }
    public static boolean touchUp(int x,int y,int pointer,int button){
        if(pointer!=0||!captured)return false;
        captured=false;
        if(button!=Input.Buttons.LEFT||!visible())return true;
        if(!open){
            String target=profileTarget(x,y);
            if(target!=null)open(target);
            else if(countryNextAt(x,y)){
                int pages=(KreyliProfile.player(countryCiv).size()+1)/2;
                countryPage=pages>0?(countryPage+1)%pages:0;
            }
        }
        else click(x,y);
        CFG.setRenderO(true);
        return true;
    }
    private static void click(int x,int y){
        Box panel=panel();
        if(!panel.contains(x,y)||action(panel,2).contains(x,y)){back();return;}
        if(page==Page.NICKNAME){
            if(action(panel,0).contains(x,y))saveNickname();
            else if(action(panel,1).contains(x,y)){KreyliProfile.resetNickname();back();}
            return;
        }
        if(page==Page.AVATARS){
            if(upload(panel).contains(x,y)){chooseFile();return;}
            if(action(panel,0).contains(x,y)){KreyliProfile.selectAvatar(-1);back();return;}
            if(action(panel,1).contains(x,y)){
                if(selectedAvatar>=0){KreyliProfile.selectAvatar(selectedAvatar);back();}
                else message=t("Сначала выберите или загрузите аватар","Select or upload an avatar first");
                return;
            }
            for(int i=0;i<KreyliProfile.avatarCount();i++)if(avatarCell(panel,i).contains(x,y)){
                selectedAvatar=i;message="";return;
            }
            return;
        }
        if(viewedPlayer!=null)return;
        if(action(panel,0).contains(x,y)){page=Page.NICKNAME;nickname=KreyliProfile.nick();message="";}
        else if(action(panel,1).contains(x,y)){page=Page.AVATARS;selectedAvatar=KreyliProfile.selectedAvatar();message="";}
    }
    private static void back(){
        if(page==Page.PROFILE)close();
        else {page=Page.PROFILE;message="";CFG.setRenderO(true);}
    }
    private static void saveNickname(){
        String clean=KreyliIdentity.cleanNickname(nickname);
        if(clean.length()<2){message=t("Введите хотя бы два символа","Enter at least two characters");return;}
        KreyliProfile.setNickname(clean);
        back();
    }
    private static void open(String name){
        viewedPlayer=name.equals(KreyliProfile.displayName())?null:name;
        open=true;
        page=Page.PROFILE;
        previousInput=Gdx.input.getInputProcessor();
        modalInput=new InputMultiplexer();
        modalInput.addProcessor(new InputAdapter(){
            @Override public boolean keyDown(int key){
                if(page==Page.NICKNAME&&key==Input.Keys.V&&(Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                        ||Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT))){
                    String paste=Gdx.app.getClipboard().getContents();
                    if(paste!=null)nickname=KreyliIdentity.cleanNickname(nickname+paste);
                }
                CFG.setRenderO(true);
                return true;
            }
            @Override public boolean keyUp(int key){
                if(key==Input.Keys.ESCAPE)back();
                else if(key==Input.Keys.ENTER&&page==Page.NICKNAME)saveNickname();
                CFG.setRenderO(true);
                return true;
            }
            @Override public boolean keyTyped(char key){
                if(page!=Page.NICKNAME)return true;
                if(key=='\b'&&!nickname.isEmpty()){
                    nickname=nickname.substring(0,nickname.offsetByCodePoints(nickname.length(),-1));
                }else if(!Character.isISOControl(key)&&key!=';'&&nickname.length()<24){
                    nickname+=key;
                }else return true;
                message="";
                CFG.setRenderO(true);
                return true;
            }
            @Override public boolean scrolled(float x,float y){return true;}
        });
        if(previousInput!=null)modalInput.addProcessor(previousInput);
        Gdx.input.setInputProcessor(modalInput);
    }
    private static void close(){
        if(!open)return;
        open=false;
        viewedPlayer=null;
        if(Gdx.input.getInputProcessor()==modalInput)Gdx.input.setInputProcessor(previousInput);
        previousInput=null;
        modalInput=null;
        CFG.setRenderO(true);
    }
    private static void chooseFile(){
        if(importing)return;
        importing=true;
        String title=t("Загрузить аватар","Upload avatar");
        String errorMessage=t("Не удалось загрузить PNG или JPG до 2 МБ (макс. 4 Мп)","Could not load PNG or JPG up to 2 MB (max. 4 MP)");
        EventQueue.invokeLater(()->{
            byte[] pixels=null;
            String problem="";
            try{
                FileDialog picker=new FileDialog((Frame)null,title,FileDialog.LOAD);
                try{
                    picker.setFilenameFilter((dir,name)->{
                        String lower=name.toLowerCase(Locale.ROOT);
                        return lower.endsWith(".png")||lower.endsWith(".jpg")||lower.endsWith(".jpeg");
                    });
                    picker.setVisible(true);
                    File[] files=picker.getFiles();
                    if(files.length!=0)pixels=decodeAvatar(files[0]);
                }finally{picker.dispose();}
            }catch(Exception error){problem=errorMessage;}
            byte[] picture=pixels;
            String status=problem;
            Gdx.app.postRunnable(()->{
                importing=false;
                if(open&&page==Page.AVATARS){
                    message=status;
                    if(picture!=null)try{selectedAvatar=KreyliProfile.addAvatar(picture);}
                    catch(IllegalArgumentException|IllegalStateException error){message=error.getMessage();}
                }
                CFG.setRenderO(true);
            });
        });
    }
    static byte[] decodeAvatar(File file)throws IOException{
        try(var input=Files.newInputStream(file.toPath())){
            return KreyliProfile.originalAvatar(input.readNBytes(KreyliProfile.MAX_AVATAR_FILE+1));
        }
    }
}
