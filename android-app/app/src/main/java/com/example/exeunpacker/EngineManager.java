package com.example.exeunpacker;

import android.content.Context;
import android.util.Log;
import java.io.*;
import java.net.URL;

public class EngineManager {
    private static final String TAG = "EngineManager";

    /**
     * @param needsBox64 true on arm64-v8a devices, where the x86_64 Wine build
     *                   must be run through Box64's dynamic recompiler. False
     *                   on native x86_64 devices, which can run Wine directly
     *                   with no translation layer, so Box64 is skipped entirely.
     */
    public static boolean downloadAndInstall(Context context, String box64Url, String wineUrl, boolean needsBox64) {
        File filesDir = context.getFilesDir();
        File wineArchive = new File(filesDir, "wine.tar.xz");

        try {
            Log.d(TAG, "Fetching translation assets natively...");
            downloadFile(wineUrl, wineArchive);
            extractArchive(wineArchive, filesDir);
            wineArchive.delete();

            if (needsBox64) {
                File box64Archive = new File(filesDir, "box64.tar.gz");
                downloadFile(box64Url, box64Archive);
                extractArchive(box64Archive, filesDir);
                box64Archive.delete();

                File box64Bin = new File(filesDir, "box64");
                if (box64Bin.exists()) box64Bin.setExecutable(true, false);
            } else {
                // Native x86_64 device: Box64 isn't needed. If a leftover
                // binary exists from a previous install, remove it.
                File box64Bin = new File(filesDir, "box64");
                if (box64Bin.exists()) box64Bin.delete();
            }

            File wineBin = new File(filesDir, "wine/bin/wine64");
            if (wineBin.exists()) wineBin.setExecutable(true, false);

            Log.d(TAG, "Translation deployment engine installation fully successful.");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Asset integration pipeline failed", e);
            return false;
        }
    }

    private static void downloadFile(String urlStr, File targetFile) throws IOException {
        InputStream in = new BufferedInputStream(new URL(urlStr).openStream());
        OutputStream out = new FileOutputStream(targetFile);
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
        }
        out.flush();
        out.close();
        in.close();
    }

    private static void extractArchive(File archive, File destination) throws IOException, InterruptedException {
        String command = "tar -xf " + archive.getAbsolutePath() + " -C " + destination.getAbsolutePath();
        Process process = Runtime.getRuntime().exec(new String[]{"/system/bin/sh", "-c", command});
        process.waitFor();
    }
}
