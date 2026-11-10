package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.CFG;
import age.of.civilizations2.jakowski.lukasz.Renderer;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

public final class KreyliSplash {
    private static Texture logo, west, east, pixel;
    private static boolean failed;

    private KreyliSplash() {}

    public static void draw(SpriteBatch batch,int offset,float progress) {
        if(failed)return;
        float previous=batch.getPackedColor();
        try {
            if(logo==null){
                logo=load(Gdx.files.classpath("kreyli-splash.png"));
                west=load(Gdx.files.local("map/backgrounds/AoC_L.png"));
                east=load(Gdx.files.local("map/backgrounds/AoC_R.png"));
                Pixmap source=new Pixmap(1,1,Pixmap.Format.RGBA8888);
                try{source.setColor(Color.WHITE);source.fill();pixel=new Texture(source);}
                finally{source.dispose();}
            }
            int width=CFG.GAMEWIDTH,height=CFG.GAMEHEIGHT;
            batch.setColor(Color.WHITE);
            batch.draw(west,0,-height,width/2f,height);
            batch.draw(east,width/2f,-height,width/2f,height);
            batch.setColor(.19f,.20f,.22f,.78f);
            batch.draw(pixel,0,-height,width,height);
            float wave=(float)Math.sin(System.nanoTime()*2.2e-9);
            float logoWidth=Math.min(width*.52f,height*.75f)*(1+.025f*wave);
            float logoHeight=logoWidth*logo.getHeight()/logo.getWidth();
            float x=(width-logoWidth)/2,y=-(height+logoHeight)/2+6*wave;
            batch.setColor(0,0,0,.55f);
            batch.draw(logo,x+5,y-9,logoWidth,logoHeight);
            batch.setColor(Color.WHITE);
            batch.draw(logo,x,y,logoWidth,logoHeight);
            float fraction=Math.max(0,Math.min(1,progress));
            String label=KreyliText.t("Загрузка","Loading")+"  "+Math.round(fraction*100)+"%";
            int bar=Math.min(width/2,480), baseline=(int)(height*.79);
            Renderer.drawTextWithShadow(batch,0,label,(width-MP_UI.textWidth(label))/2,baseline,Color.WHITE);
            batch.setColor(.11f,.12f,.15f,.9f);
            batch.draw(pixel,(width-bar)/2f,-baseline-32,bar,5);
            batch.setColor(.86f,.19f,.25f,1);
            batch.draw(pixel,(width-bar)/2f,-baseline-32,bar*fraction,5);
        }catch(RuntimeException e){
            finish();
            failed=true;
            Gdx.app.error("Kreyli Coop AOH2","Loading screen failed",e);
        }finally{batch.setPackedColor(previous);}
    }

    private static Texture load(com.badlogic.gdx.files.FileHandle file){
        Texture texture=new Texture(file);
        texture.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
        return texture;
    }

    public static void finish(){
        if(logo!=null)logo.dispose();
        if(west!=null)west.dispose();
        if(east!=null)east.dispose();
        if(pixel!=null)pixel.dispose();
        logo=west=east=pixel=null;
    }
}
