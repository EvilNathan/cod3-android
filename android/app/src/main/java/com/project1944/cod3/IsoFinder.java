package com.project1944.cod3;

import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Finds disc images (.iso) directly on the device's storage.
 *
 * With All-files access (MANAGE_EXTERNAL_STORAGE) the app can walk
 * /storage/... with java.io.File - no Storage Access Framework involved, so
 * the Android 15 picker's provider quirks cannot break path resolution.
 */
final class IsoFinder {

    private static final int MAX_RESULTS = 100;
    private static final int MAX_DIRS_VISITED = 8000;
    private static final long MIN_ISO_BYTES = 1L << 20;  // 1 MB

    /** One discovered disc image. */
    static final class Entry {
        final String path;
        final long size;

        Entry(String path, long size) {
            this.path = path;
            this.size = size;
        }

        String name() {
            return new File(path).getName();
        }
    }

    private IsoFinder() {}

    /** Scans /storage (internal + SD cards). Run on a background thread. */
    static List<Entry> scan() {
        final List<Entry> results = new ArrayList<>();
        final int[] dirsVisited = {0};

        File storage = new File("/storage");
        File[] volumes = storage.listFiles();
        if (volumes == null) {
            return results;
        }

        // Internal storage first, then SD cards.
        List<File> ordered = new ArrayList<>(Arrays.asList(volumes));
        ordered.sort((a, b) -> {
            boolean aInternal = "emulated".equals(a.getName());
            boolean bInternal = "emulated".equals(b.getName());
            if (aInternal != bInternal) {
                return aInternal ? -1 : 1;
            }
            return a.getName().compareToIgnoreCase(b.getName());
        });

        for (File volume : ordered) {
            File root = "emulated".equals(volume.getName())
                    ? new File(volume, "0")
                    : volume;
            if (!root.isDirectory()) {
                continue;
            }
            if ("emulated".equals(volume.getName())) {
                // Prefer the obvious places first: root, Download, Documents.
                scanShallow(root, 0, results, dirsVisited);
            } else {
                scan(root, 0, results, dirsVisited);
            }
            if (results.size() >= MAX_RESULTS || dirsVisited[0] > MAX_DIRS_VISITED) {
                break;
            }
        }
        results.sort((a, b) -> {
            String an = a.name().toLowerCase(Locale.US);
            String bn = b.name().toLowerCase(Locale.US);
            return an.compareTo(bn);
        });
        return results;
    }

    /** Top-level of internal storage + a shallow pass over likely folders. */
    private static void scanShallow(File dir, int depth, List<Entry> results, int[] dirs) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        Arrays.sort(children, Comparator.comparing(File::getName));
        for (File child : children) {
            if (results.size() >= MAX_RESULTS || dirs[0] > MAX_DIRS_VISITED) {
                return;
            }
            String name = child.getName();
            if (name.startsWith(".") || "Android".equals(name)) {
                continue;
            }
            if (child.isDirectory()) {
                dirs[0]++;
                // Depth 0: every top-level folder. Depth >= 1: only the places
                // a disc image is usually dropped (Download, Documents, ...).
                if (depth >= 4 || (depth >= 1 && !isLikelyFolder(name))) {
                    continue;
                }
                scanShallow(child, depth + 1, results, dirs);
            } else if (isIso(child)) {
                results.add(new Entry(child.getAbsolutePath(), child.length()));
            }
        }
    }

    private static boolean isLikelyFolder(String name) {
        String n = name.toLowerCase(Locale.US);
        return n.contains("download")
                || n.contains("document")
                || n.equals("cod3")
                || n.equals("games")
                || n.equals("game")
                || n.equals("roms")
                || n.equals("iso")
                || n.equals("xbox");
    }

    private static void scan(File dir, int depth, List<Entry> results, int[] dirs) {
        if (results.size() >= MAX_RESULTS || dirs[0] > MAX_DIRS_VISITED) {
            return;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (results.size() >= MAX_RESULTS || dirs[0] > MAX_DIRS_VISITED) {
                return;
            }
            String name = child.getName();
            if (name.startsWith(".")) {
                continue;
            }
            if (child.isDirectory()) {
                if ("Android".equals(name) || depth >= 4) {
                    continue;
                }
                dirs[0]++;
                scan(child, depth + 1, results, dirs);
            } else if (isIso(child)) {
                results.add(new Entry(child.getAbsolutePath(), child.length()));
            }
        }
    }

    private static boolean isIso(File file) {
        return file.getName().toLowerCase(Locale.US).endsWith(".iso")
                && file.length() >= MIN_ISO_BYTES;
    }

    static String humanSize(long bytes) {
        if (bytes >= 1L << 30) {
            return String.format(Locale.US, "%.2f GB", bytes / (double) (1L << 30));
        }
        if (bytes >= 1L << 20) {
            return String.format(Locale.US, "%.1f MB", bytes / (double) (1L << 20));
        }
        return bytes + " B";
    }

    /** Destination for the copy fallback when a SAF URI cannot be mapped. */
    static File copyDestinationFor(String displayName) {
        File dir = new File(Environment.getExternalStorageDirectory(), "cod3");
        dir.mkdirs();
        String name = (displayName == null || displayName.isEmpty())
                ? "disc.iso" : displayName;
        if (!name.toLowerCase(Locale.US).endsWith(".iso")) {
            name += ".iso";
        }
        return new File(dir, name);
    }
}
