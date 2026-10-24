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
 * One predictable log file for the whole APK.
 *
 * Java lines and the ReXGlue native sink both append to
 * {@code getExternalFilesDir(null)/logfile.txt}. The native side receives the
 * same path through GameActivity.nativeSetPaths().
 */
final class Logx {
    private static final String TAG = "project1944";
    private static File sFile;

    private Logx() {}

    static synchronized File init(Context context) {
        if (sFile == null) {
            File dir = context.getExternalFilesDir(null);
            if (dir == null) {
                dir = context.getFilesDir();
            }
            sFile = new File(dir, "logfile.txt");
        }
        return sFile;
    }

    static synchronized File file(Context context) {
        return init(context);
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
}
