package io.github.blk185.xiaomicleanhome;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.*;

/** Original Canvas adaptations of familiar photo slideshow compositions. */
final class PhotoEffectsView extends View {
    private final Paint paint=new Paint(3);
    private final float[] framing=new float[4];
    private final ArrayList<Bitmap> photos=new ArrayList<>();
    private final String style,transition;
    private final boolean fill;
    private final long interval,duration;
    private long changed=android.os.SystemClock.uptimeMillis();
    private boolean running=true;
    PhotoEffectsView(Context c){super(c);android.content.SharedPreferences p=PhotoStore.prefs(c);style=p.getString("style","panels");transition=p.getString("transition","fade");fill=p.getString("fit","fill").equals("fill");interval=p.getInt("interval_seconds",5)*1000L;duration=p.getInt("transition_ms",1000);}
    void add(Bitmap image){photos.add(0,image);while(photos.size()>(style.equals("classic")||style.equals("kenburns")||style.equals("flip")||style.equals("origami")?2:7)){Bitmap old=photos.remove(photos.size()-1);old.recycle();}changed=android.os.SystemClock.uptimeMillis();invalidate();}
    void stop(){running=false;for(Bitmap b:photos)b.recycle();photos.clear();}
    private Bitmap photo(int n){return photos.get(Math.min(n,photos.size()-1));}
    private void image(Canvas canvas,Bitmap b,RectF r,float alpha,boolean crop,float pan){
        if(b==null||b.isRecycled()||r.width()<=0||r.height()<=0)return;
        canvas.save();canvas.clipRect(r);
        float zoom=style.equals("kenburns")?1+.04f*(1+(float)Math.sin(pan*.6f))/2:1;
        PhotoFraming.place(b.getWidth(),b.getHeight(),r.left,r.top,r.right,r.bottom,crop&&fill,zoom,framing);
        paint.setAlpha(Math.max(0,Math.min(255,(int)(255*alpha))));canvas.drawBitmap(b,null,new RectF(framing[0],framing[1],framing[2],framing[3]),paint);paint.setAlpha(255);canvas.restore();
    }
    @Override protected void onDraw(Canvas c){super.onDraw(c);if(photos.isEmpty()||!running)return;float w=getWidth(),h=getHeight();long elapsed=android.os.SystemClock.uptimeMillis()-changed;float progress=Math.min(1,elapsed/(float)duration),t=android.os.SystemClock.uptimeMillis()/10000f;RectF full=new RectF(0,0,w,h);
        if(style.equals("classic")||style.equals("kenburns")){
            if(photos.size()>1&&progress<1)image(c,photo(1),full,1,fill,style.equals("kenburns")?t:0);
            c.save();float alpha=1;
            if(progress<1){switch(transition){case "slide":c.translate(w*(1-progress),0);break;case "slide_right":c.translate(-w*(1-progress),0);break;case "slide_up":c.translate(0,h*(1-progress));break;case "slide_down":c.translate(0,-h*(1-progress));break;case "zoom":c.scale(.8f+.2f*progress,.8f+.2f*progress,w/2,h/2);alpha=progress;break;case "flip":case "curtain":c.scale(Math.max(.001f,progress),1,w/2,h/2);break;case "none":break;default:alpha=progress;}}
            image(c,photo(0),full,alpha,fill||style.equals("kenburns"),style.equals("kenburns")?t:0);c.restore();
        }else if(style.equals("origami")||style.equals("flip")){
            image(c,photo(1),full,1,true,0);int count=style.equals("origami")?3:1;for(int i=0;i<count;i++){RectF r=new RectF(i*w/count,0,(i+1)*w/count,h);c.save();c.clipRect(r);float p=Math.min(1,Math.max(0,elapsed/(float)duration-i*.15f));c.scale(Math.max(.01f,p),1,r.centerX(),h/2);image(c,photo(0),full,1,true,t);c.restore();}
        }else if(style.equals("reflections")){
            for(int i=0;i<2;i++){RectF r=new RectF(w*(.05f+i*.5f),h*.07f,w*(.45f+i*.5f),h*.69f);image(c,photo(i),r,1,false,t+i);c.save();c.translate(0,r.bottom*2+10);c.scale(1,-1);image(c,photo(i),r,.23f,false,t+i);c.restore();}
        }else if(style.equals("floating")||style.equals("mobile")||style.equals("prints")){
            int n=style.equals("floating")?4:3;for(int i=n-1;i>=0;i--){float x=w*(.19f+(i%3)*.3f)+w*.045f*(float)Math.sin(t*.65+i*2),y=h*(.48f+(i%2)*.09f)+h*.09f*(float)Math.cos(t*.5+i);float cw=w*.33f,ch=h*.65f;RectF r=new RectF(x-cw/2,y-ch/2,x+cw/2,y+ch/2);c.save();c.rotate((float)Math.sin(t+i)*7,x,y);if(style.equals("mobile")){paint.setColor(0xffaabbcc);paint.setStrokeWidth(2);c.drawLine(x,0,x,r.top,paint);}paint.setColor(style.equals("prints")?Color.WHITE:0xff26313e);c.drawRect(r.left-8,r.top-8,r.right+8,r.bottom+(style.equals("prints")?35:8),paint);image(c,photo(i),r,1,true,t+i);c.restore();}
        }else{
            int columns=style.equals("panels")?3:style.equals("mosaic")?2:3,rows=style.equals("panels")?1:2;
            for(int i=0;i<columns*rows;i++){float gap=style.equals("wall")?10:3;RectF r=new RectF((i%columns)*w/columns+gap,(i/columns)*h/rows+gap,(i%columns+1)*w/columns-gap,(i/columns+1)*h/rows-gap);c.save();c.clipRect(r);image(c,photo(i+1),r,1,true,t+i);float p=Math.min(1,Math.max(0,elapsed/(float)duration-i*.12f));if(style.equals("panels"))c.translate(0,(i%2==0?1:-1)*r.height()*(1-p));else if(style.equals("tiles"))c.translate((i%2==0?1:-1)*r.width()*(1-p),0);else if(style.equals("wall")){c.rotate((float)Math.sin(t+i)*2,r.centerX(),r.centerY());}image(c,photo(i),r,style.equals("mosaic")?p:1,true,t*.4f+i);c.restore();}
        }
        if(running&&(progress<1||java.util.Arrays.asList("kenburns","floating","mobile","prints","wall").contains(style)))postInvalidateDelayed(40);
    }
}
