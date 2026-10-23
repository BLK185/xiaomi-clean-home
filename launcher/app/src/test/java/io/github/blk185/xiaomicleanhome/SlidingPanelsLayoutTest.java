package io.github.blk185.xiaomicleanhome;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class SlidingPanelsLayoutTest {
    @Test public void compositionsCoverScreenWithoutOverlapsOrRepeatedPhotographs(){
        Random random=new Random(84);
        for(int sample=0;sample<180;sample++){
            float[] ratios=new float[1+random.nextInt(4)];for(int i=0;i<ratios.length;i++)ratios[i]=.5f+random.nextFloat()*1.7f;
            SlidingPanelsLayout.Plan plan=SlidingPanelsLayout.choose(ratios,16f/9,sample%6);
            Set<Integer> used=new HashSet<>();float area=0;
            for(SlidingPanelsLayout.Cell a:plan.cells){
                assertTrue(used.add(a.image));assertTrue(a.image>=0&&a.image<plan.used);
                assertTrue(a.left>=0&&a.top>=0&&a.right<=1&&a.bottom<=1);
                assertTrue(a.right>a.left&&a.bottom>a.top);area+=(a.right-a.left)*(a.bottom-a.top);
                for(SlidingPanelsLayout.Cell b:plan.cells)if(a!=b)assertTrue(Math.min(a.right,b.right)<=Math.max(a.left,b.left)+.0001f||Math.min(a.bottom,b.bottom)<=Math.max(a.top,b.top)+.0001f);
            }
            assertEquals(1,area,.0001);assertEquals(plan.used,used.size());
        }
    }
    @Test public void portraitsAreNotForcedIntoThreeNarrowStrips(){
        float[] photos={2f/3,2f/3,2f/3,2f/3};
        SlidingPanelsLayout.Plan plan=SlidingPanelsLayout.choose(photos,16f/9,-1);
        assertEquals(2,plan.used);
        for(SlidingPanelsLayout.Cell c:plan.cells){float slot=16f/9*(c.right-c.left)/(c.bottom-c.top);assertTrue(Math.min(slot/photos[c.image],photos[c.image]/slot)>.7f);}
    }
    @Test public void exitFinishesBeforeEntranceAndEveryPanelSettles(){
        for(int count=2;count<=4;count++)for(int i=0;i<count;i++){
            assertEquals(0,SlidingPanelsLayout.progress(false,i,count,0,1000),0);
            assertEquals(1,SlidingPanelsLayout.progress(false,i,count,510,1000),0);
            assertEquals(0,SlidingPanelsLayout.progress(true,i,count,480,1000),0);
            assertEquals(1,SlidingPanelsLayout.progress(true,i,count,1000,1000),0);
            float last=0;for(int t=0;t<=1000;t+=10){float current=SlidingPanelsLayout.progress(true,i,count,t,1000);assertTrue(current>=last);last=current;}
        }
    }
    @Test public void landscapeLibrariesAlternateLargePanelAndGridCompositions(){
        float[] photos={16f/9,16f/9,16f/9,16f/9};int previous=-1;
        for(int expected=1;expected<=5;expected++){
            SlidingPanelsLayout.Plan plan=SlidingPanelsLayout.choose(photos,16f/9,previous);
            assertEquals(expected,plan.family);assertTrue(plan.used>=3);previous=plan.family;
        }
    }
}
