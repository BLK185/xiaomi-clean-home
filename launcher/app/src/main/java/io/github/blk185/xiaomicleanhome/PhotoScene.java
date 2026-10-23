package io.github.blk185.xiaomicleanhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import java.io.File;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bounded sampled compositions, decoded off the UI thread. Clock moves once a minute. */
final class PhotoScene {
    final FrameLayout view;
    private final Context context;
    private final PhotoEffectsView effects;
    private final SlidingPanelsView panels;
    private List<File> panelQueue;
    private int panelCursor;
    private List<File> shuffleQueue;
    private int shuffleCursor;
    private int preloaded;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService decoder=Executors.newSingleThreadExecutor();
    private final SharedPreferences prefs;
    private final LinearLayout clock;
    private final TextView empty;
    private volatile boolean stopped;
    private int index=-1,corner;
    private String previous;
    private final Random random=new Random();
    private final Runnable next=this::nextPhoto;
    private final Runnable moveClock=new Runnable() {
        @Override public void run() {
            if (stopped) return;
            int[] corners={Gravity.BOTTOM|Gravity.RIGHT,Gravity.TOP|Gravity.RIGHT,Gravity.TOP|Gravity.LEFT,Gravity.BOTTOM|Gravity.LEFT};
            FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)clock.getLayoutParams();
            String position=prefs.getString("clock_position","moving");
            int fixed=position.equals("top_right")?1:position.equals("top_left")?2:position.equals("bottom_left")?3:0;
            lp.gravity=corners[position.equals("moving")?(corner++)%4:fixed]; clock.setLayoutParams(lp);
            handler.postDelayed(this,60000);
        }
    };
    private int dp(float n) { return Math.round(n*context.getResources().getDisplayMetrics().density); }
    PhotoScene(Context context) {
        this.context=context; prefs=PhotoStore.prefs(context);
        view=new FrameLayout(context); view.setBackgroundColor(Color.BLACK);
        boolean sliding=prefs.getString("style","panels").equals("panels");
        panels=sliding?new SlidingPanelsView(context):null;
        effects=sliding?null:new PhotoEffectsView(context);
        view.addView(sliding?panels:effects,new FrameLayout.LayoutParams(-1,-1));
        View shade=new View(context);
        GradientDrawable gradient=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x38000000,0x00000000,0x55000000});
        shade.setBackground(gradient); view.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        if(sliding)shade.setVisibility(View.GONE);
        empty=new TextView(context); empty.setText("Préparation des photos…"); empty.setTextColor(0xffaaaaaa); empty.setTextSize(18); empty.setGravity(Gravity.CENTER);
        view.addView(empty,new FrameLayout.LayoutParams(-1,-1));
        clock=new LinearLayout(context); clock.setOrientation(LinearLayout.VERTICAL); clock.setPadding(dp(30),dp(24),dp(30),dp(24));
        TextClock time=new TextClock(context); time.setFormat24Hour("HH:mm"); time.setFormat12Hour("h:mm"); time.setTextColor(-1); time.setTextSize(prefs.getInt("clock_size",52)); time.setShadowLayer(dp(6),0,dp(2),Color.BLACK);
        clock.addView(time);
        TextClock date=new TextClock(context); date.setFormat24Hour("EEEE d MMMM"); date.setFormat12Hour("EEEE d MMMM"); date.setTextColor(-1); date.setTextSize(16); date.setShadowLayer(dp(4),0,dp(1),Color.BLACK); clock.addView(date);
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.RIGHT);
        lp.setMargins(dp(20),dp(20),dp(20),dp(20)); view.addView(clock,lp);
        clock.setVisibility(prefs.getBoolean("clock",true)?View.VISIBLE:View.GONE);
    }
    void start() { stopped=false; handler.post(next); handler.post(moveClock); }
    void stop() {
        stopped=true; handler.removeCallbacksAndMessages(null); decoder.shutdownNow();
        if(effects!=null)effects.stop();if(panels!=null)panels.stop();
    }
    private void nextPhoto() {
        if (stopped) return;
        decoder.execute(()->{List<File> photos=PhotoStore.playlist(context);handler.post(()->showNext(photos));});
    }
    private void showNext(List<File> photos){
        if(stopped)return;
        empty.setText("Ajoutez vos photos depuis le site de gestion");empty.setVisibility(photos.isEmpty()?View.VISIBLE:View.GONE);
        if (photos.isEmpty()) { handler.postDelayed(next,5000); return; }
        if(panels!=null){nextPanels(photos);return;}
        if (prefs.getBoolean("shuffle",false)) {
            if(shuffleQueue==null||shuffleCursor>=shuffleQueue.size()||!new java.util.HashSet<>(shuffleQueue).equals(new java.util.HashSet<>(photos))){
                shuffleQueue=new java.util.ArrayList<>(photos);java.util.Collections.shuffle(shuffleQueue,random);shuffleCursor=0;
                if(shuffleQueue.size()>1&&shuffleQueue.get(0).getName().equals(previous))java.util.Collections.swap(shuffleQueue,0,1);
            }
            index=photos.indexOf(shuffleQueue.get(shuffleCursor++));
        } else index=(index+1)%photos.size();
        File file=photos.get(index); previous=file.getName();
        decoder.execute(() -> {
            Bitmap bitmap=null;
            try { bitmap=PhotoImages.decode(file,900000); } catch (Exception | OutOfMemoryError ignored) { }
            final Bitmap loaded=bitmap;
            handler.post(() -> {
                if (stopped) { if (loaded!=null) loaded.recycle(); return; }
                if (loaded!=null) display(loaded);
                String style=prefs.getString("style","panels");int warmup=style.equals("classic")||style.equals("kenburns")||style.equals("origami")||style.equals("flip")?1:6;
                handler.postDelayed(next,loaded==null?1000:++preloaded<warmup?80:Math.max(5,prefs.getInt("interval_seconds",5))*1000L);
            });
        });
    }
    private void nextPanels(List<File> photos){
        if(panelQueue==null){panelQueue=new java.util.ArrayList<>(photos);if(prefs.getBoolean("shuffle",false))java.util.Collections.shuffle(panelQueue,random);}
        int count=Math.min(4,panelQueue.size()-panelCursor);
        List<File> candidates=new java.util.ArrayList<>();for(int i=0;i<count;i++)candidates.add(panelQueue.get(panelCursor+i));
        decoder.execute(()->{
            List<Bitmap> batch=new java.util.ArrayList<>();
            for(File file:candidates){if(stopped)break;try{Bitmap bitmap=PhotoImages.decode(file,1200000);if(bitmap!=null)batch.add(bitmap);}catch(Exception|OutOfMemoryError ignored){}}
            handler.post(()->{
                if(stopped){for(Bitmap bitmap:batch)bitmap.recycle();return;}
                if(batch.isEmpty()){panelCursor=(panelCursor+1)%panelQueue.size();handler.postDelayed(next,1000);return;}
                int used=panels.present(batch);panelCursor+=batch.size()==count?used:count;
                if(panelCursor>=panelQueue.size()){panelCursor%=panelQueue.size();if(prefs.getBoolean("shuffle",false))java.util.Collections.shuffle(panelQueue,random);}
                clock.bringToFront();
                handler.postDelayed(next,Math.max(5,prefs.getInt("interval_seconds",5))*1000L+Math.max(650,Math.min(1800,prefs.getInt("transition_ms",1000))));
            });
        });
    }
    private void display(Bitmap bitmap) {effects.add(bitmap);clock.bringToFront();}
}
