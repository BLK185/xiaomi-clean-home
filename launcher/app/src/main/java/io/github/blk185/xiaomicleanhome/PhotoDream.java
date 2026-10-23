package io.github.blk185.xiaomicleanhome;
import android.service.dreams.DreamService;
public final class PhotoDream extends DreamService {
    private PhotoScene scene;
    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow(); setInteractive(false); setFullscreen(true); setScreenBright(false);
    }
    @Override public void onDreamingStarted() {
        super.onDreamingStarted();
        if (scene!=null) scene.stop();
        scene=new PhotoScene(this); setContentView(scene.view); scene.start();
    }
    @Override public void onDreamingStopped() { if (scene!=null) scene.stop(); super.onDreamingStopped(); }
    @Override public void onDetachedFromWindow() { if (scene!=null) scene.stop(); super.onDetachedFromWindow(); }
}
