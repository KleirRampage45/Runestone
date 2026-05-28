package org.easyrpg.player.player;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/**
 * Minimal JNI surface for libeasyrpg_android.so.
 * Uses mkxp-z's SDL2 infrastructure (SDLActivity, gamepad, OpenGL)
 * but loads EasyRPG's native .so instead of mkxp-z.
 */
public class EasyRpgPlayerActivity extends com.hatkid.mkxpz.MainActivity {

    private static final String TAG = "EasyRPGWrapper";
    private String mProjectPath;

    // ═══════════════════════════════════════════════
    // Native library overrides — load EasyRPG, not mkxp-z
    // ═══════════════════════════════════════════════

    @Override
    protected String[] getLibraries() {
        return new String[] {
            "SDL2",
            "easyrpg_android"
        };
    }

    // ═══════════════════════════════════════════════
    // JNI callbacks — called by libeasyrpg_android.so
    // ═══════════════════════════════════════════════

    public void openSettings() {
        Log.d(TAG, "openSettings (no-op)");
    }

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

    public void toggleFps() {
        Log.d(TAG, "toggleFps (no-op)");
    }

    // ═══════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Intent intent = getIntent();
        mProjectPath = intent.getStringExtra("project_path");
        if (mProjectPath == null || mProjectPath.isEmpty()) {
            mProjectPath = android.os.Environment.getExternalStorageDirectory() + "/easyrpg-games";
        }
        Log.i(TAG, "Project path: " + mProjectPath);

        // Inject the project path so MainActivity/SDL picks it up
        intent.putExtra("com.grimmobile.runner.extra.GAME_PATH", mProjectPath);

        super.onCreate(savedInstanceState);
    }

    /** Called by native code via JNI to get the project directory. */
    public String getProjectPath() {
        return mProjectPath;
    }
}
