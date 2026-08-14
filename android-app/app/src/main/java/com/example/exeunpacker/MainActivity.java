package com.example.exeunpacker;

import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import au.com.darkside.xserver.XServer;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {

    private static final int PREFERRED_X_SERVER_PORT = 6000;
    private static final int PORT_SEARCH_RANGE = 10;

    private XServer xServer;
    private int xServerPort;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);

        xServerPort = findAvailablePort(PREFERRED_X_SERVER_PORT, PORT_SEARCH_RANGE);
        String displayEnv = "127.0.0.1:" + (xServerPort - PREFERRED_X_SERVER_PORT);

        xServer = new XServer(this, xServerPort, null);
        root.addView(xServer.getScreen(), new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));

        Button launchButton = new Button(this);
        launchButton.setText("Launch Engine Sandbox Pipeline");
        root.addView(launchButton);

        setContentView(root);

        xServer.start();

        launchButton.setOnClickListener(v -> {
            Toast.makeText(this, "Preparing bundled engine assets...", Toast.LENGTH_SHORT).show();

            new Thread(() -> {
                boolean wineOk = EngineManager.extractWineSupportIfNeeded(MainActivity.this);
                boolean glibcOk = EngineManager.extractGlibcIfNeeded(MainActivity.this);
                if (wineOk && glibcOk) {
                    runOnUiThread(() -> runWindowsExecutable(displayEnv));
                } else {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Engine asset extraction failed.", Toast.LENGTH_LONG).show());
                }
            }).start();
        });
    }

    private int findAvailablePort(int startPort, int range) {
        for (int port = startPort; port < startPort + range; port++) {
            try (java.net.ServerSocket socket = new java.net.ServerSocket()) {
                socket.setReuseAddress(true);
                socket.bind(new java.net.InetSocketAddress("127.0.0.1", port));
                return port;
            } catch (IOException e) {
                // Port in use, try the next one.
            }
        }
        return startPort;
    }

    private static final String INSTALLER_ASSET_NAME = "RobloxStudioInstaller.exe";

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

    private boolean deviceNeedsBox64() {
        for (String abi : Build.SUPPORTED_ABIS) {
            if (abi.equals("x86_64")) {
                return false;
            }
        }
        return true;
    }

    private void runWindowsExecutable(String displayEnv) {
        try {
            String baseDir = getFilesDir().getAbsolutePath();
            String supportDir = baseDir + "/wine-support";

            String targetExePath = extractInstallerIfNeeded();

            String wineBin = EngineManager.getWinePath(this);
            String box64Bin = EngineManager.getBox64Path(this);

            ProcessBuilder pb = deviceNeedsBox64()
                ? new ProcessBuilder(box64Bin, wineBin, targetExePath)
                : new ProcessBuilder(wineBin, targetExePath);

            String glibcDir = baseDir + "/glibc-lib";

            pb.environment().put("BOX64_DYNAREC", "1");
            pb.environment().put("HOME", baseDir);
            pb.environment().put("WINEDEBUG", "-all");
            pb.environment().put("DISPLAY", displayEnv);
            pb.environment().put("WINEPREFIX", baseDir + "/wineprefix");
            pb.environment().put("LD_LIBRARY_PATH", glibcDir + ":" + supportDir + "/lib:" + supportDir + "/lib/wine/x86_64-unix");
            pb.environment().put("WINESERVER", supportDir + "/bin/wineserver");
            pb.environment().put("WINELOADER", wineBin);
            pb.environment().put("WINEDLLPATH", supportDir + "/lib/wine/x86_64-unix");

            Process process = pb.start();

            new Thread(() -> {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        android.util.Log.d("WineOutput", line);
                    }
                } catch (IOException ignored) {}
            }).start();

            new Thread(() -> {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        android.util.Log.e("WineOutput", line);
                    }
                } catch (IOException ignored) {}
            }).start();

            Toast.makeText(this, "EXE Launched via Wine/Box64, drawing to embedded X server!", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Launcher pipeline execution failure.", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }
}
