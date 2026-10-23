package io.github.blk185.xiaomicleanhome;
/** Legacy entry point: all photo settings are now in the approved HTTPS browser. */
public final class PhotoSettingsActivity extends android.app.Activity {
 @Override public void onCreate(android.os.Bundle state){super.onCreate(state);startActivity(new android.content.Intent(this,PhotoTransferActivity.class));finish();}
}
