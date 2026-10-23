package io.github.blk185.xiaomicleanhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A single remote-controlled row. Removing a tile never changes the installed application. */
public final class RowHomeActivity extends Activity {
    private final Map<String, ResolveInfo> catalog = new LinkedHashMap<>();
    private final List<String> order = new ArrayList<>();
    private final Set<String> removed = new HashSet<>();
    private SharedPreferences preferences;
    private HorizontalScrollView scroll;
    private LinearLayout row;
    private TextView title, hint;
    private View hero;
    private String selected, moving, pendingFocus;
    private boolean registered;
    private AppPreviews previews;
    private boolean resumed;
    private final Handler idle = new Handler(Looper.getMainLooper());
    private final Runnable showSaver = () -> {
        if (resumed && hasWindowFocus()) launch(new Intent(this, PhotoSaverActivity.class));
        else if (resumed) resetIdle();
    };
    private final BroadcastReceiver changes = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { refresh(selected); }
    };

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        preferences = getSharedPreferences("horizontal_home", MODE_PRIVATE);
        removed.addAll(preferences.getStringSet("removed", new HashSet<>()));
        try {
            JSONArray saved = new JSONArray(preferences.getString("order", "[]"));
            for (int i = 0; i < saved.length(); i++) order.add(saved.getString(i));
        } catch (Exception ignored) { }
        if (order.isEmpty()) order.addAll(Arrays.asList(getResources().getStringArray(R.array.dock_apps)));
        title = findViewById(R.id.heroName);
        hero = findViewById(R.id.hero);
        hero.setAlpha(1f);
        findViewById(R.id.glass).setVisibility(View.GONE);
        findViewById(R.id.btnInputs).setVisibility(View.GONE);
        findViewById(R.id.btnReorder).setVisibility(View.GONE);
        findViewById(R.id.btnLibraryTop).setVisibility(View.GONE);
        View oldGrid = findViewById(R.id.grid);
        FrameLayout space = (FrameLayout) oldGrid.getParent();
        space.removeView(oldGrid);
        scroll = new HorizontalScrollView(this);
        scroll.setFillViewport(true);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setFocusable(false);
        scroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        scroll.setBackground(shape(0xff151515, 28, 0xff343434));
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(-1, dp(120), Gravity.BOTTOM);
        slp.bottomMargin = dp(42);
        space.addView(scroll, slp);
        row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(10), dp(14), dp(10));
        row.setClipChildren(false);
        row.setClipToPadding(false);
        scroll.addView(row, new FrameLayout.LayoutParams(-2, -1));
        hint = new TextView(this);
        hint.setTextColor(0xff999999);
        hint.setTextSize(12);
        hint.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(-1, dp(32), Gravity.BOTTOM);
        space.addView(hint, hlp);
        scroll.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> {
            int[] position = new int[2];
            scroll.getLocationOnScreen(position);
            hero.setY(position[1] - dp(88));
        });
        setupHeader();
        previews = new AppPreviews(this);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(changes, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(changes, filter);
        registered = true;
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        previews.resume();
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        ((TextView)findViewById(R.id.greeting)).setText(hour >= 18 || hour < 6 ? "Bonsoir" : "Bonjour");
        refresh(selected);
        resetIdle();
        requestPreviewPermission(false);
    }

    private void setupHeader() {
        TextView wifi = findViewById(R.id.btnWifi);
        wifi.setText("Wi-Fi");
        wifi.setOnClickListener(v -> launch(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        findViewById(R.id.btnPlayStore).setOnClickListener(v -> openStore());
        View settings = findViewById(R.id.btnSettings);
        settings.setOnClickListener(v -> settings.performLongClick());
        settings.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this).setTitle("Écran d’accueil")
                .setItems(new String[]{"Ajouter une tuile", "Réorganiser les tuiles", "Veille photo", "Aperçus", "Paramètres de la box"}, (dialog, which) -> {
                    if (which == 0) addTile();
                    else if (which == 1) beginMove(selected != null ? selected : (order.isEmpty() ? null : order.get(0)));
                    else if (which == 2) launch(new Intent(this, PhotoSettingsActivity.class));
                    else if (which == 3) previewSettings();
                    else launch(new Intent(Settings.ACTION_SETTINGS));
                }).show();
            return true;
        });
        for (View button : new View[]{wifi, findViewById(R.id.btnPlayStore), settings}) {
            button.setOnKeyListener((v, code, event) -> {
                if (code == KeyEvent.KEYCODE_DPAD_UP) return true;
                if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) focus(selected);
                    return true;
                }
                return (v == wifi && code == KeyEvent.KEYCODE_DPAD_LEFT)
                    || (v == settings && code == KeyEvent.KEYCODE_DPAD_RIGHT);
            });
        }
    }

    private void refresh(String focusPackage) {
        Set<String> hidden = new HashSet<>(Arrays.asList(getResources().getStringArray(R.array.hidden_apps)));
        catalog.clear();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo app : getPackageManager().queryIntentActivities(query, 0)) {
            String pkg = app.activityInfo.packageName;
            if (!pkg.equals(getPackageName()) && !hidden.contains(pkg)) catalog.put(pkg, app);
        }
        order.removeIf(pkg -> !catalog.containsKey(pkg) || removed.contains(pkg));
        for (String pkg : catalog.keySet()) if (!order.contains(pkg) && !removed.contains(pkg)) order.add(pkg);
        if (moving != null && !order.contains(moving)) moving = null;
        save();
        render(focusPackage);
    }

    private void save() {
        preferences.edit().putString("order", new JSONArray(order).toString())
            .putStringSet("removed", new HashSet<>(removed)).apply();
    }

    private void render(String focusPackage) {
        pendingFocus = focusPackage != null ? focusPackage : (order.isEmpty() ? "+" : order.get(0));
        int previousScroll = scroll.getScrollX();
        row.removeAllViews();
        for (String pkg : order) {
            ResolveInfo app = catalog.get(pkg);
            if (app == null) continue;
            String label = app.loadLabel(getPackageManager()).toString();
            FrameLayout tile = new FrameLayout(this);
            tile.setTag(pkg);
            tile.setContentDescription(label);
            tile.setFocusable(true);
            tile.setClickable(true);
            tile.setPadding(dp(4), dp(4), dp(4), dp(4));
            tile.setBackground(shape(Color.TRANSPARENT, 16, Color.TRANSPARENT));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(150), dp(88));
            params.setMargins(dp(5), 0, dp(5), 0);
            row.addView(tile, params);
            Drawable artwork = null;
            try { artwork = app.activityInfo.loadBanner(getPackageManager()); } catch (Exception ignored) { }
            if (artwork == null) {
                try { artwork = getPackageManager().getApplicationBanner(pkg); } catch (Exception ignored) { }
            }
            ImageView image = new ImageView(this);
            boolean banner = artwork != null;
            image.setImageDrawable(banner ? artwork : app.loadIcon(getPackageManager()));
            image.setScaleType(banner ? ImageView.ScaleType.FIT_XY : ImageView.ScaleType.FIT_CENTER);
            image.setBackground(shape(0xff252525, 12, Color.TRANSPARENT));
            image.setClipToOutline(true);
            if (!banner) image.setPadding(dp(10), dp(8), dp(10), dp(8));
            tile.addView(image, new FrameLayout.LayoutParams(-1,-1));
            tile.setOnFocusChangeListener((v, focused) -> {
                tile.setBackground(shape(focused ? Color.WHITE : Color.TRANSPARENT, 16,
                    pkg.equals(moving) ? 0xff66baff : Color.TRANSPARENT));
                tile.animate().scaleX(focused ? 1.035f : 1f).scaleY(focused ? 1.035f : 1f).setDuration(120).start();
                if (focused) {
                    selected = pkg;
                    title.setText(label);
                    reveal(v);
                    updateAmbient(app);
                    if (previews != null) previews.select(pkg);
                }
            });
            tile.setOnClickListener(v -> launchPackage(pkg));
            tile.setOnLongClickListener(v -> { tileMenu(pkg); return true; });
            navigation(tile);
        }
        TextView add = new TextView(this);
        add.setTag("+");
        add.setText("+\nAjouter");
        add.setContentDescription("Ajouter une tuile");
        add.setGravity(Gravity.CENTER);
        add.setTextColor(Color.WHITE);
        add.setTextSize(17);
        add.setFocusable(true);
        add.setClickable(true);
        add.setBackground(shape(0xff282828, 14, 0xff555555));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(88), dp(80));
        alp.setMargins(dp(8), 0, dp(4), 0);
        if (order.isEmpty()) row.addView(add, alp);
        add.setOnFocusChangeListener((v, focused) -> {
            add.setBackground(shape(focused ? 0xffeeeeee : 0xff282828, 14, 0xff555555));
            add.setTextColor(focused ? Color.BLACK : Color.WHITE);
            if (focused) { title.setText("Ajouter une application"); reveal(v); }
        });
        add.setOnClickListener(v -> addTile());
        navigation(add);
        hint.setVisibility(moving == null ? View.GONE : View.VISIBLE);
        hint.setText(moving == null ? ""
            : "◀  ▶  Déplacer la tuile   ·   OK ou Retour : enregistrer");
        row.post(() -> {
            scroll.scrollTo(previousScroll, 0);
            if (hasWindowFocus()) applyPendingFocus();
        });
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && pendingFocus != null && row != null) row.post(this::applyPendingFocus);
    }

    private void applyPendingFocus() {
        if (pendingFocus == null || !hasWindowFocus()) return;
        String target = pendingFocus;
        pendingFocus = null;
        focus(target);
    }

    private void navigation(View tile) {
        tile.setOnKeyListener((v, code, event) -> {
            if (code == KeyEvent.KEYCODE_DPAD_DOWN) return true;
            if (code == KeyEvent.KEYCODE_DPAD_UP) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) findViewById(R.id.btnWifi).requestFocus();
                return true;
            }
            int index = row.indexOfChild(v);
            if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    int next = Math.max(0, Math.min(row.getChildCount()-1,
                        index + (code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1)));
                    View target = row.getChildAt(next);
                    target.requestFocus();
                    reveal(target);
                }
                return true;
            }
            return false;
        });
    }

    private void reveal(View tile) {
        scroll.post(() -> {
            int left = tile.getLeft() - dp(12), right = tile.getRight() + dp(12);
            int x = scroll.getScrollX();
            if (left < x) scroll.smoothScrollTo(Math.max(0, left), 0);
            else if (right > x + scroll.getWidth()) scroll.smoothScrollTo(right - scroll.getWidth(), 0);
        });
    }

    private void focus(String pkg) {
        View target = pkg == null ? null : row.findViewWithTag(pkg);
        if (target == null && row.getChildCount() > 0) target = row.getChildAt(0);
        if (target != null) target.requestFocus();
    }

    private void tileMenu(String pkg) {
        new AlertDialog.Builder(this).setTitle(catalog.get(pkg).loadLabel(getPackageManager()))
            .setItems(new String[]{"Déplacer", "Retirer de l’accueil", "Ajouter une tuile"}, (dialog, which) -> {
                if (which == 0) beginMove(pkg);
                else if (which == 1) {
                    int index = order.indexOf(pkg);
                    removed.add(pkg);
                    order.remove(pkg);
                    save();
                    render(order.isEmpty() ? "+" : order.get(Math.min(index, order.size()-1)));
                } else addTile();
            }).show();
    }

    private void beginMove(String pkg) {
        if (pkg == null || !order.contains(pkg)) return;
        moving = pkg;
        render(pkg);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        resetIdle();
        if (moving != null) {
            int code = event.getKeyCode();
            if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    int from = order.indexOf(moving);
                    int to = Math.max(0, Math.min(order.size()-1, from + (code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1)));
                    if (from != to) { order.remove(from); order.add(to, moving); render(moving); }
                }
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    String done = moving;
                    moving = null;
                    save();
                    render(done);
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void addTile() {
        List<String> available = new ArrayList<>();
        for (String pkg : catalog.keySet()) if (!order.contains(pkg)) available.add(pkg);
        available.sort((a,b) -> catalog.get(a).loadLabel(getPackageManager()).toString()
            .compareToIgnoreCase(catalog.get(b).loadLabel(getPackageManager()).toString()));
        String[] labels = new String[available.size()];
        for (int i=0; i<labels.length; i++) labels[i] = catalog.get(available.get(i)).loadLabel(getPackageManager()).toString();
        AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle("Ajouter une tuile")
            .setNegativeButton("Fermer", null).setNeutralButton("Google Play", (d,w) -> openStore());
        if (available.isEmpty()) dialog.setMessage("Toutes tes applications sont déjà dans la rangée. Installe une autre application depuis Google Play.");
        else dialog.setItems(labels, (d,which) -> {
            String pkg = available.get(which);
            removed.remove(pkg);
            order.add(pkg);
            save();
            render(pkg);
        });
        dialog.show();
    }

    private void launchPackage(String pkg) {
        Intent intent = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (intent == null) intent = getPackageManager().getLaunchIntentForPackage(pkg);
        if (intent != null) launch(intent);
        else Toast.makeText(this, "Application indisponible", Toast.LENGTH_SHORT).show();
    }

    private void openStore() { launchPackage("com.android.vending"); }

    private void launch(Intent intent) {
        if (moving != null) { moving = null; save(); }
        try { startActivity(intent); }
        catch (Exception e) { Toast.makeText(this, "Application indisponible", Toast.LENGTH_SHORT).show(); }
    }

    private GradientDrawable shape(int color, int radius, int stroke) {
        GradientDrawable result = new GradientDrawable();
        result.setColor(color);
        result.setCornerRadius(dp(radius));
        result.setStroke(dp(1), stroke);
        return result;
    }

    @Override protected void onPause() {
        resumed = false;
        if (previews != null) previews.pause();
        idle.removeCallbacks(showSaver);
        if (moving != null) { moving = null; save(); }
        super.onPause();
    }

    @Override protected void onDestroy() {
        idle.removeCallbacksAndMessages(null);
        if (previews != null) previews.close();
        if (registered) unregisterReceiver(changes);
        super.onDestroy();
    }

    private void resetIdle() {
        idle.removeCallbacks(showSaver);
        int minutes = PhotoStore.prefs(this).getInt("idle_minutes", 5);
        if (minutes > 0) getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (resumed && minutes > 0) idle.postDelayed(showSaver, minutes * 60000L);
    }

    private void requestPreviewPermission(boolean retry) {
        SharedPreferences prefs = getSharedPreferences("home_previews", MODE_PRIVATE);
        if (prefs.getString("mode", "auto").equals("off")) return;
        if (checkSelfPermission(AppPreviews.PERMISSION) == PackageManager.PERMISSION_GRANTED) return;
        if (retry || !prefs.getBoolean("permission_requested", false)) {
            prefs.edit().putBoolean("permission_requested", true).apply();
            requestPermissions(new String[]{AppPreviews.PERMISSION}, 900);
        }
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == 900 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) previews.select(selected);
    }

    private void previewSettings() {
        SharedPreferences prefs = getSharedPreferences("home_previews", MODE_PRIVATE);
        String[] modes = {"auto", "posters", "off"};
        String mode = prefs.getString("mode", "auto");
        int checked = mode.equals("posters") ? 1 : mode.equals("off") ? 2 : 0;
        new AlertDialog.Builder(this).setTitle("Aperçus · vidéos sans son")
            .setSingleChoiceItems(new String[]{"Bande-annonce si disponible, sinon affiche", "Affiches uniquement", "Désactivés"}, checked, (dialog, which) -> {
                prefs.edit().putString("mode", modes[which]).apply();
                dialog.dismiss();
                if (which != 2) requestPreviewPermission(true);
                previews.select(selected);
            }).setNegativeButton("Fermer", null).show();
    }

    private void updateAmbient(ResolveInfo app) {
        Drawable artwork;
        try { artwork = app.activityInfo.loadBanner(getPackageManager()); }
        catch (Exception e) { artwork = null; }
        if (artwork == null) artwork = app.loadIcon(getPackageManager());
        Bitmap sample = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888);
        Rect original = new Rect(artwork.getBounds());
        artwork.setBounds(0,0,12,12);
        artwork.draw(new Canvas(sample));
        artwork.setBounds(original);
        int best = Color.GRAY;
        float score = -1;
        float[] hsv = new float[3];
        for (int y=0;y<12;y++) for (int x=0;x<12;x++) {
            int pixel = sample.getPixel(x,y);
            Color.colorToHSV(pixel,hsv);
            float candidate = hsv[1] * hsv[2];
            if (candidate > score) { score = candidate; best = pixel; }
        }
        sample.recycle();
        Color.colorToHSV(best,hsv);
        hsv[2] = Math.max(0.55f,hsv[2]);
        best = Color.HSVToColor(hsv);
        GradientDrawable glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setGradientCenter(0.28f,0.48f);
        glow.setGradientRadius(getResources().getDisplayMetrics().widthPixels * 0.7f);
        glow.setColors(new int[]{(best & 0xffffff) | 0x55000000, (best & 0xffffff) | 0x18000000, Color.TRANSPARENT});
        View ambient = findViewById(R.id.ambient);
        ambient.animate().cancel();
        ambient.setBackground(glow);
        ambient.setAlpha(0.4f);
        ambient.animate().alpha(1f).setDuration(400).start();
    }
}
