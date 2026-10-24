package com.project1944.cod3;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.Locale;

/**
 * Launcher, diagnostics and game-data setup for the Android port.
 *
 * The screen is built in code on purpose: the APK has no resource-heavy UI and
 * this keeps the whole layout visible in one file. Game data resolution lives
 * in GameData; logging lives in Logx.
 */
public final class MainActivity extends Activity {
    private static final int PICK_GAME_FOLDER = 1001;
    private static final int PERMISSION_READ_STORAGE = 1002;

    private static final int BG = Color.rgb(8, 10, 13);
    private static final int PANEL = Color.rgb(19, 23, 28);
    private static final int STROKE = Color.rgb(42, 49, 57);
    private static final int TEXT = Color.rgb(232, 236, 240);
    private static final int MUTED = Color.rgb(139, 148, 158);
    private static final int GOLD = Color.rgb(232, 180, 74);
    private static final int GREEN = Color.rgb(92, 204, 140);
    private static final int RED = Color.rgb(232, 106, 90);

    private TextView dataPath;
    private TextView dataState;
    private TextView allFilesState;
    private Button allFilesButton;
    private int checkGeneration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Logx.init(this);
        Logx.i("MainActivity", "launcher opened; log=" + Logx.file(this));

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        setContentView(buildUi());
        hideSystemBars();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && Build.VERSION.SDK_INT < Build.VERSION_CODES.R
                && checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[] {android.Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSION_READ_STORAGE);
        }

        refreshData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAllFilesState();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_GAME_FOLDER || resultCode != RESULT_OK || data == null
                || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception e) {
            Logx.w("MainActivity", "persistable permission rejected: " + e.getMessage());
        }
        String path = GameData.treeToPath(uri);
        if (path == null) {
            Logx.e("MainActivity", "picker returned unmappable tree: " + uri);
            toast("That folder is not on accessible storage. Use /sdcard/cod3 instead.");
            return;
        }
        GameData.save(this, path);
        Logx.i("MainActivity", "game folder selected: " + path);
        refreshData();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_READ_STORAGE) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            Logx.i("MainActivity", "read storage granted=" + granted);
            refreshData();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
        }
    }

    // ------------------------------------------------------------------ UI

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[] {Color.rgb(8, 10, 13), Color.rgb(14, 21, 27), Color.rgb(8, 10, 13)}));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll, match());

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(30), dp(22), dp(30), dp(30));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content.addView(buildHero());

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        chips.setPadding(0, dp(14), 0, dp(4));
        chips.addView(chip(Build.MANUFACTURER.toUpperCase(Locale.US) + " " + Build.MODEL,
                TEXT));
        chips.addView(chip("ANDROID " + Build.VERSION.RELEASE, MUTED));
        chips.addView(chip("VULKAN " + vulkanVersion(), GOLD));
        chips.addView(chip(ramGb(), GREEN));
        content.addView(chips);

        content.addView(sectionLabel("GAME DATA"));
        content.addView(buildGameCard());

        content.addView(sectionLabel("SYSTEM"));
        content.addView(buildSystemCard());

        content.addView(buildLaunchButton());

        TextView footer = text("Log: " + Logx.file(this).getAbsolutePath()
                + "\nTap to copy the path. M1a build - the game itself is not included.",
                11, MUTED);
        footer.setTypeface(Typeface.MONOSPACE);
        footer.setPadding(dp(4), dp(18), dp(4), 0);
        footer.setOnClickListener(v -> {
            ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("logfile", footer.getText()));
            toast("Log path copied");
        });
        content.addView(footer);
        return root;
    }

    private View buildHero() {
        FrameLayout hero = new FrameLayout(this);
        hero.setBackground(roundRect(PANEL, 20, STROKE, 1));
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(148));
        hero.setLayoutParams(heroParams);

        ImageView image = new ImageView(this);
        image.setImageResource(R.drawable.hero);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setAlpha(0.62f);
        hero.addView(image, match());

        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] {0xEE080A0D, 0x99080A0D, 0x22080A0D}));
        hero.addView(scrim, match());

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setGravity(Gravity.BOTTOM);
        titles.setPadding(dp(22), dp(16), dp(22), dp(16));
        hero.addView(titles, match());

        TextView eyebrow = text("ANDROID NATIVE PORT", 11, GOLD);
        eyebrow.setTypeface(Typeface.DEFAULT_BOLD);
        eyebrow.setLetterSpacing(0.22f);
        titles.addView(eyebrow);

        TextView title = text("PROJECT 1944", 34, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLetterSpacing(0.02f);
        titles.addView(title);

        TextView subtitle = text("ReXGlue runtime  /  Vulkan 1.3  /  arm64-v8a", 12, MUTED);
        subtitle.setPadding(0, dp(2), 0, 0);
        titles.addView(subtitle);
        return hero;
    }

    private View buildGameCard() {
        LinearLayout card = card();
        card.addView(sectionLabel("CALL OF DUTY 3 - XBOX 360"));

        dataPath = text("", 13, TEXT);
        dataPath.setTypeface(Typeface.MONOSPACE);
        dataPath.setPadding(0, dp(6), 0, dp(2));
        card.addView(dataPath);

        dataState = text("Checking game data...", 12, MUTED);
        dataState.setPadding(0, dp(4), 0, dp(10));
        card.addView(dataState);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(actionButton("PICK FOLDER", true, v -> pickFolder()));
        row.addView(actionButton("USE /sdcard/cod3", false, v -> useDefaultFolder()));
        row.addView(actionButton("RECHECK", false, v -> refreshData()));
        card.addView(row);

        allFilesButton = actionButton("GRANT ALL-FILES ACCESS", false,
                v -> requestAllFilesAccess());
        LinearLayout.LayoutParams allParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        allParams.topMargin = dp(10);
        allFilesButton.setLayoutParams(allParams);
        card.addView(allFilesButton);

        allFilesState = text("", 11, MUTED);
        allFilesState.setPadding(0, dp(8), 0, 0);
        card.addView(allFilesState);
        return card;
    }

    private View buildSystemCard() {
        LinearLayout card = card();
        PackageManager pm = getPackageManager();
        String vulkan = pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION)
                ? "Vulkan " + vulkanVersion() : "not reported";
        String gamepad = pm.hasSystemFeature(PackageManager.FEATURE_GAMEPAD)
                ? "detected" : "not reported (touch overlay available)";
        String version = "0.1.0-m1a";
        try {
            version = pm.getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        TextView rows = text(String.format(Locale.US,
                "Device     : %s %s\n"
                        + "Android    : %s (API %d)\n"
                        + "RAM        : %s total\n"
                        + "Vulkan     : %s\n"
                        + "Gamepad    : %s\n"
                        + "Build      : %s (debug)",
                Build.MANUFACTURER, Build.MODEL,
                Build.VERSION.RELEASE, Build.VERSION.SDK_INT,
                ramGb(), vulkan, gamepad, version), 12, TEXT);
        rows.setTypeface(Typeface.MONOSPACE);
        rows.setLineSpacing(dp(3), 1.05f);
        card.addView(rows);
        return card;
    }

    private View buildLaunchButton() {
        Button launch = new Button(this);
        launch.setText("START VULKAN SMOKE TEST");
        launch.setAllCaps(false);
        launch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        launch.setTypeface(Typeface.DEFAULT_BOLD);
        launch.setLetterSpacing(0.08f);
        launch.setTextColor(Color.rgb(12, 12, 12));
        launch.setBackground(ripple(roundRect(GOLD, 15, GOLD, 1)));
        launch.setOnClickListener(v -> launchGame());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        params.topMargin = dp(18);
        launch.setLayoutParams(params);
        return launch;
    }

    // ------------------------------------------------------------- actions

    private void pickFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        try {
            startActivityForResult(intent, PICK_GAME_FOLDER);
            Logx.i("MainActivity", "folder picker opened");
        } catch (Exception e) {
            Logx.e("MainActivity", "no document picker", e);
            toast("No system file picker available. Put the data in /sdcard/cod3.");
        }
    }

    private void useDefaultFolder() {
        String path = GameData.defaultPath();
        GameData.save(this, path);
        Logx.i("MainActivity", "game folder set to default: " + path);
        refreshData();
    }

    private void launchGame() {
        if (!GameData.hasXex(GameData.resolve(this))) {
            toast("Game data missing - starting the smoke test only.");
        }
        Logx.i("MainActivity", "launching GameActivity");
        startActivity(new Intent(this, GameActivity.class));
    }

    private void refreshData() {
        final int generation = ++checkGeneration;
        final String path = GameData.resolve(this);
        dataPath.setText(path);
        dataState.setTextColor(MUTED);
        dataState.setText("Checking default.xex...");

        new Thread(() -> {
            File xex = new File(path, "default.xex");
            String state;
            int color;
            if (!xex.isFile()) {
                state = "default.xex not found. Use PICK FOLDER or copy the game to "
                        + GameData.defaultPath() + ".";
                color = RED;
            } else {
                Logx.i("MainActivity", "hashing " + xex + " (" + xex.length() + " bytes)");
                String sha = GameData.sha256(xex);
                if (GameData.SUPPORTED_SHA256.equalsIgnoreCase(sha)) {
                    state = "default.xex verified - SHA-256 matches the supported build.";
                    color = GREEN;
                } else {
                    state = "SHA-256 mismatch. This revision is untested.\n" + sha;
                    color = GOLD;
                }
            }
            final String finalState = state;
            final int finalColor = color;
            runOnUiThread(() -> {
                if (generation != checkGeneration) {
                    return;
                }
                dataState.setTextColor(finalColor);
                dataState.setText(finalState);
                Logx.i("MainActivity", "game data check: " + finalState.replace('\n', ' '));
            });
        }, "game-data-check").start();
        refreshAllFilesState();
    }

    private void refreshAllFilesState() {
        if (allFilesButton == null) {
            return;
        }
        boolean required = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
        boolean granted = !required || Environment.isExternalStorageManager();
        allFilesButton.setVisibility(granted ? View.GONE : View.VISIBLE);
        allFilesState.setText(granted
                ? (required ? "All-files access granted." : "Legacy storage permission path.")
                : "All-files access is required to read /sdcard/cod3 in place.");
        allFilesState.setTextColor(granted ? GREEN : MUTED);
    }

    private void requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            requestPermissions(
                    new String[] {android.Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSION_READ_STORAGE);
            return;
        }
        try {
            startActivity(new Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            } catch (Exception ignored) {
                toast("Open Settings > Apps > Project 1944 > All files access.");
            }
        }
    }

    // -------------------------------------------------------------- widgets

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(roundRect(PANEL, 18, STROKE, 1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(6);
        card.setLayoutParams(params);
        return card;
    }

    private TextView sectionLabel(String value) {
        TextView label = text(value, 11, MUTED);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setLetterSpacing(0.18f);
        label.setPadding(dp(4), dp(16), 0, dp(4));
        return label;
    }

    private TextView chip(String value, int color) {
        TextView chip = text(value, 10, color);
        chip.setTypeface(Typeface.DEFAULT_BOLD);
        chip.setLetterSpacing(0.06f);
        chip.setPadding(dp(10), dp(5), dp(10), dp(5));
        chip.setBackground(roundRect(Color.rgb(20, 25, 30), 20, STROKE, 1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = dp(8);
        chip.setLayoutParams(params);
        return chip;
    }

    private Button actionButton(String label, boolean primary, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setLetterSpacing(0.04f);
        button.setTextColor(primary ? Color.rgb(12, 12, 12) : TEXT);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(14), dp(9), dp(14), dp(9));
        button.setBackground(ripple(roundRect(
                primary ? GOLD : Color.rgb(30, 36, 43),
                11,
                primary ? GOLD : STROKE,
                1)));
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = dp(8);
        button.setLayoutParams(params);
        return button;
    }

    private TextView text(String value, float sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        return view;
    }

    private GradientDrawable roundRect(int fill, int radiusDp, int stroke, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) {
            drawable.setStroke(dp(strokeDp), stroke);
        }
        return drawable;
    }

    private RippleDrawable ripple(GradientDrawable background) {
        return new RippleDrawable(ColorStateList.valueOf(0x44FFFFFF), background, null);
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }

    private String vulkanVersion() {
        for (FeatureInfo feature : getPackageManager().getSystemAvailableFeatures()) {
            if (PackageManager.FEATURE_VULKAN_HARDWARE_VERSION.equals(feature.name)
                    && feature.version > 0) {
                int major = (feature.version >> 22) & 0x7F;
                int minor = (feature.version >> 12) & 0x3FF;
                return major + "." + minor;
            }
        }
        return "n/a";
    }

    private String ramGb() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        if (manager == null) {
            return "?";
        }
        manager.getMemoryInfo(info);
        return String.format(Locale.US, "%.1f GB", info.totalMem / (1024.0 * 1024.0 * 1024.0));
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }
}
