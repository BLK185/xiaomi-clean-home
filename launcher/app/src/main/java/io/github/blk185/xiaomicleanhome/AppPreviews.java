package io.github.blk185.xiaomicleanhome;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.VideoView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Reads only public/searchable Android TV listings; unsupported video falls back to its poster. */
final class AppPreviews {
    static final String PERMISSION="android.permission.READ_TV_LISTINGS";
    private final Activity activity;
    private final ImageView poster;
    private final VideoView video;
    private final TextView caption;
    private final View scrim;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newFixedThreadPool(2);
    private final LruCache<String,Bitmap> artwork=new LruCache<>(4);
    private volatile int generation;
    private boolean resumed;
    private String focused;
    private Runnable load;
    private Runnable videoTimeout;

    static final class Program {
        String title,poster,video;
    }

    AppPreviews(Activity activity) {
        this.activity=activity;
        poster=activity.findViewById(R.id.heroArt);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        caption=activity.findViewById(R.id.heroMeta);
        caption.setMaxWidth(Math.round(560*activity.getResources().getDisplayMetrics().density));
        caption.setEllipsize(android.text.TextUtils.TruncateAt.END);
        scrim=activity.findViewById(R.id.heroScrim);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0x99000000,0x33000000,0xdd000000}));
        FrameLayout root=activity.findViewById(R.id.root);
        video=new VideoView(activity);
        video.setFocusable(false); video.setFocusableInTouchMode(false);
        video.setVisibility(View.GONE);
        root.addView(video,1,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
    }

    void resume() { resumed=true; }
    void pause() { resumed=false; clear(); }
    void close() { pause(); worker.shutdownNow(); artwork.evictAll(); }

    void select(String pkg) {
        focused=pkg;
        clear();
        String mode=activity.getSharedPreferences("home_previews",0).getString("mode","auto");
        if (!resumed || pkg==null || mode.equals("off")
            || activity.checkSelfPermission(PERMISSION)!=PackageManager.PERMISSION_GRANTED) return;
        final int ticket=generation;
        load=() -> worker.execute(() -> {
            List<Program> programs=read(pkg);
            Program chosen=null;
            if(mode.equals("auto")) for(Program item:programs) if(https(item.video)){chosen=item;break;}
            if(chosen==null) for(Program item:programs) if(readable(item.poster)){chosen=item;break;}
            final Program program=chosen;
            Bitmap image=program==null?null:readPoster(program.poster);
            handler.post(() -> {
                if(ticket!=generation || !resumed || program==null) return;
                caption.setText(program.title==null?"":program.title);
                caption.setVisibility(View.VISIBLE);
                if(image!=null) {
                    poster.setImageBitmap(image); poster.setVisibility(View.VISIBLE);
                    poster.setAlpha(0f); poster.animate().alpha(0.65f).setDuration(350).start();
                    scrim.setVisibility(View.VISIBLE);
                }
                if(mode.equals("auto") && https(program.video)) play(program.video,ticket);
            });
        });
        handler.postDelayed(load,800);
    }

    private List<Program> read(String pkg) {
        List<Program> result=new ArrayList<>();
        String[] columns={"package_name","title","poster_art_uri","preview_video_uri"};
        for(String table:new String[]{"preview_program","watch_next_program"}) {
            try(Cursor cursor=activity.getContentResolver().query(Uri.parse("content://android.media.tv/"+table),columns,null,null,null)) {
                if(cursor==null) continue;
                int scanned=0;
                while(cursor.moveToNext() && scanned++<2000 && result.size()<60) {
                    if(!pkg.equals(cursor.getString(0))) continue;
                    Program item=new Program(); item.title=cursor.getString(1); item.poster=cursor.getString(2); item.video=cursor.getString(3);
                    if(readable(item.poster)||https(item.video)) result.add(item);
                }
            } catch(SecurityException e) { Log.i("HomePreviews",pkg+" : provider access restricted"); }
              catch(Exception e) { Log.i("HomePreviews",pkg+" : provider unavailable"); }
        }
        int videos=0,posters=0;
        for(Program item:result){if(https(item.video)) videos++;if(readable(item.poster)) posters++;}
        Log.i("HomePreviews",pkg+" programmes="+result.size()+" videos="+videos+" affiches="+posters);
        activity.getSharedPreferences("home_previews",0).edit().putInt("posters_"+pkg,posters).putInt("videos_"+pkg,videos).apply();
        return result;
    }

    private boolean https(String value) {
        if(value==null) return false;
        Uri uri=Uri.parse(value);
        return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost()!=null;
    }

    private boolean readable(String value) {
        if(value==null) return false;
        String scheme=Uri.parse(value).getScheme();
        return https(value)||"content".equals(scheme)||"android.resource".equals(scheme);
    }

    private Bitmap readPoster(String uri) {
        if(!readable(uri)) return null;
        Bitmap cached=artwork.get(uri);
        if(cached!=null) return cached;
        HttpURLConnection connection=null;
        try {
            InputStream input;
            if(https(uri)) {
                connection=(HttpURLConnection)new URL(uri).openConnection();
                connection.setConnectTimeout(3500); connection.setReadTimeout(4500);
                connection.setInstanceFollowRedirects(false);
                if(connection.getResponseCode()!=200) return null;
                input=connection.getInputStream();
            } else input=activity.getContentResolver().openInputStream(Uri.parse(uri));
            if(input==null) return null;
            byte[] bytes;
            try(InputStream stream=input;ByteArrayOutputStream output=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[16384];
                for(int n;(n=stream.read(buffer))>=0;) {
                    if(output.size()+n>8*1024*1024) return null;
                    output.write(buffer,0,n);
                }
                bytes=output.toByteArray();
            }
            BitmapFactory.Options info=new BitmapFactory.Options();info.inJustDecodeBounds=true;
            BitmapFactory.decodeByteArray(bytes,0,bytes.length,info);
            if(info.outWidth<1||info.outHeight<1) return null;
            BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;
            while((long)(info.outWidth/options.inSampleSize)*(info.outHeight/options.inSampleSize)>1500000) options.inSampleSize*=2;
            Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
            if(bitmap!=null) artwork.put(uri,bitmap);
            return bitmap;
        } catch(Exception|OutOfMemoryError e) { return null; }
        finally { if(connection!=null) connection.disconnect(); }
    }

    private void play(String uri,int ticket) {
        video.setVisibility(View.VISIBLE);
        video.setOnPreparedListener(player -> {
            if(ticket!=generation || !resumed){video.stopPlayback();return;}
            player.setVolume(0f,0f); player.setLooping(false); video.start();
        });
        video.setOnInfoListener((player,what,extra) -> {
            if(what==MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START && ticket==generation) {
                if(videoTimeout!=null) handler.removeCallbacks(videoTimeout);
                poster.animate().cancel();poster.setVisibility(View.GONE);scrim.setVisibility(View.VISIBLE);
                return true;
            }
            return false;
        });
        video.setOnErrorListener((player,what,extra) -> {fallback(ticket);return true;});
        video.setOnCompletionListener(player -> fallback(ticket));
        videoTimeout=() -> fallback(ticket);
        handler.postDelayed(videoTimeout,10000);
        try {video.setVideoURI(Uri.parse(uri));}
        catch(Exception e){fallback(ticket);}
    }

    private void fallback(int ticket) {
        if(ticket!=generation) return;
        if(videoTimeout!=null) handler.removeCallbacks(videoTimeout);
        video.stopPlayback();video.setVisibility(View.GONE);
        if(poster.getDrawable()!=null){poster.setVisibility(View.VISIBLE);poster.setAlpha(0.65f);}
        else scrim.setVisibility(View.GONE);
    }

    private void clear() {
        generation++;
        if(load!=null) handler.removeCallbacks(load);
        if(videoTimeout!=null) handler.removeCallbacks(videoTimeout);
        video.setOnPreparedListener(null);video.setOnInfoListener(null);video.setOnErrorListener(null);video.setOnCompletionListener(null);
        video.stopPlayback();video.setVisibility(View.GONE);
        poster.animate().cancel();poster.setImageDrawable(null);poster.setVisibility(View.GONE);
        caption.setVisibility(View.GONE);scrim.setVisibility(View.GONE);
    }
}
