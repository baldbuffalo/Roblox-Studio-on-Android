package com.example.exeunpacker;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;
import java.io.*;

public class EngineManager {
    private static final String TAG = "EngineManager";
    // Android's build process automatically decompresses .gz assets during
    // packaging and strips the extension -- same treatment applies here as
    // it did for wine-support (see WINE_SUPPORT_ASSET_NAME below).
    private static final String GLIBC_ASSET_NAME = "glibc-lib.tar";

    /**
     * box64 is a genuine glibc-linked Linux binary (Winlator's own real,
     * working build) -- Android has no glibc at all (only Bionic), so its
     * ELF interpreter was patched at build time (via patchelf) to point at
     * a fixed path where THIS method extracts a real glibc runtime. This
     * must match exactly what was patched into the binary:
     * /data/data/com.example.exeunpacker/files/glibc-lib/
     */
    public static boolean extractGlibcIfNeeded(Context context) {
        File filesDir = context.getFilesDir();
        File destDir = new File(filesDir, "glibc-lib");
        File marker = new File(destDir, "ld-linux-aarch64.so.1");

        try {
            if (!marker.exists()) {
                Log.d(TAG, "Extracting bundled glibc runtime from assets...");
                File archive = copyAssetToFile(context, GLIBC_ASSET_NAME, filesDir);
                destDir.mkdirs();
                extractArchive(archive, destDir);
                archive.delete();
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "glibc runtime extraction failed", e);
            return false;
        }
    }

    // Android's build process automatically decompresses .gz assets during
    // packaging and strips the extension -- the file inside the built APK
    // ends up named "wine-support.tar" (plain, uncompressed), not
    // "wine-support.tar.gz". tar -xf auto-detects format regardless, so
    // only the name needs to match what's actually bundled.
    private static final String WINE_SUPPORT_ASSET_NAME = "wine-support.tar";
    private static final String ASSET_SUBDIR = "engine";

    /**
     * box64/wine64 themselves are NOT extracted here -- they ship as
     * jniLibs/<abi>/lib{box64,wine64}.so, which PackageManager extracts to
     * nativeLibraryDir at install time. That's the only location on modern
     * Android (API 29+, W^X enforcement) where an app-bundled binary is
     * actually executable; anything an app writes to its own storage at
     * runtime (assets extracted to getFilesDir()) can't be exec'd.
     */
    public static String getBox64Path(Context context) {
        return resolveNativeLibPath(context, "libbox64.so");
    }

    public static String getWinePath(Context context) {
        return resolveNativeLibPath(context, "libwine.so");
    }

    /**
     * On most devices, nativeLibraryDir points directly at the folder
     * containing the extracted .so files. On some devices/Android versions
     * (observed with useLegacyPackaging), there's an extra ABI-named
     * subfolder (e.g. "arm64") nested one level deeper than expected. Check
     * both locations rather than assuming one.
     */
    private static String resolveNativeLibPath(Context context, String libName) {
        String baseDir = context.getApplicationInfo().nativeLibraryDir;
        Log.d(TAG, "nativeLibraryDir reported as: " + baseDir);

        File direct = new File(baseDir, libName);
        Log.d(TAG, "Trying: " + direct.getAbsolutePath() + " -> exists=" + direct.exists());
        if (direct.exists()) {
            return direct.getAbsolutePath();
        }
        for (String abiFolder : new String[]{"arm64", "arm64-v8a", "x86_64"}) {
            File nested = new File(baseDir + "/" + abiFolder, libName);
            Log.d(TAG, "Trying: " + nested.getAbsolutePath() + " -> exists=" + nested.exists());
            if (nested.exists()) {
                return nested.getAbsolutePath();
            }
        }
        // Nothing found -- return the originally-expected path anyway so
        // the resulting error message shows where it actually looked.
        Log.e(TAG, "Could not locate " + libName + " anywhere under " + baseDir);
        return direct.getAbsolutePath();
    }

    /**
     * Wine's lib/share support tree is just data Wine reads at runtime (not
     * something Android itself execs), so it's fine as a plain archive
     * extracted to internal storage on first launch.
     */
    public static boolean extractWineSupportIfNeeded(Context context) {
        File filesDir = context.getFilesDir();
        File marker = new File(filesDir, "wine-support/lib");

        try {
            if (!marker.exists()) {
                Log.d(TAG, "Extracting bundled Wine support files from assets...");
                File archive = copyAssetToFile(context, WINE_SUPPORT_ASSET_NAME, filesDir);
                File destDir = new File(filesDir, "wine-support");
                destDir.mkdirs();
                extractArchive(archive, destDir);
                archive.delete();
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Wine support extraction failed", e);
            return false;
        }
    }

    private static File copyAssetToFile(Context context, String assetName, File destDir) throws IOException {
        AssetManager assets = context.getAssets();
        File outFile = new File(destDir, assetName);
        try (InputStream in = assets.open(ASSET_SUBDIR + "/" + assetName);
             OutputStream out = new FileOutputStream(outFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
        return outFile;
    }

    private static void extractArchive(File archive, File destination) throws IOException, InterruptedException {
        String command = "tar -xf " + archive.getAbsolutePath() + " -C " + destination.getAbsolutePath();
        Process process = Runtime.getRuntime().exec(new String[]{"/system/bin/sh", "-c", command});
        process.waitFor();
    }
}
