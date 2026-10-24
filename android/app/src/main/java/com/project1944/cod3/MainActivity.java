package com.project1944.cod3;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * Minimal launcher for the Android port.
 *
 * M0a is a build smoke test: it proves the NDK, SDL3, JNI and Vulkan surface
 * path work on the device before any ReXGlue runtime or game code is added.
 * The game setup screen (game folder, SHA-256 verification, settings) replaces
 * this in a later milestone.
 */
public final class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        setContentView(buildUi());
        hideSystemBars();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
        }
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(48), dp(32), dp(48), dp(32));
        root.setBackgroundColor(Color.rgb(10, 12, 14));

        TextView eyebrow = text("ANDROID NATIVE PORT", 13, Color.rgb(232, 180, 74));
        eyebrow.setTypeface(Typeface.DEFAULT_BOLD);
        eyebrow.setLetterSpacing(0.18f);
        root.addView(eyebrow);

        TextView title = text("PROJECT 1944", 40, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, dp(6), 0, dp(12));
        root.addView(title);

        TextView subtitle = text(
                "Milestone M0a — SDL3 + Vulkan surface smoke test.\n"
                        + "This is not the game yet. It validates the Android build "
                        + "and GPU presentation path.",
                16,
                Color.rgb(176, 184, 192));
        subtitle.setLineSpacing(dp(3), 1.0f);
        root.addView(subtitle);

        PackageManager pm = getPackageManager();
        boolean vulkan = pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION);
        boolean gamepad = pm.hasSystemFeature(PackageManager.FEATURE_GAMEPAD);
        long maxHeapMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L);

        String details = String.format(Locale.US,
                "Device   : %s %s\n"
                        + "Android  : %s (API %d)\n"
                        + "Heap max : %d MB\n"
                        + "Vulkan   : %s\n"
                        + "Gamepad  : %s",
                Build.MANUFACTURER,
                Build.MODEL,
                Build.VERSION.RELEASE,
                Build.VERSION.SDK_INT,
                maxHeapMb,
                vulkan ? "available" : "MISSING",
                gamepad ? "detected" : "not reported");
        TextView info = text(details, 15, Color.rgb(214, 220, 226));
        info.setTypeface(Typeface.MONOSPACE);
        info.setLineSpacing(dp(2), 1.0f);
        info.setPadding(0, dp(22), 0, dp(24));
        root.addView(info);

        Button start = new Button(this);
        start.setText("Start Vulkan smoke test");
        start.setAllCaps(false);
        start.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        start.setTextColor(Color.rgb(12, 12, 12));
        start.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(232, 180, 74)));
        start.setOnClickListener(v -> startActivity(new Intent(this, GameActivity.class)));
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        startParams.topMargin = dp(4);
        root.addView(start, startParams);

        TextView note = text(
                "If the screen stays black, check `adb logcat -s project1944 SDL`.",
                12,
                Color.rgb(128, 136, 144));
        note.setPadding(0, dp(14), 0, 0);
        root.addView(note);

        return root;
    }

    private TextView text(String value, float sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        return view;
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
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
