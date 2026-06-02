/*
 * Runestone - Multi-engine RPG Maker and visual novel launcher for Android
 * Copyright (C) 2026 Runestone Contributors
 *
 * Minimal Ren'Py Android activity adapted from Ren'Py 8.3.4 RAPT.
 */

package org.renpy.android;

import org.libsdl.app.SDLActivity;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * SDL activity used by the bundled librenpython.so.
 *
 * The native library invokes preparePython() from SDL_main and exports
 * nativeSetEnv() for this exact package and class name.
 */
public class PythonSDLActivity extends SDLActivity {

    private static final String TAG = "PythonSDLActivity";
    private static final String RUNTIME_ASSET = "renpy-runtime.zip";
    private static final String RUNTIME_VERSION = "8.3.4-runestone-1";

    public static PythonSDLActivity mActivity;

    private FrameLayout mFrameLayout;
    private boolean mStopDone = true;
    private PowerManager.WakeLock wakeLock;

    @Override
    protected String[] getLibraries() {
        return new String[] { "SDL2", "renpython" };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mActivity = this;
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mHasMultiWindow) {
            resumeNativeThread();
        }
    }

    @Override
    public void setContentView(View view) {
        mFrameLayout = new FrameLayout(this);
        mFrameLayout.setBackgroundColor(Color.BLACK);
        mFrameLayout.addView(view);
        super.setContentView(mFrameLayout);
    }

    @Override
    public void setOrientationBis(int width, int height, boolean resizable, String hint) {
        // Respect the activity orientation from the manifest.
    }

    public native void nativeSetEnv(String variable, String value);

    /**
     * Called on SDL's native thread before Python starts.
     */
    public void preparePython() {
        try {
            File runtimeDir = new File(getFilesDir(), "renpy-runtime");
            extractRuntimeIfNeeded(runtimeDir);

            String gamePath = getIntent().getStringExtra("game_path");
            if (gamePath == null || !new File(gamePath).isDirectory()) {
                throw new IOException("Ren'Py game directory is missing");
            }

            String savePath = getIntent().getStringExtra("save_path");
            File saveDir = savePath == null ? new File(gamePath, "saves") : new File(savePath);
            if (!saveDir.exists() && !saveDir.mkdirs()) {
                throw new IOException("Could not create Ren'Py save directory");
            }

            File publicDir = saveDir.getParentFile();
            if (publicDir == null) {
                publicDir = saveDir;
            }

            nativeSetEnv("ANDROID_PRIVATE", runtimeDir.getAbsolutePath());
            nativeSetEnv("ANDROID_PUBLIC", publicDir.getAbsolutePath());
            nativeSetEnv("ANDROID_OLD_PUBLIC", publicDir.getAbsolutePath());
            nativeSetEnv("ANDROID_APK", getApkPath());
            nativeSetEnv("RENPY_GAME_DIR", new File(gamePath).getAbsolutePath());

            Log.i(TAG, "Ren'Py runtime prepared for " + gamePath);
        } catch (Exception e) {
            Log.e(TAG, "Could not prepare Ren'Py runtime", e);
            showError("Could not start Ren'Py: " + e.getMessage());
            throw new IllegalStateException(e);
        }
    }

    private void extractRuntimeIfNeeded(File runtimeDir) throws IOException {
        File versionFile = new File(runtimeDir, "runtime.version");
        if (versionFile.isFile() && RUNTIME_VERSION.equals(readText(versionFile))) {
            return;
        }

        deleteRecursively(runtimeDir);
        if (!runtimeDir.mkdirs() && !runtimeDir.isDirectory()) {
            throw new IOException("Could not create Ren'Py runtime directory");
        }

        String rootPath = runtimeDir.getCanonicalPath() + File.separator;
        try (
            InputStream assetStream = getAssets().open(RUNTIME_ASSET);
            ZipInputStream zip = new ZipInputStream(new BufferedInputStream(assetStream))
        ) {
            ZipEntry entry;
            byte[] buffer = new byte[64 * 1024];
            while ((entry = zip.getNextEntry()) != null) {
                File outputFile = new File(runtimeDir, entry.getName());
                String outputPath = outputFile.getCanonicalPath();
                if (!outputPath.startsWith(rootPath)) {
                    throw new IOException("Invalid runtime zip entry: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    if (!outputFile.mkdirs() && !outputFile.isDirectory()) {
                        throw new IOException("Could not create " + outputFile);
                    }
                    continue;
                }

                File parent = outputFile.getParentFile();
                if (parent != null && !parent.mkdirs() && !parent.isDirectory()) {
                    throw new IOException("Could not create " + parent);
                }

                try (OutputStream output = new BufferedOutputStream(new FileOutputStream(outputFile))) {
                    int count;
                    while ((count = zip.read(buffer)) != -1) {
                        output.write(buffer, 0, count);
                    }
                }
            }
        }

        writeText(versionFile, RUNTIME_VERSION);
    }

    private String getApkPath() {
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(getPackageName(), 0);
            return info.sourceDir;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    private static String readText(File file) throws IOException {
        byte[] data = new byte[(int) file.length()];
        try (InputStream input = new FileInputStream(file)) {
            int count = input.read(data);
            return new String(data, 0, Math.max(count, 0));
        }
    }

    private static void writeText(File file, String value) throws IOException {
        try (OutputStream output = new FileOutputStream(file)) {
            output.write(value.getBytes());
        }
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    public static boolean isChromebook() {
        return mActivity != null
            && mActivity.getPackageManager().hasSystemFeature("org.chromium.arc.device_management");
    }

    public static void hidePresplash() {
        // Runestone does not use Ren'Py's presplash overlay.
    }

    public void armOnStop() {
        mStopDone = false;
    }

    public void finishOnStop() {
        synchronized (this) {
            mStopDone = true;
            notifyAll();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        long deadline = System.currentTimeMillis() + 8000;
        synchronized (this) {
            while (!mStopDone && System.currentTimeMillis() < deadline) {
                try {
                    wait(100);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    public void openUrl(String url) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    public void vibrate(double seconds) {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null) {
            return;
        }

        long duration = (long) (seconds * 1000);
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(duration);
        }
    }

    public int getDPI() {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        return metrics.densityDpi;
    }

    public void setWakeLock(boolean active) {
        if (wakeLock == null) {
            PowerManager manager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = manager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "Runestone:RenPy");
            wakeLock.setReferenceCounted(false);
        }

        if (active && !wakeLock.isHeld()) {
            wakeLock.acquire();
        } else if (!active && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    private void showError(final String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }
}
