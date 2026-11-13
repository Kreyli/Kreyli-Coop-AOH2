package age.of.civilizations2.jakowski.lukasz.mp;

import age.of.civilizations2.jakowski.lukasz.CFG;
import age.of.civilizations2.jakowski.lukasz.Core.Core;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.ScreenUtils;
import java.util.ArrayList;

/** Called on the render thread in the isolated native turn-features probe. */
public final class KreyliRecruitmentRenderProbe {
    public static void verify(int civ,int province){
        var location=CFG.core.getProv(province);
        var country=CFG.core.getCiv(civ);
        if(country.getRecruitArmy_BasedOnProvinceID(province)<=0)throw new AssertionError("Queued recruitment required");
        boolean drawn=location.getDrawProv();
        var menu=CFG.menus.getInGame_ProvinceRecruit();
        var instant=CFG.menus.getInGame_ProvinceRecruit_Instantly();
        boolean menuVisible=menu.getVisibleM(),instantVisible=instant.getVisibleM();
        var previous=new ArrayList<>(Core.AMRCT);
        var buffer=new FrameBuffer(Pixmap.Format.RGBA8888,1024,1024,false);
        var batch=new SpriteBatch();
        int x=location.getCeX()+location.getShPX()+location.getTranslateProvPosX();
        int y=CFG.core.getDrawProvinceArmy_EndPosY(province,1f)
                +(CFG.ARMY_HEIGHT+CFG.ARMY_BG_EXTRA_HEIGHT*2)/2+CFG.ARMY_BG_EXTRA_HEIGHT;
        batch.setProjectionMatrix(new Matrix4().setToOrtho2D(x-512,-y-512,1024,1024));
        try{
            menu.setVisibleM(false);instant.setVisibleM(false);Core.AMRCT.clear();
            location.setDrawProv(false);
            long hidden=image(buffer,batch,civ);
            location.setDrawProv(true);
            long queued=image(buffer,batch,civ);
            if(hidden==queued)throw new AssertionError("Queued recruitment badge produced no pixels");
            String sync=SharedState.capture();
            country.clearRecruitArmy();SharedState.restore(sync);
            if(queued!=image(buffer,batch,civ))throw new AssertionError("Restored guest queue badge differs");
            Core.AMRCT.add(new Core.AMR(province,123,0));menu.setVisibleM(true);
            long preview=image(buffer,batch,civ);
            if(preview==queued||preview==hidden)throw new AssertionError("F recruitment preview is missing");
            Core.AMRCT.get(0).SPR=456;
            if(preview==image(buffer,batch,civ))throw new AssertionError("Preview amount does not change the displayed count");
            System.out.println("KREYLI_RECRUIT_RENDER: PASS queued/restored badge and changing F-preview pixels");
        }finally{
            Core.AMRCT.clear();Core.AMRCT.addAll(previous);
            menu.setVisibleM(menuVisible);instant.setVisibleM(instantVisible);location.setDrawProv(drawn);
            batch.dispose();buffer.dispose();
        }
    }
    private static long image(FrameBuffer buffer,SpriteBatch batch,int civ){
        buffer.begin();
        try{
            Gdx.gl.glClearColor(0,0,0,0);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            batch.begin();KreyliRealtimeRecruitment.drawPending(batch,1f,civ);batch.end();
            var pixels=ScreenUtils.getFrameBufferPixmap(0,0,1024,1024);
            try{
                long hash=1469598103934665603L;
                var bytes=pixels.getPixels();
                for(int i=0;i<bytes.capacity();i++)hash=(hash^Byte.toUnsignedInt(bytes.get(i)))*1099511628211L;
                return hash;
            }finally{pixels.dispose();}
        }finally{buffer.end();}
    }
}
