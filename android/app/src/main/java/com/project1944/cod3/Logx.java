package com.project1944.cod3;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * One fixed log file for the whole APK: {@code getExternalFilesDir(null)/native-log.txt}.
 *
 * Java lines and the ReXGlue native sink append to the same absolute path.
 * {@link #startSession(Context, String)} truncates it at the start of every
 * game session, so a new run never inherits lines or rotated copies from the
 * previous run.
 */
final class Logx {
    private static final String TAG = "project1944";
    private static final String FILE_NAME = "native-log.txt";
    private static final String LEGACY_FILE_NAME = "logfile.txt";
    private static File sFile;

    private Logx() {}

    static synchronized File init(Context context) {
        if (sFile == null) {
            File dir = context.getExternalFilesDir(null);
            if (dir == null) {
                dir = context.getFilesDir();
            }
            sFile = new File(dir, FILE_NAME);
        }
        return sFile;
    }

    static synchronized File file(Context context) {
        return init(context);
    }

    /**
     * Truncates the single log file and writes a session header. Called from
     * GameActivity before the native runtime starts, so the native append
     * sink sees an empty file and both sides share one file.
     */
    static synchronized void startSession(Context context, String detail) {
        File file = init(context);
        String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                .format(new Date());
        String header = "===== SESSION " + stamp + " build=" + versionName(context)
                + " " + detail + " =====\n";
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            out.write(header.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // Logging must never take the app down.
        }
        cleanupPreviousFiles(file);
    }

    static void d(String tag, String message) {
        write(Log.DEBUG, 'D', tag, message);
    }

    static void i(String tag, String message) {
        write(Log.INFO, 'I', tag, message);
    }

    static void w(String tag, String message) {
        write(Log.WARN, 'W', tag, message);
    }

    static void e(String tag, String message) {
        write(Log.ERROR, 'E', tag, message);
    }

    static void e(String tag, String message, Throwable error) {
        write(Log.ERROR, 'E', tag, message + ": " + Log.getStackTraceString(error));
    }

    private static synchronized void write(int priority, char level, String tag, String message) {
        Log.println(priority, tag, message);
        if (sFile == null) {
            return;
        }
        String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                .format(new Date());
        byte[] line = (stamp + " " + level + "/" + tag + ": " + message + "\n")
                .getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream out = new FileOutputStream(sFile, true)) {
            out.write(line);
        } catch (Exception ignored) {
            // Logging must never take the app down.
        }
    }

    private static String versionName(Context context) {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    /**
     * Removes the old split Java log and any rotated native files
     * (native-log.1.txt ... native-log.20.txt) left by earlier builds.
     */
    private static void cleanupPreviousFiles(File keep) {
        File dir = keep.getParentFile();
        if (dir == null) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.equals(keep)) {
                continue;
            }
            String name = file.getName();
            if (LEGACY_FILE_NAME.equals(name)
                    || (name.startsWith("native-log.") && name.endsWith(".txt"))) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }
}
