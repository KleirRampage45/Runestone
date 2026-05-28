package org.easyrpg.player.player;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/**
 * Minimal JNI surface for libeasyrpg_android.so (APK v0.8.1).
 *
 * Extends mkxp-z MainActivity to reuse SDL2/OpenGL/gamepad infrastructure.
 * Loads EasyRPG native .so instead of mkxp-z, and forwards the game path
 * as command-line arguments that EasyRPG's main() expects.
 */
public class EasyRpgPlayerActivity extends com.hatkid.mkxpz.MainActivity {

    private static final String TAG = "EasyRPGWrapper";

    // Intent extra keys — match what the EasyRPG native code expects
    private static final String EXTRA_PROJECT_PATH = "project_path";
    private static final String EXTRA_COMMAND_LINE = "command_line";
    private static final String EXTRA_SAVE_PATH = "save_path";

    private String mProjectPath;
    private String mSavePath;
    private String[] mCommandLine;

    // ═══════════════════════════════════════════════
    // SDL overrides — load EasyRPG, pass correct args
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
        // EasyRPG's main() expects: [project_path, ...]
        // Pass the project path plus any extra command-line args
        if (mCommandLine != null && mCommandLine.length > 0) {
            return mCommandLine;
        }
        if (mProjectPath != null) {
            return new String[] { mProjectPath };
        }
        return new String[0];
    }

    // ═══════════════════════════════════════════════
    // JNI callbacks — required by libeasyrpg_android.so
    // ═══════════════════════════════════════════════

    /** Called by native code via JNI. Must be public, no args, void return. */
    public void openSettings() {
        Log.d(TAG, "openSettings (no-op)");
    }

    /** Called by native code via JNI. */
    public void endGame() {
        Log.i(TAG, "endGame");
        finish();
    }

    /** Called by native code via JNI. */
    public void resetGame() {
        Log.i(TAG, "resetGame — restarting");
        Intent intent = getIntent();
        finish();
        startActivity(intent);
    }

    /** Called by native code via JNI. */
    public void toggleFps() {
        Log.d(TAG, "toggleFps (no-op)");
    }

    // ═══════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Intent intent = getIntent();

        // Read project path from intent extras
        mProjectPath = intent.getStringExtra(EXTRA_PROJECT_PATH);
        if (mProjectPath == null || mProjectPath.isEmpty()) {
            mProjectPath = intent.getStringExtra("game_path");
        }
        if (mProjectPath == null || mProjectPath.isEmpty()) {
            mProjectPath = android.os.Environment.getExternalStorageDirectory() + "/easyrpg-games";
        }

        mSavePath = intent.getStringExtra(EXTRA_SAVE_PATH);
        mCommandLine = intent.getStringArrayExtra(EXTRA_COMMAND_LINE);

        Log.i(TAG, "Project path: " + mProjectPath);
        Log.i(TAG, "Save path: " + mSavePath);

        // Inject the project path so MainActivity/SDL picks it up
        intent.putExtra("com.grimmobile.runner.extra.GAME_PATH", mProjectPath);

        super.onCreate(savedInstanceState);
    }
}
