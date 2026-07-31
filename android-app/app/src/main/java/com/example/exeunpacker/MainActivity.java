package com.example.exeunpacker;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.io.IOException;

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
                    boolean success = EngineManager.downloadAndInstall(MainActivity.this, box64Url, wineUrl);
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

    private void runWindowsExecutable() {
        try {
            String baseDir = getFilesDir().getAbsolutePath();
            
            // Customize this path string target rule to point exactly to your app executable file location
            String targetExePath = "/sdcard/Download/YourAppFolder/program.exe";

            String execChain = baseDir + "/box64 " + baseDir + "/wine/bin/wine64 " + targetExePath;

            ProcessBuilder pb = new ProcessBuilder("/system/bin/sh", "-c", execChain);
            
            pb.environment().put("BOX64_DYNAREC", "1");
            pb.environment().put("HOME", baseDir); 
            pb.environment().put("WINEDEBUG", "-all");

            pb.start();
            Toast.makeText(this, "EXE Launched Headlessly via Box64 Engine Integration!", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Launcher pipeline execution failure.", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }
}
