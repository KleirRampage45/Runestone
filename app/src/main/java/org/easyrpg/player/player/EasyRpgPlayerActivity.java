package org.easyrpg.player.player;

import android.content.Intent;
import android.content.Context;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

/**
 * Minimal JNI surface for libeasyrpg_android.so (APK v0.8.1).
 *
 * Provides the static methods that the native code calls via JNI.
 * Extends mkxp-z MainActivity for SDL2/OpenGL/gamepad infrastructure.
 */
public class EasyRpgPlayerActivity extends com.hatkid.mkxpz.MainActivity {

    private static final String TAG = "EasyRPGWrapper";
    private static final String EXTRA_PROJECT_PATH = "project_path";

    private String mProjectPath;
    private String mConfigPath;

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
            return new String[] {
                "--project-path", mProjectPath,
                "--config-path", mConfigPath,
                "--save-path", mConfigPath + "/saves"
            };
        }
        return new String[0];
    }

    // ═══════════════════════════════════════════════
    // JNI callbacks — called by native code
    // ═══════════════════════════════════════════════

    /**
     * Called by native RTP file finder to locate RPG Maker 2000/2003 RTP.
     * Returns empty string — most games include RTP resources bundled.
     */
    public String getRtpPath() {
        Log.d(TAG, "getRtpPath — RTP not bundled, returning empty");
        return "";
    }

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

        // Read project path from intent
        mProjectPath = intent.getStringExtra(EXTRA_PROJECT_PATH);
        if (mProjectPath == null || mProjectPath.isEmpty()) {
            mProjectPath = intent.getStringExtra("game_path");
        }
        if (mProjectPath == null || mProjectPath.isEmpty()) {
            mProjectPath = android.os.Environment.getExternalStorageDirectory() + "/easyrpg-games";
        }

        // Set config path to app's private data dir (writable)
        mConfigPath = getFilesDir().getAbsolutePath() + "/easyrpg";
        new java.io.File(mConfigPath).mkdirs();
        Log.i(TAG, "Config path: " + mConfigPath);

        // Forward GAME_PATH + LAYOUT_MODE to mkxp-z parent
        intent.putExtra("com.grimmobile.runner.extra.GAME_PATH", mProjectPath);
        // NOTE: LAYOUT_MODE, TOUCH_* and HAPTICS_* are passed from GameActivity.kt

        super.onCreate(savedInstanceState);

        // Replace the parent's KBD button with one that properly sets up
        // SDL text input (mTextEdit visible + focused) before showing IME.
        replaceKbdButton();
    }

    /**
     * Find the parent's KBD floating button and replace its onClickListener
     * to call showTextInput() instead of just toggling the IME.
     */
    private void replaceKbdButton() {
        if (mLayout == null) {
            Log.w(TAG, "mLayout null, cannot replace KBD button");
            return;
        }
        try {
            // Find the KBD/⌨ floating button and replace its behavior
            for (int i = 0; i < mLayout.getChildCount(); i++) {
                View child = mLayout.getChildAt(i);
                if (child instanceof android.widget.TextView) {
                    android.widget.TextView tv = (android.widget.TextView) child;
                    String text = tv.getText().toString();
                    if ("KBD".equals(text) || "\u2328".equals(text)) {
                        tv.setText("\u2328");
                        tv.setOnClickListener(v -> showSystemKeyboard());
                        Log.i(TAG, "KBD button replaced with showTextInput");
                        return;
                    }
                }
            }
            Log.w(TAG, "KBD button not found in layout");
        } catch (Exception e) {
            Log.e(TAG, "Error replacing KBD button: " + e.getMessage());
        }
    }

    /**
     * Show the soft keyboard with proper SDL text input setup.
     * Uses SDL's ShowTextInputTask which creates mTextEdit (DummyEdit),
     * makes it visible, requests focus, then shows the IME.
     */
    public void showSystemKeyboard() {
        org.libsdl.app.SDLActivity.showTextInput(0, 0, 1, 1);
    }
}
