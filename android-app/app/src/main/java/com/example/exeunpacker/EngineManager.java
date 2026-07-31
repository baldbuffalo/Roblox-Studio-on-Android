package com.example.exeunpacker;

import android.content.Context;
import android.util.Log;
import java.io.*;
import java.net.URL;

public class EngineManager {
    private static final String TAG = "EngineManager";

    public static boolean downloadAndInstall(Context context, String box64Url, String wineUrl) {
        File filesDir = context.getFilesDir();
        File box64Archive = new File(filesDir, "box64.tar.gz");
        File wineArchive = new File(filesDir, "wine.tar.xz");

        try {
            Log.d(TAG, "Downloading translation assets natively...");
            downloadFile(box64Url, box64Archive);
            downloadFile(wineUrl, wineArchive);

            Log.d(TAG, "Extracting components into secure app environment path...");
            extractArchive(box64Archive, filesDir);
            extractArchive(wineArchive, filesDir);

            // Clean up compressed archives to instantly free device storage memory
            box64Archive.delete();
            wineArchive.delete();

            // CRITICAL STEP: Give Linux executing permissions so Android OS allows them to function as an engine
            File box64Bin = new File(filesDir, "box64");
            if (box64Bin.exists()) box64Bin.setExecutable(true, true);

            File wineBin = new File(filesDir, "wine/bin/wine64");
            if (wineBin.exists()) wineBin.setExecutable(true, true);

            Log.d(TAG, "Translation deployment engine installation fully successful.");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Installation failure pipeline crashed", e);
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
        // Uses Android's native internal Linux shell architecture to extract tar safely and quickly
        String command = "tar -xf " + archive.getAbsolutePath() + " -C " + destination.getAbsolutePath();
        Process process = Runtime.getRuntime().exec(new String[]{"/system/bin/sh", "-c", command});
        process.waitFor();
    }
}
