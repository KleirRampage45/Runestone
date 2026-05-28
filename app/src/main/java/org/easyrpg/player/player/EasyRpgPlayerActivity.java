package org.easyrpg.player.player;

import android.content.Intent;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.util.Log;

/**
 * Minimal JNI surface for libeasyrpg_android.so (APK v0.8.1).
 *
 * Provides the static methods that the native code calls via JNI.
 * Extends mkxp-z MainActivity for SDL2/OpenGL/gamepad infrastructure.
 */
public class EasyRpgPlayerActivity extends com.hatkid.mkxpz.MainActivity {

    private static final String TAG = "EasyRPGWrapper";
    private static final String EXTRA_PROJECT_PATH = "project_path";
    private static final String EXTRA_COMMAND_LINE = "command_line";

    private String mProjectPath;

    // ═══════════════════════════════════════════════
    // Static JNI methods — called by native code
    // ═══════════════════════════════════════════════

    /**
     * Called by native ApkFilesystem to read APK assets (RTP, fonts, etc.).
     * Must be static — the native code calls it as CallStaticObjectMethod.
     */
    public static AssetManager getAssetManager() {
        return getContext().getAssets();
    }

    /**
     * Called by native SafFilesystem for SAF-based file access.
     * Only needed if games are accessed via SAF URIs — returns null
     * which will cause the native code to fall back to regular filesystem.
     */
    public static Object getHandleForPath(String path) {
        Log.w(TAG, "getHandleForPath — SAF not supported, using regular filesystem");
        return null;
    }

    // ═══════════════════════════════════════════════
    // SDL overrides
    // ═══════════════════════════════════════════════

    @Override
    protected String[] getLibraries() {
        return new String[] {
            "SDL2",
            "easyrpg_android"
        };
    }

    @Override
    protected String[] getArguments() {
        if (mProjectPath != null) {
            return new String[] { "--project-path", mProjectPath };
        }
        return new String[0];
    }

    // ═══════════════════════════════════════════════
    // JNI callbacks
    // ═══════════════════════════════════════════════

    public void openSettings() { Log.d(TAG, "openSettings"); }

    public void endGame() {
        Log.i(TAG, "endGame");
        finish();
    }

    public void resetGame() {
        Log.i(TAG, "resetGame — restarting");
        Intent intent = getIntent();
        finish();
        startActivity(intent);
    }

    public void toggleFps() { Log.d(TAG, "toggleFps"); }

    // ═══════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Intent intent = getIntent();

        mProjectPath = intent.getStringExtra(EXTRA_PROJECT_PATH);
        if (mProjectPath == null || mProjectPath.isEmpty()) {
            mProjectPath = intent.getStringExtra("game_path");
        }
        if (mProjectPath == null || mProjectPath.isEmpty()) {
            mProjectPath = android.os.Environment.getExternalStorageDirectory() + "/easyrpg-games";
        }

        Log.i(TAG, "Project path: " + mProjectPath);

        intent.putExtra("com.grimmobile.runner.extra.GAME_PATH", mProjectPath);
        super.onCreate(savedInstanceState);
    }
}
