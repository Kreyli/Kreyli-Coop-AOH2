package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.CFG;
import age.of.civilizations2.jakowski.lukasz.Renderer;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

public final class KreyliSplash {
    private static Texture logo, map, pixel;
    private static boolean failed;

    private KreyliSplash(){}

    public static void draw(SpriteBatch batch,int offset,float progress){
        if(!prepare())return;
        float previous=batch.getPackedColor();
        try{
            drawBackdrop(batch,0,0);
            int width=CFG.GAMEWIDTH,height=CFG.GAMEHEIGHT;
            float wave=(float)Math.sin(System.nanoTime()*2.2e-9);
            float logoWidth=Math.min(width*.52f,height*.75f)*(1+.025f*wave);
            drawLogo(batch,(width-logoWidth)/2,height*.19f-wave*6,logoWidth,height*.61f,1f);
            float fraction=Math.max(0,Math.min(1,progress));
            String label=KreyliText.t("Загрузка","Loading")+"  "+Math.round(fraction*100)+"%";
            int bar=Math.min(width/2,480),baseline=(int)(height*.79f);
            Renderer.drawTextWithShadow(batch,0,label,(width-MP_UI.textWidth(label))/2,baseline,Color.WHITE);
            batch.setColor(.11f,.12f,.15f,.9f);
            batch.draw(pixel,(width-bar)/2f,-baseline-32,bar,5);
            batch.setColor(.86f,.19f,.25f,1f);
            batch.draw(pixel,(width-bar)/2f,-baseline-32,bar*fraction,5);
        }finally{batch.setPackedColor(previous);}
    }

    public static void drawBackdrop(SpriteBatch batch,int offsetX,int offsetY){
        if(!prepare())return;
        float previous=batch.getPackedColor();
        try{
            int width=CFG.GAMEWIDTH,height=CFG.GAMEHEIGHT;
            batch.setColor(Color.WHITE);
            batch.draw(map,offsetX,-offsetY-height,width,height);
            batch.setColor(.22f,.23f,.25f,.46f);
            batch.draw(pixel,offsetX,-offsetY-height,width,height);
        }finally{batch.setPackedColor(previous);}
    }

    static void drawLogo(SpriteBatch batch,float x,float y,float width,float height,float opacity){
        if(!prepare())return;
        float previous=batch.getPackedColor();
        try{
            float scale=Math.min(width/logo.getWidth(),height/logo.getHeight());
            float logoWidth=logo.getWidth()*scale,logoHeight=logo.getHeight()*scale;
            float left=x+(width-logoWidth)/2,base=-(y+(height+logoHeight)/2);
            batch.setColor(0,0,0,.55f*opacity);
            batch.draw(logo,left+4,base-7,logoWidth,logoHeight);
            batch.setColor(1,1,1,opacity);
            batch.draw(logo,left,base,logoWidth,logoHeight);
        }finally{batch.setPackedColor(previous);}
    }

    private static boolean prepare(){
        if(failed)return false;
        if(logo!=null)return true;
        try{
            logo=new Texture(Gdx.files.classpath("kreyli-splash.png"));
            logo.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
            map=blurredMap();
            Pixmap source=new Pixmap(1,1,Pixmap.Format.RGBA8888);
            try{source.setColor(Color.WHITE);source.fill();pixel=new Texture(source);}
            finally{source.dispose();}
            return true;
        }catch(RuntimeException error){
            if(logo!=null)logo.dispose();
            if(map!=null)map.dispose();
            if(pixel!=null)pixel.dispose();
            logo=map=pixel=null;
            failed=true;
            Gdx.app.error("Kreyli Coop AOH2","Could not load the branded backdrop",error);
            return false;
        }
    }

    private static Texture blurredMap(){
        int width=448,height=262;
        Pixmap joined=new Pixmap(width,height,Pixmap.Format.RGBA8888);
        Pixmap horizontal=new Pixmap(width,height,Pixmap.Format.RGBA8888);
        try{
            joined.setBlending(Pixmap.Blending.None);
            joined.setFilter(Pixmap.Filter.BiLinear);
            horizontal.setBlending(Pixmap.Blending.None);
            drawHalf(joined,"AoC_L.png",0,width/2,height);
            drawHalf(joined,"AoC_R.png",width/2,width/2,height);
            blur(joined,horizontal,true);
            blur(horizontal,joined,false);
            Texture texture=new Texture(joined);
            texture.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
            return texture;
        }finally{
            joined.dispose();
            horizontal.dispose();
        }
    }

    private static void drawHalf(Pixmap target,String name,int x,int width,int height){
        Pixmap source=new Pixmap(Gdx.files.local("map/backgrounds/"+name));
        try{target.drawPixmap(source,0,0,source.getWidth(),source.getHeight(),x,0,width,height);}
        finally{source.dispose();}
    }

    private static void blur(Pixmap source,Pixmap target,boolean horizontal){
        int width=source.getWidth(),height=source.getHeight();
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){
            int red=0,green=0,blue=0;
            for(int offset=-3;offset<=3;offset++){
                int sx=horizontal?Math.clamp(x+offset,0,width-1):x;
                int sy=horizontal?y:Math.clamp(y+offset,0,height-1);
                int rgba=source.getPixel(sx,sy),weight=4-Math.abs(offset);
                red+=(rgba>>>24)*weight;
                green+=(rgba>>>16&255)*weight;
                blue+=(rgba>>>8&255)*weight;
            }
            target.drawPixel(x,y,(red/16<<24)|(green/16<<16)|(blue/16<<8)|255);
        }
    }
}
