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
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
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
    private static Box ownCountryPortrait;
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
            if(!CFG.menus.getInGameView()){
                ownCountryPortrait=null;
                player(batch,badge(),KreyliProfile.displayName(),KreyliProfile.avatar());
            }else if(!CFG.menus.getIn_Game_CivilizationView())ownCountryPortrait=null;
            if(!open)return;
            fill(batch,new Box(0,0,CFG.GAMEWIDTH,CFG.GAMEHEIGHT),SHADE);
            Box panel=panel();
            fill(batch,panel,PANEL);
            if(page==Page.NICKNAME){drawNickname(batch,panel);return;}
            if(page==Page.AVATARS){drawAvatars(batch,panel);return;}
            int x=panel.x+CFG.PADD*2,y=panel.y+CFG.PADD*2;
            text(batch,t("Профиль игрока","Player profile"),x,y,Color.GOLD);
            y+=row();
            player(batch,new Box(x,y,panel.w-CFG.PADD*4,row()+CFG.PADD),KreyliProfile.displayName(),KreyliProfile.avatar());
            y+=row()*2;
            text(batch,KreyliProfile.steamLinked()?t("Профиль Steam привязан","Steam profile linked"):t("Локальный профиль","Local profile"),x,y,MUTED);
            y+=row();
            long seconds=KreyliProfile.playSeconds();
            stat(batch,panel,y,t("Время в игре","Time played"),NUMBERS.format(seconds/3600)+t(" ч "," h ")+(seconds/60%60)+t(" мин"," min"));
            stat(batch,panel,y+row(),t("Завоёвано областей","Provinces conquered"),NUMBERS.format(KreyliProfile.conquered()));
            stat(batch,panel,y+row()*2,t("Заработано денег","Money earned"),NUMBERS.format(KreyliProfile.earned()));
            stat(batch,panel,y+row()*3,t("Призвано войск","Troops recruited"),NUMBERS.format(KreyliProfile.recruited()));
            button(batch,action(panel,0),t("Изменить ник","Edit nickname"));
            button(batch,action(panel,1),t("Аватары","Avatars"));
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
        button(batch,action(panel,2),t("Назад","Back"));
    }
    private static void drawAvatars(SpriteBatch batch,Box panel){
        int x=panel.x+CFG.PADD*2,y=panel.y+CFG.PADD*2;
        text(batch,t("Аватары","Avatars"),x,y,Color.GOLD);
        button(batch,upload(panel),importing?t("Выбор файла…","Choosing file…"):t("Загрузить с компьютера","Upload from computer"));
        if(KreyliProfile.avatarCount()==0)text(batch,t("Загрузите изображение PNG или JPG","Upload a PNG or JPG image"),x,panel.y+row()*3,MUTED);
        for(int i=0;i<KreyliProfile.avatarCount();i++){
            Box cell=avatarCell(panel,i);
            fill(batch,cell,i==selectedAvatar?Color.GOLD:CONTROL);
            int size=Math.min(cell.w,cell.h)-CFG.PADD;
            if(size>0)drawAvatar(batch,cell.x+(cell.w-size)/2,cell.y+(cell.h-size)/2,size,KreyliProfile.avatarPreview(i));
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

    /** A separate portrait panel beside the native country details and flag. */
    public static void drawCountry(Menu menu,SpriteBatch batch,int offsetX,int offsetY){
        ownCountryPortrait=null;
        if(open)return;
        int civ=CFG.getActiveCivInfoId();
        if(civ<=0)return;
        var players=KreyliProfile.player(civ);
        if(players.isEmpty())return;
        int columns=players.size()>8?3:players.size()>4?2:1;
        int rows=(players.size()+columns-1)/columns;
        int width=Math.min(CFG.GAMEWIDTH/(columns+2),Math.max(CFG.BUTTON_W,row()*4));
        int desiredX=menu.getPosX()+offsetX+CFG.PADD+menu.getWidthM();
        int startX=Math.max(CFG.PADD,Math.min(CFG.GAMEWIDTH-width*columns-CFG.PADD,desiredX));
        int startY=Math.max(CFG.PADD,menu.getPosY()+offsetY+CFG.PADD);
        float previous=batch.getPackedColor();
        try{
            int height=Math.min(row()*5,(CFG.GAMEHEIGHT-startY-CFG.PADD)/rows);
            if(height<row()*2)return;
            for(int i=0;i<players.size();i++){
                var controller=players.get(i);
                int x=startX+width*(i%columns),y=startY+height*(i/columns);
                Box card=new Box(x,y,width,height-CFG.PADD);
                fill(batch,card,PANEL);
                int size=Math.min(width-CFG.PADD*4,height-row()*2);
                if(size>0)drawAvatar(batch,x+(width-size)/2,y+CFG.PADD,size,controller.avatar());
                String name=MP_UI.fit(controller.nick(),width-CFG.PADD*2);
                text(batch,name,x+(width-MP_UI.textWidth(name))/2,y+height-row(),Color.WHITE);
                if(controller.nick().equals(KreyliProfile.displayName()))ownCountryPortrait=card;
            }
        }finally{batch.setPackedColor(previous);}
    }
    private static boolean profileTarget(int x,int y){
        if(!CFG.menus.getInGameView())return badge().contains(x,y);
        return CFG.menus.getIn_Game_CivilizationView()&&ownCountryPortrait!=null&&ownCountryPortrait.contains(x,y);
    }

    public static boolean touchDown(int x,int y,int pointer,int button){
        if(pointer!=0||!visible())return false;
        captured=open||(button==Input.Buttons.LEFT&&profileTarget(x,y));
        return captured;
    }
    public static boolean touchUp(int x,int y,int pointer,int button){
        if(pointer!=0||!captured)return false;
        captured=false;
        if(button!=Input.Buttons.LEFT||!visible())return true;
        if(!open){if(profileTarget(x,y))open();}
        else click(x,y);
        CFG.setRenderO(true);
        return true;
    }
    private static void click(int x,int y){
        Box panel=panel();
        if(!panel.contains(x,y)||action(panel,2).contains(x,y)){back();return;}
        if(page==Page.NICKNAME){
            if(action(panel,0).contains(x,y))saveNickname();
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
    private static void open(){
        open=true;
        page=Page.PROFILE;
        previousInput=Gdx.input.getInputProcessor();
        modalInput=new InputMultiplexer();
        modalInput.addProcessor(new InputAdapter(){
            @Override public boolean keyDown(int key){
                if(page==Page.NICKNAME&&key==Input.Keys.BACKSPACE&&!nickname.isEmpty())
                    nickname=nickname.substring(0,nickname.offsetByCodePoints(nickname.length(),-1));
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
                if(page==Page.NICKNAME&&!Character.isISOControl(key)&&key!=';'&&nickname.length()<24){
                    nickname+=key;message="";CFG.setRenderO(true);
                }
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
        if(Gdx.input.getInputProcessor()==modalInput)Gdx.input.setInputProcessor(previousInput);
        previousInput=null;
        modalInput=null;
        CFG.setRenderO(true);
    }
    private static void chooseFile(){
        if(importing)return;
        importing=true;
        String title=t("Загрузить аватар","Upload avatar");
        String filter=t("PNG или JPEG","PNG or JPEG");
        String errorMessage=t("Не удалось загрузить PNG или JPG","Could not load PNG or JPG");
        EventQueue.invokeLater(()->{
            byte[] pixels=null;
            String problem="";
            try{
                JFileChooser picker=new JFileChooser();
                picker.setDialogTitle(title);
                picker.setAcceptAllFileFilterUsed(false);
                picker.setFileFilter(new FileNameExtensionFilter(filter,"png","jpg","jpeg"));
                if(picker.showOpenDialog(null)==JFileChooser.APPROVE_OPTION)pixels=decodeAvatar(picker.getSelectedFile());
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
        if(!file.isFile()||file.length()>10L*1024*1024)throw new IOException("Размер файла");
        try(var input=ImageIO.createImageInputStream(file)){
            if(input==null)throw new IOException("Формат изображения");
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext())throw new IOException("Формат изображения");
            var reader=readers.next();
            try{
                String format=reader.getFormatName();
                if(!format.equalsIgnoreCase("PNG")&&!format.equalsIgnoreCase("JPEG"))throw new IOException("Формат изображения");
                reader.setInput(input,true,true);
                int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<1||height<1||width>4096||height>4096)throw new IOException("Размер изображения");
                BufferedImage source=reader.read(0);
                BufferedImage small=new BufferedImage(32,32,BufferedImage.TYPE_INT_ARGB);
                int side=Math.min(width,height),x=(width-side)/2,y=(height-side)/2;
                Graphics2D graphics=small.createGraphics();
                try{
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    graphics.drawImage(source,0,0,32,32,x,y,x+side,y+side,null);
                }finally{graphics.dispose();}
                byte[] rgba=new byte[32*32*4];
                for(int row=0;row<32;row++)for(int col=0;col<32;col++){
                    int argb=small.getRGB(col,row),offset=(row*32+col)*4;
                    rgba[offset]=(byte)(argb>>>16);rgba[offset+1]=(byte)(argb>>>8);
                    rgba[offset+2]=(byte)argb;rgba[offset+3]=(byte)(argb>>>24);
                }
                return rgba;
            }finally{reader.dispose();}
        }
    }
}
