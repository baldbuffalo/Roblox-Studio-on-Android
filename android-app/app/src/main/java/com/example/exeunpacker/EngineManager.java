package com.example.exeunpacker;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;
import java.io.*;

public class EngineManager {
    private static final String TAG = "EngineManager";
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
        return context.getApplicationInfo().nativeLibraryDir + "/libbox64.so";
    }

    public static String getWinePath(Context context) {
        return context.getApplicationInfo().nativeLibraryDir + "/libwine.so";
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
