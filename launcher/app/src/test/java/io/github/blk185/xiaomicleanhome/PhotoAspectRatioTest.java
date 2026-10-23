package io.github.blk185.xiaomicleanhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class PhotoAspectRatioTest {
    @Test public void landscape169AndPixelRoundingAreAccepted(){
        for(int[] size:new int[][]{{1920,1080},{3840,2160},{1280,720},{854,480},{1332,749},{1245,700},{1668,938},{1260,709}})assertTrue(PhotoAspectRatio.isLandscape169(size[0],size[1]));
    }
    @Test public void portraitSquareAndOtherLandscapeRatiosAreRejected(){
        for(int[] size:new int[][]{{1080,1920},{1000,1000},{1600,1000},{2560,1080},{1920,1082},{0,0},{2,1}})assertFalse(PhotoAspectRatio.isLandscape169(size[0],size[1]));
    }
}
