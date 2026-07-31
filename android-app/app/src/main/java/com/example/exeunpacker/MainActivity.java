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
        
        // Simple UI container logic wrapper
        Button launchButton = new Button(this);
        launchButton.setText("Launch Windows Application Engine");
        setContentView(launchButton);

        launchButton.setOnClickListener(v -> {
            Toast.makeText(this, "Checking engine system updates...", Toast.LENGTH_SHORT).show();
            
            AutoUpdater.checkForUpdates(this, new AutoUpdater.UpdateCallback() {
                @Override
                public void onUpdateRequired(String box64Url, String wineUrl) {
                    boolean success = EngineManager.downloadAndInstall(MainActivity.this, box64Url, wineUrl);
                    if (success) {
                        runOnUiThread(() -> runWindowsExecutable());
                    } else {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Engine updates failed.", Toast.LENGTH_LONG).show());
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
            
            // Hardcode your application target file location path here
            String targetExePath = "/sdcard/Download/YourAppFolder/program.exe";

            // Binds Box64, Wine64, and your target program executable systematically into a single call line
            String execChain = baseDir + "/box64 " + baseDir + "/wine/bin/wine64 " + targetExePath;

            ProcessBuilder pb = new ProcessBuilder("/system/bin/sh", "-c", execChain);
            
            // Passes necessary optimizations straight down into the translation kernel core components
            pb.environment().put("BOX64_DYNAREC", "1");
            pb.environment().put("HOME", baseDir); 
            pb.environment().put("WINEDEBUG", "-all");

            pb.start();
            Toast.makeText(this, "EXE Engine Launched Headlessly!", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Failed to launch Windows binary executable target.", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }
}
