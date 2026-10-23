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
import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import android.os.SystemClock;
import android.os.CancellationSignal;
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
    private final LruCache<String,Bitmap> artwork=new LruCache<String,Bitmap>(24*1024*1024) {
        @Override protected int sizeOf(String key,Bitmap value){return value.getAllocationByteCount();}
    };
    private final Map<String,List<Program>> listings=new ConcurrentHashMap<>();
    private final Map<String,Long> listingTimes=new ConcurrentHashMap<>();
    private final Map<String,Program> best=new ConcurrentHashMap<>();
    private final Map<String,java.util.concurrent.CompletableFuture<Bitmap>> inFlight=new ConcurrentHashMap<>();
    private final ExecutorService warmer=Executors.newSingleThreadExecutor();
    private final File disk;
    private volatile Task foreground,background;
    private Runnable warm;
    private volatile int warmGeneration;
    private Map<String,String> tf1Index;
    private long tf1IndexTime;
    private static final class Task {
        final CancellationSignal signal=new CancellationSignal();
        volatile boolean cancelled;
        volatile HttpURLConnection connection;
        void cancel(){cancelled=true;signal.cancel();HttpURLConnection c=connection;if(c!=null)c.disconnect();}
        boolean stopped(){return cancelled||Thread.currentThread().isInterrupted();}
    }
    private volatile int generation;
    private volatile boolean resumed;
    private volatile String focused;
    private Runnable load;
    private Runnable videoTimeout;

    static final class Program {
        String title,poster,video,pkg,intent;
        int aspect=-1,type=-1;
    }

    AppPreviews(Activity activity) {
        this.activity=activity;
        disk=new File(activity.getCacheDir(),"preview-art-v2");disk.mkdirs();
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
    void pause() {
        resumed=false; warmGeneration++; clear();
        if(warm!=null)handler.removeCallbacks(warm);
        if(background!=null)background.cancel();
    }
    void close() { pause(); worker.shutdownNow(); warmer.shutdownNow(); artwork.evictAll(); }

    void warm(List<String> packages) {
        if(activity.getSharedPreferences("home_previews",0).getString("mode","auto").equals("off")
            ||activity.checkSelfPermission(PERMISSION)!=PackageManager.PERMISSION_GRANTED)return;
        if(warm!=null)handler.removeCallbacks(warm);
        final int warmTicket=++warmGeneration;
        List<String> copy=new ArrayList<>(packages);
        // Local content providers may themselves fetch remote artwork slowly. Prepare HTTP-backed
        // streaming apps first so one such provider cannot delay every other app's first preview.
        copy.sort(Comparator.comparingInt(p->p.equals("org.jellyfin.androidtv")?1:0));
        warm=()->warmer.execute(()->{
            for(String pkg:copy){
                if(!resumed||warmTicket!=warmGeneration||Thread.currentThread().isInterrupted())return;
                if(pkg.equals(focused)||hasCached(pkg))continue;
                Task task=new Task();background=task;
                loadBest(pkg,task,-1,false);
            }
        });
        handler.postDelayed(warm,1200);
    }
    private boolean hasCached(String pkg){Program p=best.get(pkg);return p!=null&&artwork.get(p.poster)!=null;}

    void select(String pkg) {
        focused=pkg;
        clear();
        String mode=activity.getSharedPreferences("home_previews",0).getString("mode","auto");
        if(mode.equals("off")){
            warmGeneration++;
            if(warm!=null)handler.removeCallbacks(warm);
            if(background!=null)background.cancel();
        }
        if (!resumed || pkg==null || mode.equals("off")
            || activity.checkSelfPermission(PERMISSION)!=PackageManager.PERMISSION_GRANTED) return;
        final int ticket=generation;
        Program cached=best.get(pkg);
        Bitmap image=cached==null?null:artwork.get(cached.poster);
        if(image!=null){
            display(cached,image,ticket,mode.equals("auto"),0);
            Long last=listingTimes.get(pkg);
            if(last!=null&&SystemClock.elapsedRealtime()-last<120000)return;
        }
        Task task=new Task();foreground=task;
        load=()->worker.execute(()->loadBest(pkg,task,ticket,mode.equals("auto")));
        handler.postDelayed(load,120);
    }

    private void loadBest(String pkg,Task task,int ticket,boolean auto) {
        if(task.stopped()||!resumed)return;
        long started=SystemClock.elapsedRealtime();
        List<Program> programs=read(pkg,task);
        if(task.stopped())return;
        List<Program> ranked=new ArrayList<>(programs);
        ranked.sort(Comparator.comparingInt((Program p)->PreviewArtwork.contentPriority(pkg,p.title,p.poster)+(PreviewArtwork.landscape(p.aspect)?100:0)+(auto&&https(p.video)?20:0)).reversed());
        Program previous=best.get(pkg);
        if(previous!=null&&programs.contains(previous))ranked.add(0,previous);
        int quality=-1,attempts=0;boolean videoStarted=false;
        for(Program p:ranked){
            if(task.stopped()||!resumed||attempts++>=4)return;
            Bitmap image=readPoster(p.poster,task);
            if(image==null)continue;
            int score=PreviewArtwork.fullBackdrop(image.getWidth(),image.getHeight())?10000000+image.getWidth()*image.getHeight():image.getWidth()*image.getHeight();
            if(score<=quality)continue;
            quality=score;best.put(pkg,p);
            if(ticket>=0){
                final boolean startVideo=auto&&!videoStarted;
                if(startVideo&&https(p.video))videoStarted=true;
                handler.post(()->display(p,image,ticket,startVideo,SystemClock.elapsedRealtime()-started));
            }
            if(!PreviewArtwork.fullBackdrop(image.getWidth(),image.getHeight())&&!task.stopped()){
                String official=officialImage(p,task);
                Bitmap upgraded=official==null?null:fetchPoster(official,task);
                if(upgraded!=null&&PreviewArtwork.fullBackdrop(upgraded.getWidth(),upgraded.getHeight())){
                    artwork.remove(official);artwork.put(p.poster,upgraded);best.put(pkg,p);
                    Log.i("HomePreviews","official HD "+pkg+" "+upgraded.getWidth()+"x"+upgraded.getHeight());
                    if(ticket>=0)handler.post(()->display(p,upgraded,ticket,false,SystemClock.elapsedRealtime()-started));
                    else handler.post(()->{
                        if(resumed&&pkg.equals(focused)&&!activity.getSharedPreferences("home_previews",0).getString("mode","auto").equals("off"))
                            display(p,upgraded,generation,false,SystemClock.elapsedRealtime()-started);
                    });
                    return;
                }
            }
            if(PreviewArtwork.fullBackdrop(image.getWidth(),image.getHeight()))return;
        }
    }

    private void display(Program program,Bitmap image,int ticket,boolean auto,long elapsed) {
        if(ticket!=generation||!resumed)return;
        caption.setText(program.title==null?"":program.title);caption.setVisibility(View.VISIBLE);
        boolean backdrop=PreviewArtwork.fullBackdrop(image.getWidth(),image.getHeight());
        poster.setScaleType(backdrop?ImageView.ScaleType.CENTER_CROP:ImageView.ScaleType.CENTER_INSIDE);
        poster.setImageBitmap(image);poster.setVisibility(View.VISIBLE);poster.setAlpha(0.8f);
        scrim.setVisibility(View.VISIBLE);
        Log.i("HomePreviews","ready "+focused+" "+image.getWidth()+"x"+image.getHeight()+" in "+elapsed+"ms");
        if(auto&&https(program.video))play(program.video,ticket);
    }

    private List<Program> read(String pkg,Task task) {
        Long last=listingTimes.get(pkg);
        if(last!=null&&SystemClock.elapsedRealtime()-last<120000)return listings.get(pkg);
        List<Program> result=new ArrayList<>();
        String[] columns={"package_name","title","poster_art_uri","preview_video_uri","poster_art_aspect_ratio","thumbnail_uri","poster_thumbnail_aspect_ratio","type","intent_uri"};
        for(String table:new String[]{"preview_program","watch_next_program"}) {
            try(Cursor cursor=activity.getContentResolver().query(Uri.parse("content://android.media.tv/"+table),columns,null,null,null,task.signal)) {
                if(cursor==null) continue;
                int scanned=0;
                while(!task.stopped() && cursor.moveToNext() && scanned++<2000 && result.size()<60) {
                    if(!pkg.equals(cursor.getString(0))) continue;
                    Program item=new Program(); item.title=cursor.getString(1); item.poster=cursor.getString(2); item.video=cursor.getString(3);
                    item.pkg=pkg;item.intent=cursor.getString(8);
                    item.aspect=cursor.isNull(4)?-1:cursor.getInt(4);item.type=cursor.isNull(7)?-1:cursor.getInt(7);
                    if(readable(item.poster)||https(item.video)) result.add(item);
                    String thumbnail=cursor.getString(5);
                    if(readable(thumbnail)&&!thumbnail.equals(item.poster)){
                        Program alternative=new Program();alternative.title=item.title;alternative.video=item.video;alternative.type=item.type;
                        alternative.pkg=pkg;alternative.intent=item.intent;
                        alternative.poster=thumbnail;alternative.aspect=cursor.isNull(6)?-1:cursor.getInt(6);result.add(alternative);
                    }
                    if(result.size()<=3)Log.i("HomePreviews","source "+pkg+" aspect="+item.aspect+" host="+(item.poster==null?"none":Uri.parse(item.poster).getHost()));
                }
            } catch(SecurityException e) { Log.i("HomePreviews",pkg+" : provider access restricted"); }
              catch(Exception e) { Log.i("HomePreviews",pkg+" : provider unavailable"); }
        }
        int videos=0,posters=0;
        for(Program item:result){if(https(item.video)) videos++;if(readable(item.poster)) posters++;}
        Log.i("HomePreviews",pkg+" programmes="+result.size()+" videos="+videos+" affiches="+posters);
        activity.getSharedPreferences("home_previews",0).edit().putInt("posters_"+pkg,posters).putInt("videos_"+pkg,videos).apply();
        if(!task.stopped()){listings.put(pkg,result);listingTimes.put(pkg,SystemClock.elapsedRealtime());}
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

    private Bitmap readPoster(String uri,Task task) {
        if(uri!=null){Bitmap cached=artwork.get(uri);if(cached!=null)return cached;}
        String high=PreviewArtwork.highResolution(uri);
        if(high!=null&&!high.equals(uri)){
            Bitmap upgraded=fetchPoster(high,task);
            if(upgraded!=null&&upgraded.getWidth()>=960){artwork.remove(high);artwork.put(uri,upgraded);return upgraded;}
        }
        return fetchPoster(uri,task);
    }

    private String officialImage(Program program,Task task) {
        if(!("fr.tf1.mytf1".equals(program.pkg)||"com.netflix.ninja".equals(program.pkg)))return null;
        try{
            String key=cachedFile(program.poster+".official-v1").getName();
            android.content.SharedPreferences prefs=activity.getSharedPreferences("official_artwork",0);
            long now=System.currentTimeMillis();
            String cached=prefs.getString(key,"");
            if(now-prefs.getLong(key+".time",0)<(cached.isEmpty()?120000L:86400000L))return cached.isEmpty()?null:cached;
            String image=null;
            if("fr.tf1.mytf1".equals(program.pkg)){
                synchronized(this){
                    if(tf1Index==null||now-tf1IndexTime>3600000L){
                        String page=readPublicPage("https://www.tf1.fr/",task);
                        if(page!=null&&!task.stopped()){tf1Index=OfficialArtwork.tf1Images(page);tf1IndexTime=now;}
                    }
                    String asset=OfficialArtwork.asset(program.poster);
                    if(tf1Index!=null&&asset!=null)image=tf1Index.get(asset);
                }
            }else{
                String id=OfficialArtwork.netflixId(program.intent);
                Log.i("HomePreviews","Netflix public title reference available="+(id!=null));
                if(id!=null){String page=readPublicPage("https://www.netflix.com/fr/title/"+id,task);if(page!=null)image=OfficialArtwork.netflix(page);}
            }
            if(!task.stopped()){
                // Bound persisted mappings too; no account credentials or page HTML are retained.
                if(prefs.getAll().size()>96)prefs.edit().clear().apply();
                prefs.edit().putString(key,image==null?"":image).putLong(key+".time",now).apply();
            }
            return image;
        }catch(Exception unavailable){return null;}
    }

    private String readPublicPage(String address,Task task) {
        long started=SystemClock.elapsedRealtime();
        for(int redirects=0;redirects<3&&!task.stopped();redirects++){
            HttpURLConnection connection=null;
            try{
                URL url=new URL(address);
                if(!"https".equals(url.getProtocol())||!("www.tf1.fr".equals(url.getHost())||"www.netflix.com".equals(url.getHost()))||url.getPort()!=-1)return null;
                connection=(HttpURLConnection)url.openConnection();task.connection=connection;
                connection.setConnectTimeout(1800);connection.setReadTimeout(1800);connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("User-Agent","Mozilla/5.0");
                connection.setRequestProperty("Accept-Encoding","gzip");
                int status=connection.getResponseCode();
                if(status>=300&&status<400){address=new URL(url,connection.getHeaderField("Location")).toString();continue;}
                if(status!=200)return null;
                InputStream input=connection.getInputStream();
                if("gzip".equalsIgnoreCase(connection.getContentEncoding()))input=new java.util.zip.GZIPInputStream(input);
                try(InputStream stream=input;ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
                    byte[] buffer=new byte[16384];
                    for(int n;(n=stream.read(buffer))!=-1;){
                        if(task.stopped()||bytes.size()+n>8*1024*1024||SystemClock.elapsedRealtime()-started>5000)return null;
                        bytes.write(buffer,0,n);
                    }
                    return bytes.toString("UTF-8");
                }
            }catch(Exception unavailable){return null;}
            finally{if(connection!=null)connection.disconnect();}
        }
        return null;
    }

    private File cachedFile(String uri)throws Exception{
        StringBuilder key=new StringBuilder();
        for(byte b:MessageDigest.getInstance("SHA-256").digest(uri.getBytes(java.nio.charset.StandardCharsets.UTF_8)))key.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        return new File(disk,key+".art");
    }
    private void cacheBytes(File target,byte[] bytes){
        try{
            File temp=File.createTempFile("pending-",".art",disk);
            try(FileOutputStream output=new FileOutputStream(temp)){output.write(bytes);}
            if(!temp.renameTo(target))temp.delete();
            File[] files=disk.listFiles();if(files==null)return;
            java.util.Arrays.sort(files,Comparator.comparingLong(File::lastModified));
            long total=0;for(File f:files)total+=f.length();
            for(int i=0;i<files.length&&(total>24L*1024*1024||i<files.length-16);i++){total-=files[i].length();files[i].delete();}
        }catch(Exception ignored){}
    }

    private Bitmap fetchPoster(String uri,Task task) {
        if(task.stopped()||!readable(uri))return null;
        Bitmap cached=artwork.get(uri);if(cached!=null)return cached;
        java.util.concurrent.CompletableFuture<Bitmap> result=new java.util.concurrent.CompletableFuture<>();
        java.util.concurrent.CompletableFuture<Bitmap> existing=inFlight.putIfAbsent(uri,result);
        if(existing!=null){
            while(!task.stopped())try{return existing.get(50,java.util.concurrent.TimeUnit.MILLISECONDS);}
            catch(java.util.concurrent.TimeoutException waiting){}
            catch(Exception interrupted){return null;}
            return null;
        }
        try{Bitmap image=fetchPosterRaw(uri,task);result.complete(image);return image;}
        finally{result.complete(null);inFlight.remove(uri,result);}
    }

    private Bitmap fetchPosterRaw(String uri,Task task) {
        if(task.stopped()||!readable(uri)) return null;
        Bitmap cached=artwork.get(uri);
        if(cached!=null) return cached;
        HttpURLConnection connection=null;
        try {
            File saved=cachedFile(uri);
            InputStream input;
            boolean fromDisk=saved.isFile()&&System.currentTimeMillis()-saved.lastModified()<86400000L;
            if(fromDisk)input=new java.io.FileInputStream(saved);
            else if(https(uri)) {
                connection=(HttpURLConnection)new URL(uri).openConnection();
                task.connection=connection;
                connection.setConnectTimeout(1800); connection.setReadTimeout(1800);
                connection.setInstanceFollowRedirects(false);
                if(connection.getResponseCode()!=200) return null;
                input=connection.getInputStream();
            } else input=activity.getContentResolver().openInputStream(Uri.parse(uri));
            if(input==null) return null;
            byte[] bytes;
            try(InputStream stream=input;ByteArrayOutputStream output=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[16384];
                for(int n;(n=stream.read(buffer))>=0;) {
                    if(task.stopped()||output.size()+n>8*1024*1024) return null;
                    output.write(buffer,0,n);
                }
                bytes=output.toByteArray();
            }
            if(task.stopped())return null;
            BitmapFactory.Options info=new BitmapFactory.Options();info.inJustDecodeBounds=true;
            BitmapFactory.decodeByteArray(bytes,0,bytes.length,info);
            if(info.outWidth<1||info.outHeight<1) return null;
            BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=PreviewArtwork.sample(info.outWidth,info.outHeight);
            Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
            if(bitmap!=null&&!task.stopped()){artwork.put(uri,bitmap);if(!fromDisk)cacheBytes(saved,bytes);}
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
        if(foreground!=null){foreground.cancel();foreground=null;}
        if(load!=null) handler.removeCallbacks(load);
        if(videoTimeout!=null) handler.removeCallbacks(videoTimeout);
        video.setOnPreparedListener(null);video.setOnInfoListener(null);video.setOnErrorListener(null);video.setOnCompletionListener(null);
        video.stopPlayback();video.setVisibility(View.GONE);
        poster.animate().cancel();poster.setImageDrawable(null);poster.setVisibility(View.GONE);
        caption.setVisibility(View.GONE);scrim.setVisibility(View.GONE);
    }
}
