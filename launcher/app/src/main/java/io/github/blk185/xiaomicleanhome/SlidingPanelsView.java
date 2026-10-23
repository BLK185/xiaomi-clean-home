package io.github.blk185.xiaomicleanhome;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import java.util.*;

/** Complete compositions are swapped atomically; photographs never jump between fixed strips. */
final class SlidingPanelsView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final long duration,hold;
    private final RectF bounds=new RectF(),destination=new RectF();
    private final Rect source=new Rect();
    private final float[] framing=new float[4];
    private final boolean fill;
    private List<Bitmap> current=new ArrayList<>(),outgoing=new ArrayList<>();
    private SlidingPanelsLayout.Plan plan,oldPlan;
    private long changed;
    private boolean stopped;
    SlidingPanelsView(Context c){super(c);android.content.SharedPreferences p=PhotoStore.prefs(c);duration=Math.max(650,Math.min(1800,p.getInt("transition_ms",1000)));hold=p.getInt("interval_seconds",5)*1000L;fill=p.getString("fit","fill").equals("fill");setBackgroundColor(Color.BLACK);}
    int present(List<Bitmap> batch){
        release(outgoing);outgoing=current;oldPlan=plan;
        float[] ratios=new float[batch.size()];for(int i=0;i<ratios.length;i++)ratios[i]=batch.get(i).getWidth()/(float)batch.get(i).getHeight();
        plan=SlidingPanelsLayout.choose(ratios,getWidth()>0?getWidth()/(float)getHeight():16f/9,oldPlan==null?-1:oldPlan.family);
        current=new ArrayList<>(batch.subList(0,plan.used));
        for(int i=plan.used;i<batch.size();i++)batch.get(i).recycle();
        changed=SystemClock.uptimeMillis();invalidate();return plan.used;
    }
    private void release(List<Bitmap> images){for(Bitmap b:images)if(!b.isRecycled())b.recycle();images.clear();}
    void stop(){stopped=true;release(current);release(outgoing);}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);if(stopped||plan==null)return;
        long elapsed=SystemClock.uptimeMillis()-changed;
        if(oldPlan!=null&&elapsed<duration)draw(canvas,oldPlan,outgoing,false,elapsed);
        if(oldPlan==null||elapsed>=duration)draw(canvas,plan,current,true,duration);
        else draw(canvas,plan,current,true,elapsed);
        if(elapsed>=duration&&!outgoing.isEmpty()){release(outgoing);oldPlan=null;}
        if(elapsed<duration)postInvalidateOnAnimation();
    }
    private void draw(Canvas canvas,SlidingPanelsLayout.Plan layout,List<Bitmap> bitmaps,boolean incoming,long elapsed){
        float gap=Math.max(1,getWidth()/960f);
        for(int i=0;i<layout.cells.size();i++){
            SlidingPanelsLayout.Cell cell=layout.cells.get(i);Bitmap bitmap=bitmaps.get(cell.image);
            bounds.set(cell.left*getWidth(),cell.top*getHeight(),cell.right*getWidth(),cell.bottom*getHeight());
            // Only internal seams are inset; the composition reaches all four screen edges.
            if(cell.left>0)bounds.left+=gap/2;if(cell.right<1)bounds.right-=gap/2;
            if(cell.top>0)bounds.top+=gap/2;if(cell.bottom<1)bounds.bottom-=gap/2;
            float p=oldPlan==null?1:SlidingPanelsLayout.progress(incoming,i,layout.cells.size(),elapsed,duration);
            float distance=incoming?1-p:p;
            if(distance>=1)continue;
            int direction=SlidingPanelsLayout.direction(layout.family,i,incoming);
            canvas.save();canvas.clipRect(bounds);
            float dx=direction==0?-bounds.width()*distance:direction==1?bounds.width()*distance:0;
            float dy=direction==2?-bounds.height()*distance:direction==3?bounds.height()*distance:0;
            canvas.translate(dx,dy);
            PhotoFraming.place(bitmap.getWidth(),bitmap.getHeight(),bounds.left,bounds.top,bounds.right,bounds.bottom,fill,1,framing);
            destination.set(framing[0],framing[1],framing[2],framing[3]);source.set(0,0,bitmap.getWidth(),bitmap.getHeight());
            canvas.drawBitmap(bitmap,source,destination,paint);canvas.restore();
        }
    }
}
