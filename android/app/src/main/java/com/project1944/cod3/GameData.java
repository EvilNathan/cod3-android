package com.project1944.cod3;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.Locale;

/** Game folder resolution and the supported default.xex checksum. */
final class GameData {
    static final String SUPPORTED_SHA256 =
            "2944EEC7D1231AD6798B5F9FADF8855F5E489296B22EAB45B27A577CEE23692";

    private static final String PREFS = "project1944";
    private static final String KEY_PATH = "game_path";

    private GameData() {}

    /** Default location the user can populate directly: /sdcard/cod3. */
    static String defaultPath() {
        return new File(Environment.getExternalStorageDirectory(), "cod3").getAbsolutePath();
    }

    static String resolve(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String saved = prefs.getString(KEY_PATH, null);
        return saved != null && !saved.isEmpty() ? saved : defaultPath();
    }

    static void save(Context context, String path) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PATH, path)
                .apply();
    }

    static boolean hasXex(String path) {
        return path != null && new File(path, "default.xex").isFile();
    }

    /** True when the configured path is a disc image file (.iso). */
    static boolean isIso(String path) {
        return path != null
                && path.toLowerCase(Locale.US).endsWith(".iso")
                && new File(path).isFile();
    }

    static String sha256(File file) {
        try (FileInputStream in = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1 << 16];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            StringBuilder hex = new StringBuilder(64);
            for (byte value : digest.digest()) {
                hex.append(String.format(Locale.US, "%02X", value));
            }
            return hex.toString();
        } catch (Exception e) {
            Logx.e("GameData", "SHA-256 failed for " + file, e);
            return null;
        }
    }

    /** Queries a SAF document's display name (fallback to "disc.iso"). */
    static String queryDisplayName(Context context, Uri docUri) {
        try (android.database.Cursor cursor = context.getContentResolver()
                .query(docUri, new String[] {
                        android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(
                        android.provider.OpenableColumns.DISPLAY_NAME);
                if (index >= 0 && !cursor.isNull(index)) {
                    String name = cursor.getString(index);
                    if (name != null && !name.isEmpty()) {
                        return name;
                    }
                }
            }
        } catch (Exception e) {
            Logx.w("GameData", "display name query failed for " + docUri
                    + ": " + e.getMessage());
        }
        return "disc.iso";
    }

    /** Converts a SAF tree URI from the primary external volume to a real path. */
    static String treeToPath(Uri treeUri) {
        try {
            if (!"com.android.externalstorage.documents".equals(treeUri.getAuthority())) {
                return null;
            }
            String docId = DocumentsContract.getTreeDocumentId(treeUri);
            return volumeIdToPath(docId);
        } catch (Exception e) {
            Logx.e("GameData", "treeToPath failed: " + treeUri, e);
            return null;
        }
    }

    /** Converts a SAF single-document URI (e.g. a picked .iso) to a real path. */
    static String documentToPath(Context context, Uri docUri) {
        String path = documentToPathImpl(context, docUri);
        if (path != null) {
            return path;
        }
        // Fallback: many providers (Downloads on Android 15, Files by Google,
        // third-party managers) expose the real path through the legacy _data
        // column even when the authority is not externalstorage.
        try (android.database.Cursor cursor = context.getContentResolver()
                .query(docUri, new String[] {"_data"}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex("_data");
                if (index >= 0 && !cursor.isNull(index)) {
                    String data = cursor.getString(index);
                    if (data != null && !data.isEmpty() && new File(data).isFile()) {
                        Logx.i("GameData", "_data fallback resolved " + docUri + " -> " + data);
                        return data;
                    }
                }
            }
        } catch (Exception e) {
            Logx.w("GameData", "_data fallback failed for " + docUri + ": " + e.getMessage());
        }
        return null;
    }

    private static String documentToPathImpl(Context context, Uri docUri) {
        try {
            if (!"com.android.externalstorage.documents".equals(docUri.getAuthority())) {
                return null;
            }
            String docId = DocumentsContract.getDocumentId(docUri);
            return volumeIdToPath(docId);
        } catch (Exception e) {
            Logx.e("GameData", "documentToPath failed: " + docUri, e);
            return null;
        }
    }

    /** Maps a "volume:relative/path" document id to an absolute host path. */
    private static String volumeIdToPath(String docId) {
        int colon = docId.indexOf(':');
        if (colon < 0) {
            return null;
        }
        String volume = docId.substring(0, colon);
        String relative = docId.substring(colon + 1);
        if (!"primary".equalsIgnoreCase(volume) && !"home".equalsIgnoreCase(volume)) {
            // SD cards and USB volumes are not mapped: the native filesystem
            // layer only understands absolute paths such as /storage/XXXX-XXXX.
            File[] externals = new File("/storage").listFiles();
            if (externals != null) {
                for (File candidate : externals) {
                    if (candidate.getName().equalsIgnoreCase(volume)) {
                        return new File(candidate, relative).getAbsolutePath();
                    }
                }
            }
            return null;
        }
        File base = Environment.getExternalStorageDirectory();
        return relative.isEmpty() ? base.getAbsolutePath()
                : new File(base, relative).getAbsolutePath();
    }
}
