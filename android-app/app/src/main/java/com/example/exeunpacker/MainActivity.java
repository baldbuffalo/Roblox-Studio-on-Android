package com.example.exeunpacker;

import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Button launchButton = new Button(this);
        launchButton.setText("Launch Engine Sandbox Pipeline");
        setContentView(launchButton);

        launchButton.setOnClickListener(v -> {
            Toast.makeText(this, "Syncing execution engine rules...", Toast.LENGTH_SHORT).show();
            
            AutoUpdater.checkForUpdates(this, new AutoUpdater.UpdateCallback() {
                @Override
                public void onUpdateRequired(String box64Url, String wineUrl) {
                    boolean success = EngineManager.downloadAndInstall(MainActivity.this, box64Url, wineUrl, deviceNeedsBox64());
                    if (success) {
                        runOnUiThread(() -> runWindowsExecutable());
                    } else {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Engine assets mismatch error.", Toast.LENGTH_LONG).show());
                    }
                }

                @Override
                public void onNoUpdateRequired() {
                    runOnUiThread(() -> runWindowsExecutable());
                }
            });
        });
    }

    // Name of the installer file as placed in android-app/app/src/main/assets/.
    // Rename this constant (or the file) if you use a different filename.
    private static final String INSTALLER_ASSET_NAME = "RobloxStudioInstaller.exe";

    /**
     * The installer is bundled inside the APK under assets/, which is a
     * read-only, non-executable-path zip entry at runtime. It must be
     * copied out to internal storage (getFilesDir()) before Wine/Box64
     * can read and execute it. This copy only needs to happen once.
     */
    private String extractInstallerIfNeeded() throws IOException {
        File outFile = new File(getFilesDir(), INSTALLER_ASSET_NAME);
        if (!outFile.exists()) {
            try (InputStream in = getAssets().open(INSTALLER_ASSET_NAME);
                 OutputStream out = new FileOutputStream(outFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
        }
        return outFile.getAbsolutePath();
    }

    /**
     * arm64-v8a devices (the overwhelming majority of Android hardware) can't
     * run the x86_64 Wine build natively, so they need Box64's dynamic
     * recompiler in front of it. Native x86_64 devices (rare — some Intel
     * tablets/Chromebooks) can run the same Wine build directly with no
     * translation layer, so Box64 should be skipped there entirely.
     */
    private boolean deviceNeedsBox64() {
        for (String abi : Build.SUPPORTED_ABIS) {
            if (abi.equals("x86_64")) {
                return false;
            }
        }
        return true; // arm64-v8a or anything else: assume translation is needed
    }

    private void runWindowsExecutable() {
        try {
            String baseDir = getFilesDir().getAbsolutePath();

            String targetExePath = extractInstallerIfNeeded();
            String wineBin = baseDir + "/wine/bin/wine64";

            String execChain = deviceNeedsBox64()
                ? baseDir + "/box64 " + wineBin + " " + targetExePath
                : wineBin + " " + targetExePath;

            ProcessBuilder pb = new ProcessBuilder("/system/bin/sh", "-c", execChain);

            pb.environment().put("BOX64_DYNAREC", "1");
            pb.environment().put("HOME", baseDir); 
            pb.environment().put("WINEDEBUG", "-all");

            pb.start();
            Toast.makeText(this, "EXE Launched Headlessly via Wine/Box64 Engine Integration!", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Launcher pipeline execution failure.", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }
}
