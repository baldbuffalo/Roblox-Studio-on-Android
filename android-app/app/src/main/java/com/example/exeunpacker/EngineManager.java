package com.example.exeunpacker;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;
import java.io.*;

public class EngineManager {
    private static final String TAG = "EngineManager";

    // These filenames must match what compile_engine.yml commits into
    // android-app/app/src/main/assets/engine/.
    private static final String WINE_ASSET_NAME = "wine64-latest.tar.xz";
    private static final String BOX64_ASSET_NAME = "box64-latest.tar.gz";
    private static final String ASSET_SUBDIR = "engine";

    /**
     * Both Box64 and Wine ship bundled inside the APK's assets/engine/
     * folder (no network download needed). This extracts them into internal
     * storage on first run, since assets/ itself is a read-only zip entry
     * and binaries there can't be executed directly.
     *
     * @param needsBox64 true on arm64-v8a devices, where the x86_64 Wine
     *                   build must run through Box64's dynamic recompiler.
     *                   False on native x86_64 devices, which run Wine
     *                   directly with no translation layer -- Box64 is
     *                   skipped and removed there if present.
     */
    public static boolean extractBundledEngineIfNeeded(Context context, boolean needsBox64) {
        File filesDir = context.getFilesDir();
        File wineMarker = new File(filesDir, "wine/bin/wine64");
        File box64Bin = new File(filesDir, "box64");

        try {
            if (!wineMarker.exists()) {
                Log.d(TAG, "Extracting bundled Wine from assets...");
                File wineArchive = copyAssetToFile(context, WINE_ASSET_NAME, filesDir);
                extractArchive(wineArchive, filesDir);
                wineArchive.delete();
                if (wineMarker.exists()) wineMarker.setExecutable(true, false);
            }

            if (needsBox64) {
                if (!box64Bin.exists()) {
                    Log.d(TAG, "Extracting bundled Box64 from assets...");
                    File box64Archive = copyAssetToFile(context, BOX64_ASSET_NAME, filesDir);
                    extractArchive(box64Archive, filesDir);
                    box64Archive.delete();
                    if (box64Bin.exists()) box64Bin.setExecutable(true, false);
                }
            } else {
                // Native x86_64 device: Box64 isn't needed, remove any
                // leftover binary from a previous install on different hardware.
                if (box64Bin.exists()) box64Bin.delete();
            }

            Log.d(TAG, "Bundled engine extraction complete.");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Bundled engine extraction failed", e);
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
