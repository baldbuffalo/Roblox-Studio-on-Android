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

    // Preferred X server port (maps to DISPLAY :0). If unavailable, nearby
    // ports are tried instead -- see findAvailablePort().
    private static final int PREFERRED_X_SERVER_PORT = 6000;
    private static final int PORT_SEARCH_RANGE = 10; // tries 6000..6009

    private XServer xServer;
    private int xServerPort;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);

        xServerPort = findAvailablePort(PREFERRED_X_SERVER_PORT, PORT_SEARCH_RANGE);
        String displayEnv = "127.0.0.1:" + (xServerPort - PREFERRED_X_SERVER_PORT);

        // Embedded X11 server view -- this is what Wine will actually draw
        // into. Without this, Wine has no display and runs invisibly.
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
                boolean success = EngineManager.extractWineSupportIfNeeded(MainActivity.this);
                if (success) {
                    runOnUiThread(() -> runWindowsExecutable(displayEnv));
                } else {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Engine asset extraction failed.", Toast.LENGTH_LONG).show());
                }
            }).start();
        });
    }

    /**
     * X11 ports are conventionally 6000 + display number. If the preferred
     * port is already bound (another X server, a stale process, etc.), try
     * the next few ports instead of failing outright -- same convention
     * real X installs use when running multiple displays (:0, :1, :2...).
     */
    private int findAvailablePort(int startPort, int range) {
        for (int port = startPort; port < startPort + range; port++) {
            try (java.net.ServerSocket socket = new java.net.ServerSocket()) {
                socket.setReuseAddress(true);
                socket.bind(new java.net.InetSocketAddress("127.0.0.1", port));
                return port; // successfully bound and immediately released -> available
            } catch (IOException e) {
                // Port in use, try the next one.
            }
        }
        // Nothing free in range; fall back to the preferred port and let
        // XServer.start() itself report the failure if it's still taken.
        return startPort;
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
     * recompiler in front of it. Native x86_64 devices (rare -- some Intel
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

    private void runWindowsExecutable(String displayEnv) {
        try {
            String baseDir = getFilesDir().getAbsolutePath();
            String supportDir = baseDir + "/wine-support";

            String targetExePath = extractInstallerIfNeeded();

            // box64/wine64 exec from nativeLibraryDir (installer-extracted,
            // actually executable on API 29+) -- NOT from filesDir, which is
            // subject to Android's W^X restriction on app-written files.
            String wineBin = EngineManager.getWinePath(this);
            String box64Bin = EngineManager.getBox64Path(this);

            String execChain = deviceNeedsBox64()
                ? box64Bin + " " + wineBin + " " + targetExePath
                : wineBin + " " + targetExePath;

            ProcessBuilder pb = new ProcessBuilder("/system/bin/sh", "-c", execChain);

            pb.environment().put("BOX64_DYNAREC", "1");
            pb.environment().put("HOME", baseDir);
            pb.environment().put("WINEDEBUG", "-all");
            // Point Wine at the embedded X server running inside this app.
            pb.environment().put("DISPLAY", displayEnv);
            // Wine's own lib/share support tree, extracted from assets as
            // plain data (not exec'd directly, so no W^X issue there).
            pb.environment().put("WINEPREFIX", baseDir + "/wineprefix");
            pb.environment().put("LD_LIBRARY_PATH", supportDir + "/lib:" + supportDir + "/lib64");
            pb.environment().put("WINESERVER", supportDir + "/bin/wineserver");
            pb.environment().put("WINELOADER", wineBin);
            pb.environment().put("WINEDLLPATH", supportDir + "/lib/wine:" + supportDir + "/lib64/wine");

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
