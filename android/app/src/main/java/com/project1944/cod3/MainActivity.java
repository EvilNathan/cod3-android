package com.project1944.cod3;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
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
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

/**
 * Launcher, diagnostics and game-data setup for the Android port.
 *
 * The screen is built in code on purpose: the APK has no resource-heavy UI and
 * this keeps the whole layout visible in one file. Game data resolution lives
 * in GameData, disc-image discovery in IsoFinder, logging in Logx.
 */
public final class MainActivity extends Activity {
    private static final int PICK_GAME_FOLDER = 1001;
    private static final int PERMISSION_READ_STORAGE = 1002;
    private static final int PICK_GAME_ISO = 1003;

    // Palette: deep navy surfaces, one gold accent, soft positive/negative
    // greens and reds for status. Monochrome first, gold second.
    private static final int BG_TOP = Color.rgb(16, 22, 32);
    private static final int BG_BOTTOM = Color.rgb(8, 11, 16);
    private static final int PANEL = Color.rgb(20, 27, 38);
    private static final int PANEL_SOFT = Color.rgb(26, 34, 48);
    private static final int STROKE = Color.rgb(38, 48, 66);
    private static final int TEXT = Color.rgb(238, 242, 248);
    private static final int MUTED = Color.rgb(142, 154, 172);
    private static final int FAINT = Color.rgb(96, 106, 122);
    private static final int GOLD = Color.rgb(232, 180, 74);
    private static final int GOLD_DARK = Color.rgb(20, 17, 9);
    private static final int GREEN = Color.rgb(72, 203, 143);
    private static final int RED = Color.rgb(255, 107, 107);

    private TextView dataPath;
    private TextView dataState;
    private TextView dataPill;
    private TextView allFilesState;
    private Button allFilesButton;
    private int checkGeneration;
    private Dialog isoDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Logx.init(this);
        Logx.i("MainActivity", "launcher opened; log=" + Logx.file(this));

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        getWindow().setStatusBarColor(BG_BOTTOM);
        getWindow().setNavigationBarColor(BG_BOTTOM);
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
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception e) {
            Logx.w("MainActivity", "persistable permission rejected: " + e.getMessage());
        }
        if (requestCode == PICK_GAME_ISO) {
            String path = GameData.documentToPath(this, uri);
            if (path != null) {
                selectPath(path);
                return;
            }
            // The picker returned something exotic (downloads provider on
            // Android 15, cloud, USB OTG). Stream-copy it into storage the
            // app can read directly.
            Logx.i("MainActivity", "unmappable document, copying: " + uri);
            copyFromUri(uri, GameData.queryDisplayName(this, uri));
        } else if (requestCode == PICK_GAME_FOLDER) {
            String path = GameData.treeToPath(uri);
            if (path == null) {
                Logx.e("MainActivity", "picker returned unmappable tree: " + uri);
                toast("That folder is not on accessible storage. Use /sdcard/cod3 instead.");
                return;
            }
            selectPath(path);
        }
    }

    private void selectPath(String path) {
        GameData.save(this, path);
        Logx.i("MainActivity", "game data selected: " + path);
        refreshData();
    }

    /** Streams a SAF document into /sdcard/cod3/ when no real path exists. */
    private void copyFromUri(Uri uri, String displayName) {
        File destination = IsoFinder.copyDestinationFor(displayName);
        dataState.setText("Copying disc image to " + destination.getAbsolutePath() + " ...");
        dataState.setTextColor(MUTED);
        new Thread(() -> {
            String result;
            int color;
            try (InputStream in = getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(destination)) {
                if (in == null) {
                    throw new IllegalStateException("content provider refused the stream");
                }
                byte[] buffer = new byte[1 << 16];
                long total = 0;
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                    total += read;
                }
                out.getFD().sync();
                Logx.i("MainActivity", "copied disc image: " + total + " bytes -> "
                        + destination);
                result = "Copied " + IsoFinder.humanSize(total) + " from the picker.";
                color = GREEN;
                selectPath(destination.getAbsolutePath());
            } catch (Exception e) {
                Logx.e("MainActivity", "copy from picker failed", e);
                result = "Could not copy the picked file: " + e.getMessage();
                color = RED;
            }
            final String finalResult = result;
            final int finalColor = color;
            runOnUiThread(() -> {
                dataState.setText(finalResult);
                dataState.setTextColor(finalColor);
            });
        }, "iso-copy").start();
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
                new int[] {BG_TOP, BG_BOTTOM, BG_BOTTOM}));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll, match());

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(32), dp(24), dp(32), dp(28));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content.addView(buildHero());

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        chips.setPadding(0, dp(16), 0, dp(2));
        chips.addView(chip(Build.MANUFACTURER.toUpperCase(Locale.US) + " " + Build.MODEL));
        chips.addView(chip("ANDROID " + Build.VERSION.RELEASE));
        chips.addView(chip("VULKAN " + vulkanVersion(), GOLD));
        chips.addView(chip(ramGb(), GREEN));
        content.addView(chips);

        content.addView(buildGameCard());

        content.addView(sectionLabel("SYSTEM"));
        content.addView(buildSystemCard());

        content.addView(buildLaunchButton());

        TextView footer = text("Log: " + Logx.file(this).getAbsolutePath()
                + "\nTap to copy the path. The game itself is not included -"
                + " bring your own disc.", 10, FAINT);
        footer.setTypeface(Typeface.MONOSPACE);
        footer.setPadding(dp(4), dp(16), dp(4), 0);
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
        hero.setBackground(roundRect(0xFF0D1219, 22, STROKE, 1));
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(132));
        hero.setLayoutParams(heroParams);

        ImageViewStub image = new ImageViewStub(this);
        hero.addView(image, match());
        image.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] {0xFF0D1219, 0xB00D1219, 0x00}));
        image.setClickable(false);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setGravity(Gravity.CENTER_VERTICAL);
        titles.setPadding(dp(24), dp(18), dp(24), dp(18));
        hero.addView(titles, match());

        TextView eyebrow = text("ANDROID NATIVE PORT \u2022 XBOX 360 RE-COMPILED", 10, GOLD);
        eyebrow.setTypeface(Typeface.DEFAULT_BOLD);
        eyebrow.setLetterSpacing(0.24f);
        titles.addView(eyebrow);

        TextView title = text("PROJECT 1944", 32, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLetterSpacing(0.02f);
        title.setPadding(0, dp(4), 0, 0);
        titles.addView(title);

        TextView subtitle = text("Call of Duty 3 \u00b7 ReXGlue runtime \u00b7 Vulkan \u00b7 arm64",
                11, MUTED);
        subtitle.setPadding(0, dp(4), 0, 0);
        titles.addView(subtitle);

        // Thin gold hairline along the bottom edge of the hero.
        View hairline = new View(this);
        FrameLayout.LayoutParams hairlineParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(2));
        hairlineParams.gravity = Gravity.BOTTOM;
        hairline.setLayoutParams(hairlineParams);
        hairline.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] {GOLD, 0x00E8B44A, 0x00000000}));
        hero.addView(hairline);
        return hero;
    }

    /** Minimal image view stub: the hero relies on gradients, no artwork. */
    private static final class ImageViewStub extends View {
        ImageViewStub(Context context) {
            super(context);
        }
    }

    private View buildGameCard() {
        LinearLayout card = card();

        // Header row: label left, live status pill right.
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = text("GAME DATA", 11, MUTED);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setLetterSpacing(0.2f);
        header.addView(label);
        LinearLayout filler = new LinearLayout(this);
        filler.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        header.addView(filler);
        dataPill = pill("CHECKING", GOLD);
        header.addView(dataPill);
        card.addView(header);

        dataPath = text("", 11, MUTED);
        dataPath.setTypeface(Typeface.MONOSPACE);
        dataPath.setPadding(dp(10), dp(10), dp(10), dp(10));
        dataPath.setBackground(roundRect(PANEL_SOFT, 10, STROKE, 1));
        LinearLayout.LayoutParams pathParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pathParams.topMargin = dp(12);
        dataPath.setLayoutParams(pathParams);
        card.addView(dataPath);

        dataState = text("Checking game data...", 12, MUTED);
        dataState.setPadding(dp(2), dp(10), dp(2), dp(4));
        card.addView(dataState);

        // Primary action: pick a disc image.
        LinearLayout primaryRow = new LinearLayout(this);
        primaryRow.setOrientation(LinearLayout.HORIZONTAL);
        primaryRow.setGravity(Gravity.CENTER_VERTICAL);
        primaryRow.setPadding(0, dp(12), 0, 0);
        primaryRow.addView(actionButton("PICK ISO", true, v -> showIsoDialog()));
        primaryRow.addView(actionButton("PICK FOLDER", false, v -> pickFolder()));
        primaryRow.addView(actionButton("USE /sdcard/cod3", false, v -> useDefaultFolder()));
        primaryRow.addView(actionButton("RECHECK", false, v -> refreshData()));
        card.addView(primaryRow);

        allFilesButton = actionButton("GRANT ALL-FILES ACCESS", false,
                v -> requestAllFilesAccess());
        LinearLayout.LayoutParams allParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        allParams.topMargin = dp(10);
        allFilesButton.setLayoutParams(allParams);
        card.addView(allFilesButton);

        allFilesState = text("", 11, MUTED);
        allFilesState.setPadding(dp(2), dp(8), 0, 0);
        card.addView(allFilesState);
        return card;
    }

    private View buildSystemCard() {
        LinearLayout card = card();
        PackageManager pm = getPackageManager();
        String vulkan = pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION)
                ? "Vulkan " + vulkanVersion() : "not reported";
        String gamepad = pm.hasSystemFeature(PackageManager.FEATURE_GAMEPAD)
                ? "detected" : "not reported \u00b7 touch overlay available";
        String version = "unknown";
        try {
            version = pm.getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        card.addView(infoRow("DEVICE", Build.MANUFACTURER + " " + Build.MODEL));
        card.addView(infoRow("ANDROID", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT
                + ")"));
        card.addView(infoRow("MEMORY", ramGb() + " total"));
        card.addView(infoRow("GPU API", vulkan));
        card.addView(infoRow("GAMEPAD", gamepad));
        card.addView(infoRow("BUILD", version + " (debug)"));
        return card;
    }

    private View infoRow(String key, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(7), dp(2), dp(7));

        TextView keyView = text(key, 11, FAINT);
        keyView.setTypeface(Typeface.MONOSPACE);
        keyView.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams keyParams = new LinearLayout.LayoutParams(
                dp(92), ViewGroup.LayoutParams.WRAP_CONTENT);
        keyView.setLayoutParams(keyParams);
        row.addView(keyView);

        TextView valueView = text(value, 12, TEXT);
        valueView.setTypeface(Typeface.MONOSPACE);
        row.addView(valueView);
        return row;
    }

    private View buildLaunchButton() {
        Button launch = new Button(this);
        launch.setText("\u25B6  LAUNCH GAME");
        launch.setAllCaps(false);
        launch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        launch.setTypeface(Typeface.DEFAULT_BOLD);
        launch.setLetterSpacing(0.12f);
        launch.setTextColor(GOLD_DARK);
        GradientDrawable launchBackground = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] {GOLD, Color.rgb(210, 158, 58)});
        launchBackground.setCornerRadius(dp(16));
        launch.setBackground(ripple(launchBackground));
        launch.setOnClickListener(v -> launchGame());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        params.topMargin = dp(18);
        launch.setLayoutParams(params);
        return launch;
    }

    // --------------------------------------------------------- ISO browser

    /** In-app disc image browser - reads /storage directly, no SAF needed. */
    private void showIsoDialog() {
        Dialog dialog = new Dialog(this);
        isoDialog = dialog;
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setOnDismissListener(d -> {
            if (isoDialog == d) {
                isoDialog = null;
            }
        });

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(22), dp(20), dp(22), dp(18));
        panel.setBackground(roundRect(0xFF111722, 20, STROKE, 1));

        TextView title = text("SELECT DISC IMAGE", 16, TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLetterSpacing(0.04f);
        panel.addView(title);

        TextView subtitle = text("Disc images found on this device. The game boots"
                + " straight from the .iso \u2014 no extraction.", 11, MUTED);
        subtitle.setPadding(0, dp(6), 0, dp(10));
        panel.addView(subtitle);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(300));
        scroll.setLayoutParams(scrollParams);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView scanning = text("Scanning storage for .iso files...", 12, GOLD);
        scanning.setPadding(dp(4), dp(16), dp(4), dp(16));
        list.addView(scanning);
        panel.addView(scroll);

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(0, dp(14), 0, 0);
        Button rescan = actionButton("SCAN AGAIN", false, v -> {
            list.removeAllViews();
            list.addView(scanning);
            runScan(list, scanning);
        });
        footer.addView(rescan);
        Button systemPicker = actionButton("SYSTEM PICKER", false, v -> {
            dialog.dismiss();
            pickIso();
        });
        footer.addView(systemPicker);
        LinearLayout filler = new LinearLayout(this);
        filler.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        footer.addView(filler);
        Button close = actionButton("CLOSE", false, v -> dialog.dismiss());
        footer.addView(close);
        panel.addView(footer);

        int width = Math.min(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.88f),
                dp(720));
        dialog.setContentView(panel, new ViewGroup.LayoutParams(
                width, ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.show();
        runScan(list, scanning);
    }

    private void runScan(LinearLayout list, TextView scanning) {
        new Thread(() -> {
            List<IsoFinder.Entry> entries = IsoFinder.scan();
            runOnUiThread(() -> {
                list.removeAllViews();
                if (entries.isEmpty()) {
                    TextView empty = text("No .iso files found.\n\n"
                            + "Copy your disc image to /sdcard (e.g. /sdcard/Download),"
                            + " then press SCAN AGAIN. If this device has no All-files"
                            + " access yet, grant it first.", 12, MUTED);
                    empty.setPadding(dp(4), dp(18), dp(4), dp(18));
                    empty.setLineSpacing(dp(3), 1.05f);
                    list.addView(empty);
                    return;
                }
                for (IsoFinder.Entry entry : entries) {
                    list.addView(isoRow(entry, () -> {
                        GameData.save(this, entry.path);
                        Logx.i("MainActivity", "disc image selected: " + entry.path);
                        refreshData();
                        if (isoDialog != null) {
                            isoDialog.dismiss();
                            isoDialog = null;
                        }
                    }));
                }
            });
        }, "iso-scan").start();
    }

    private View isoRow(IsoFinder.Entry entry, Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(ripple(roundRect(PANEL_SOFT, 12, STROKE, 1)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        row.setLayoutParams(params);

        // Disc glyph: small gold ring.
        TextView glyph = text("\u25C9", 16, GOLD);
        glyph.setGravity(Gravity.CENTER);
        row.addView(glyph);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12), 0, dp(8), 0);
        TextView name = text(entry.name(), 13, TEXT);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        body.addView(name);
        TextView meta = text(IsoFinder.humanSize(entry.size) + "  \u00b7  " + entry.path,
                10, MUTED);
        meta.setTypeface(Typeface.MONOSPACE);
        meta.setPadding(0, dp(2), 0, 0);
        body.addView(meta);
        row.addView(body);

        row.setOnClickListener(v -> onClick.run());
        return row;
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

    private void pickIso() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/x-iso9660-image", "application/x-cd-image",
                "application/octet-stream"});
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, PICK_GAME_ISO);
            Logx.i("MainActivity", "system disc image picker opened");
        } catch (Exception e) {
            Logx.e("MainActivity", "no document picker", e);
            toast("No system file picker available. Put the .iso in /sdcard.");
        }
    }

    private void launchGame() {
        String path = GameData.resolve(this);
        if (!GameData.hasXex(path) && !GameData.isIso(path)) {
            toast("Game data missing - default.xex is not readable, the guest cannot boot.");
        }
        Logx.i("MainActivity", "launching GameActivity");
        startActivity(new Intent(this, GameActivity.class));
    }

    private void refreshData() {
        final int generation = ++checkGeneration;
        final String path = GameData.resolve(this);
        dataPath.setText(path);
        setPill("CHECKING", GOLD);
        dataState.setTextColor(MUTED);
        dataState.setText("Checking game data...");

        new Thread(() -> {
            String state;
            int color;
            boolean ready;
            if (GameData.isIso(path)) {
                // Boots directly from the disc image via the GDFX/XISO VFS
                // device - the game itself stays inside the image. The native
                // side logs the default.xex digest at boot for verification.
                state = "Disc image \u00b7 " + IsoFinder.humanSize(new File(path).length())
                        + " \u00b7 mounts directly at boot, no extraction needed.";
                color = GREEN;
                ready = true;
                Logx.i("MainActivity", "game data: disc image at " + path);
            } else {
                File xex = new File(path, "default.xex");
                if (!xex.isFile()) {
                    state = diagnoseMissingData(path);
                    color = RED;
                    ready = false;
                    Logx.w("MainActivity", "game data: " + state.replace('\n', ' '));
                } else {
                    Logx.i("MainActivity", "hashing " + xex + " (" + xex.length() + " bytes)");
                    String sha = GameData.sha256(xex);
                    if (GameData.SUPPORTED_SHA256.equalsIgnoreCase(sha)) {
                        state = "default.xex verified \u00b7 SHA-256 matches the supported build.";
                        color = GREEN;
                        ready = true;
                    } else {
                        state = "SHA-256 mismatch. This revision is untested.\n" + sha;
                        color = GOLD;
                        ready = true;
                    }
                }
            }
            final String finalState = state;
            final int finalColor = color;
            final boolean finalReady = ready;
            runOnUiThread(() -> {
                if (generation != checkGeneration) {
                    return;
                }
                dataState.setTextColor(finalColor);
                dataState.setText(finalState);
                setPill(finalReady ? "READY" : "MISSING", finalReady ? GREEN : RED);
                Logx.i("MainActivity", "game data check: " + finalState.replace('\n', ' '));
            });
        }, "game-data-check").start();
        refreshAllFilesState();
    }

    /**
     * Explains why default.xex is not visible. The common mistakes are a
     * missing All-files access grant (which hides /sdcard/cod3 from the app)
     * and pointing at a folder that has not been populated yet. A disc image
     * is fully supported via PICK ISO - extraction is not required.
     */
    private String diagnoseMissingData(String path) {
        File dir = new File(path);
        boolean allFiles = Build.VERSION.SDK_INT < Build.VERSION_CODES.R
                || Environment.isExternalStorageManager();
        if (!dir.exists()) {
            return "Folder not found: " + path
                    + (allFiles ? "" : "\nAll-files access is off, so /sdcard/cod3 is invisible.");
        }
        if (!dir.isDirectory()) {
            return "Not a folder: " + path;
        }
        String[] names = dir.list();
        if (names == null) {
            return "Folder is not readable: " + path
                    + (allFiles ? "" : "\nGrant All-files access first.");
        }
        int isoCount = 0;
        StringBuilder sample = new StringBuilder();
        for (String name : names) {
            if (name.toLowerCase(Locale.US).endsWith(".iso")) {
                isoCount++;
            }
            if (sample.length() < 80) {
                sample.append(sample.length() == 0 ? "" : ", ").append(name);
            }
        }
        StringBuilder out = new StringBuilder();
        out.append("default.xex is missing. The folder holds ")
                .append(names.length).append(" item(s): ").append(sample);
        if (names.length > 0) {
            out.append(" ...");
        }
        if (isoCount > 0) {
            out.append("\nAn ISO file is present - no extraction is needed: press")
                    .append(" PICK ISO and select it. The game boots directly")
                    .append(" from the disc image.");
        } else {
            out.append("\nCopy the extracted disc contents here, or press PICK ISO")
                    .append(" and choose the .iso directly.");
        }
        return out.toString();
    }

    private void refreshAllFilesState() {
        if (allFilesButton == null) {
            return;
        }
        boolean modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
        boolean granted = modern
                ? Environment.isExternalStorageManager()
                : checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
        allFilesButton.setVisibility(granted ? View.GONE : View.VISIBLE);
        allFilesState.setText(granted
                ? (modern ? "All-files access granted." : "Storage read permission granted.")
                : "All-files access is required to read /sdcard in place.");
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
        card.setPadding(dp(20), dp(16), dp(20), dp(16));
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
        label.setLetterSpacing(0.2f);
        label.setPadding(dp(4), dp(18), 0, dp(4));
        return label;
    }

    private TextView chip(String value) {
        return chip(value, TEXT);
    }

    private TextView chip(String value, int color) {
        TextView chip = text(value, 10, color);
        chip.setTypeface(Typeface.DEFAULT_BOLD);
        chip.setLetterSpacing(0.08f);
        chip.setPadding(dp(11), dp(5), dp(11), dp(5));
        chip.setBackground(roundRect(PANEL, 22, STROKE, 1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = dp(8);
        chip.setLayoutParams(params);
        return chip;
    }

    /** Status pill with a colored dot, shown in the game card header. */
    private TextView pill(String label, int color) {
        TextView pill = text("\u25CF " + label, 10, color);
        pill.setTypeface(Typeface.DEFAULT_BOLD);
        pill.setLetterSpacing(0.1f);
        pill.setPadding(dp(10), dp(5), dp(10), dp(5));
        pill.setBackground(roundRect(blend(color, PANEL, 0.14f), 22,
                blend(color, STROKE, 0.45f), 1));
        return pill;
    }

    private void setPill(String label, int color) {
        if (dataPill == null) {
            return;
        }
        dataPill.setText("\u25CF " + label);
        dataPill.setTextColor(color);
        dataPill.setBackground(roundRect(blend(color, PANEL, 0.14f), 22,
                blend(color, STROKE, 0.45f), 1));
    }

    private Button actionButton(String label, boolean primary, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setLetterSpacing(0.05f);
        button.setTextColor(primary ? GOLD_DARK : TEXT);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(15), dp(9), dp(15), dp(9));
        button.setBackground(ripple(roundRect(
                primary ? GOLD : PANEL_SOFT,
                12,
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
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), background, null);
    }

    private static int blend(int color, int base, float amount) {
        int r = Math.round(Color.red(base) * (1 - amount) + Color.red(color) * amount);
        int g = Math.round(Color.green(base) * (1 - amount) + Color.green(color) * amount);
        int b = Math.round(Color.blue(base) * (1 - amount) + Color.blue(color) * amount);
        return Color.rgb(r, g, b);
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
