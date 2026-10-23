package io.github.blk185.xiaomicleanhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class PhotoFramingTest {
    @Test public void everyAspectRatioKeepsTheImageCentreAtThePanelCentre(){
        float[][] photos={{400,1200},{1920,1080},{1200,400},{1000,1000}};
        for(float[] photo:photos)for(boolean fill:new boolean[]{false,true})for(float zoom:new float[]{1,1.04f}){
            float[] result=new float[4];PhotoFraming.place(photo[0],photo[1],150,240,750,940,fill,zoom,result);
            assertEquals(450,(result[0]+result[2])/2,.001);assertEquals(590,(result[1]+result[3])/2,.001);
            assertEquals(photo[0]/photo[1],(result[2]-result[0])/(result[3]-result[1]),.0001);
            if(fill){assertTrue(result[0]<=150.001&&result[1]<=240.001);assertTrue(result[2]>=749.999&&result[3]>=939.999);}
            else if(zoom==1){assertTrue(result[0]>=149.999&&result[1]>=239.999);assertTrue(result[2]<=750.001&&result[3]<=940.001);}
        }
    }
}
