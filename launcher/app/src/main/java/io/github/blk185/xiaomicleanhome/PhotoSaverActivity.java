package io.github.blk185.xiaomicleanhome;
import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowManager;
public final class PhotoSaverActivity extends Activity {
    private PhotoScene scene;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        scene=new PhotoScene(this); setContentView(scene.view);
    }
    @Override protected void onResume() { super.onResume(); scene.start(); }
    @Override protected void onPause() { scene.stop(); super.onPause(); finish(); }
    @Override public boolean dispatchKeyEvent(KeyEvent event) { if (event.getAction()==KeyEvent.ACTION_UP) finish(); return true; }
    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) { finish(); return true; }
}
