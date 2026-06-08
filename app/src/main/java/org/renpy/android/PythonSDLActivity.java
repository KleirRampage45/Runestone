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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
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

    private RenpySideControlsView controlsView;
    private boolean mStopDone = true;
    private PowerManager.WakeLock wakeLock;

    @Override
    protected String[] getLibraries() {
        return new String[] { "SDL2", "renpython" };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mActivity = this;
        Log.i(TAG, "onCreate game_path=" + getIntent().getStringExtra("game_path"));
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams attrs = getWindow().getAttributes();
            attrs.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(attrs);
        }
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mHasMultiWindow) {
            resumeNativeThread();
        }
        attachControlsWhenReady();
    }

    @Override
    public void setContentView(View view) {
        Log.i(TAG, "setContentView SDL view=" + view.getClass().getName());
        super.setContentView(view);
        attachControlsWhenReady();
    }

    private void attachControlsWhenReady() {
        if (controlsView != null || mLayout == null) {
            return;
        }
        mLayout.post(() -> {
            if (controlsView != null || mLayout == null) {
                return;
            }
            controlsView = new RenpySideControlsView(this);
            mLayout.addView(
                controlsView,
                new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            );
            Log.i(TAG, "Ren'Py side controls attached to SDL layout");
        });
    }

    private void sendKey(int keyCode, boolean pressed) {
        if (pressed) {
            SDLActivity.onNativeKeyDown(keyCode);
        } else {
            SDLActivity.onNativeKeyUp(keyCode);
        }
    }

    private void dispatchMouse(int action, float x, float y, boolean pressed) {
        if (mSurface == null) {
            return;
        }
        long now = System.currentTimeMillis();
        MotionEvent.PointerProperties properties = new MotionEvent.PointerProperties();
        properties.id = 0;
        properties.toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        coords.x = x;
        coords.y = y;
        coords.pressure = pressed ? 1f : 0f;
        coords.size = 1f;
        MotionEvent event = MotionEvent.obtain(
            now,
            now,
            action,
            1,
            new MotionEvent.PointerProperties[] { properties },
            new MotionEvent.PointerCoords[] { coords },
            0,
            pressed ? MotionEvent.BUTTON_PRIMARY : 0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_MOUSE,
            0
        );
        if (action == MotionEvent.ACTION_HOVER_MOVE ||
            action == MotionEvent.ACTION_BUTTON_PRESS ||
            action == MotionEvent.ACTION_BUTTON_RELEASE) {
            mSurface.dispatchGenericMotionEvent(event);
        } else {
            mSurface.dispatchTouchEvent(event);
        }
        event.recycle();
    }

    private final class RenpySideControlsView extends View {
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF dpadBounds = new RectF();
        private final RectF handleRect = new RectF();
        private final RectF settingsRect = new RectF();
        private final Handler handler = new Handler(Looper.getMainLooper());
        private int activeKey = KeyEvent.KEYCODE_UNKNOWN;
        private boolean settingsOpen = false;
        private boolean dpadDragMode = false;
        private boolean draggingDpad = false;
        private boolean trackpadActive = false;
        private boolean trackpadMoved = false;
        private boolean cursorVisible = false;
        private boolean mouseEnabled = true;
        private int cursorColor = Color.rgb(238, 226, 190);
        private float dpadCx = 0f;
        private float dpadCy = 0f;
        private float dpadRadius = 46f;
        private float dpadOpacity = 0.70f;
        private float mouseOpacity = 0.85f;
        private float cursorScale = 1.0f;
        private float pointerSensitivity = 1.25f;
        private float pointerAcceleration = 0.35f;
        private float pointerSmoothing = 0.25f;
        private float cursorX = 0f;
        private float cursorY = 0f;
        private float targetCursorX = 0f;
        private float targetCursorY = 0f;
        private float downX = 0f;
        private float downY = 0f;
        private float lastX = 0f;
        private float lastY = 0f;
        private float clickPulse = 0f;
        private long lastGameTapAt = 0L;
        private final Runnable hideCursor = () -> {
            cursorVisible = false;
            invalidate();
        };

        RenpySideControlsView(Context context) {
            super(context);
            setWillNotDraw(false);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setFakeBoldText(true);
            textPaint.setTextSize(dp(12f));
            loadControlPrefs();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (dpadCx <= 0f || dpadCy <= 0f) {
                dpadCx = dp(72f);
                dpadCy = h * 0.52f;
            }
            if (cursorX <= 0f || cursorY <= 0f) {
                cursorX = w * 0.5f;
                cursorY = h * 0.5f;
                targetCursorX = cursorX;
                targetCursorY = cursorY;
            }
            layoutRects();
        }

        private void layoutRects() {
            dpadRadius = clamp(dpadRadius, dp(30f), dp(76f));
            dpadCx = clamp(dpadCx, dpadRadius + dp(8f), getWidth() - dpadRadius - dp(8f));
            dpadCy = clamp(dpadCy, dpadRadius + dp(8f), getHeight() - dpadRadius - dp(8f));
            dpadBounds.set(dpadCx - dpadRadius, dpadCy - dpadRadius, dpadCx + dpadRadius, dpadCy + dpadRadius);
            float handleW = dp(48f);
            float handleH = dp(28f);
            handleRect.set(getWidth() / 2f - handleW / 2f, dp(8f), getWidth() / 2f + handleW / 2f, dp(8f) + handleH);
            float settingsW = Math.min(getWidth() - dp(24f), dp(620f));
            settingsRect.set(getWidth() / 2f - settingsW / 2f, dp(36f), getWidth() / 2f + settingsW / 2f, dp(124f));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            drawDpad(canvas);
            drawHandle(canvas);
            if (settingsOpen) drawSettings(canvas);
            if (cursorVisible && mouseEnabled) drawCursor(canvas);
        }

        private void drawHandle(Canvas canvas) {
            Path caret = new Path();
            float cx = handleRect.centerX();
            float cy = handleRect.centerY();
            float s = dp(7f);
            if (settingsOpen) {
                caret.moveTo(cx - s, cy + s / 2f); caret.lineTo(cx, cy - s / 2f); caret.lineTo(cx + s, cy + s / 2f);
            } else {
                caret.moveTo(cx - s, cy - s / 2f); caret.lineTo(cx, cy + s / 2f); caret.lineTo(cx + s, cy - s / 2f);
            }
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(2f));
            strokePaint.setColor(Color.argb(210, 238, 226, 190));
            canvas.drawPath(caret, strokePaint);
        }

        private void drawSettings(Canvas canvas) {
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(Color.argb(175, 8, 8, 10));
            canvas.drawRoundRect(settingsRect, dp(12f), dp(12f), fillPaint);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(1f));
            strokePaint.setColor(Color.argb(90, 200, 170, 130));
            canvas.drawRoundRect(settingsRect, dp(12f), dp(12f), strokePaint);
            float cellW = settingsRect.width() / 10f;
            for (int i = 0; i < 10; i++) {
                float left = settingsRect.left + cellW * i + dp(4f);
                float top = settingsRect.top + dp(8f);
                float right = settingsRect.left + cellW * (i + 1) - dp(4f);
                float bottom = settingsRect.bottom - dp(8f);
                boolean active = (i == 3 && dpadDragMode) ||
                    (i == 4 && mouseEnabled) ||
                    (i == 8 && pointerAcceleration > 0.05f) ||
                    (i == 9 && pointerSmoothing > 0.05f);
                if (active) {
                    fillPaint.setStyle(Paint.Style.FILL);
                    fillPaint.setColor(Color.argb(45, 210, 180, 135));
                    canvas.drawRoundRect(left, top, right, bottom, dp(8f), dp(8f), fillPaint);
                }
                float cx = (left + right) / 2f;
                float iconCy = top + (bottom - top) * 0.38f;
                drawSettingIcon(canvas, i, cx, iconCy, Math.min(right - left, bottom - top) * 0.23f);
                textPaint.setTextSize(dp(8.2f));
                textPaint.setFakeBoldText(true);
                textPaint.setColor(Color.argb(205, 235, 232, 220));
                canvas.drawText(settingLabel(i), cx, bottom - dp(7f), textPaint);
            }
        }

        private String settingLabel(int index) {
            switch (index) {
                case 0: return "HOME";
                case 1: return "D-";
                case 2: return "D+";
                case 3: return "MOVE";
                case 4: return "MOUSE";
                case 5: return "CURSOR";
                case 6: return "D OP";
                case 7: return "M OP";
                case 8: return "ACCEL";
                case 9: return "SMOOTH";
                default: return "";
            }
        }

        private void drawSettingIcon(Canvas canvas, int index, float cx, float cy, float s) {
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(2f));
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
            strokePaint.setStrokeJoin(Paint.Join.ROUND);
            strokePaint.setColor(Color.argb(215, 235, 232, 220));
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(Color.argb(215, 235, 232, 220));
            Path path = new Path();
            switch (index) {
                case 0:
                    path.moveTo(cx - s, cy); path.lineTo(cx, cy - s); path.lineTo(cx + s, cy); canvas.drawPath(path, strokePaint);
                    canvas.drawLine(cx - s * 0.62f, cy, cx - s * 0.62f, cy + s, strokePaint);
                    canvas.drawLine(cx + s * 0.62f, cy, cx + s * 0.62f, cy + s, strokePaint);
                    canvas.drawLine(cx - s * 0.62f, cy + s, cx + s * 0.62f, cy + s, strokePaint);
                    break;
                case 1:
                case 2:
                    canvas.drawCircle(cx, cy, s * 0.92f, strokePaint);
                    canvas.drawLine(cx - s * 0.45f, cy, cx + s * 0.45f, cy, strokePaint);
                    if (index == 2) canvas.drawLine(cx, cy - s * 0.45f, cx, cy + s * 0.45f, strokePaint);
                    break;
                case 3:
                    canvas.drawLine(cx - s, cy, cx + s, cy, strokePaint); canvas.drawLine(cx, cy - s, cx, cy + s, strokePaint);
                    canvas.drawCircle(cx, cy, s * 0.28f, fillPaint);
                    break;
                case 4:
                    canvas.drawOval(new RectF(cx - s * 0.75f, cy - s, cx + s * 0.75f, cy + s), strokePaint);
                    canvas.drawLine(cx, cy - s, cx, cy - s * 0.25f, strokePaint);
                    break;
                case 5:
                    path.moveTo(cx - s * 0.7f, cy - s); path.lineTo(cx - s * 0.7f, cy + s); path.lineTo(cx + s * 0.72f, cy + s * 0.2f); path.close();
                    canvas.drawPath(path, strokePaint);
                    break;
                case 6:
                    canvas.drawText("D", cx, cy + s * 0.35f, textPaint);
                    break;
                case 7:
                    canvas.drawText("M", cx, cy + s * 0.35f, textPaint);
                    break;
                case 8:
                    path.moveTo(cx - s, cy + s * 0.55f); path.cubicTo(cx - s * 0.2f, cy - s * 0.9f, cx + s * 0.35f, cy + s * 0.1f, cx + s, cy - s * 0.55f);
                    canvas.drawPath(path, strokePaint);
                    break;
                case 9:
                    canvas.drawLine(cx - s, cy - s * 0.35f, cx + s, cy - s * 0.35f, strokePaint);
                    canvas.drawLine(cx - s * 0.7f, cy + s * 0.1f, cx + s * 0.7f, cy + s * 0.1f, strokePaint);
                    canvas.drawLine(cx - s * 0.4f, cy + s * 0.55f, cx + s * 0.4f, cy + s * 0.55f, strokePaint);
                    break;
            }
        }

        private int settingsIndex(float x, float y) {
            if (!settingsRect.contains(x, y)) return -1;
            int col = (int) ((x - settingsRect.left) / (settingsRect.width() / 10f));
            return Math.max(0, Math.min(9, col));
        }

        private void drawDpad(Canvas canvas) {
            int alpha = (int) (210 * dpadOpacity);
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(Color.argb((int) (42 * dpadOpacity), 235, 232, 220));
            canvas.drawCircle(dpadCx, dpadCy, dpadRadius * 0.98f, fillPaint);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(1.2f));
            strokePaint.setColor(Color.argb((int) (88 * dpadOpacity), 200, 170, 130));
            canvas.drawCircle(dpadCx, dpadCy, dpadRadius * 0.98f, strokePaint);
            drawChevron(canvas, dpadCx, dpadCy - dpadRadius * 0.50f, dpadRadius * 0.22f, 0, activeKey == KeyEvent.KEYCODE_DPAD_UP, alpha);
            drawChevron(canvas, dpadCx + dpadRadius * 0.50f, dpadCy, dpadRadius * 0.22f, 1, activeKey == KeyEvent.KEYCODE_DPAD_RIGHT, alpha);
            drawChevron(canvas, dpadCx, dpadCy + dpadRadius * 0.50f, dpadRadius * 0.22f, 2, activeKey == KeyEvent.KEYCODE_DPAD_DOWN, alpha);
            drawChevron(canvas, dpadCx - dpadRadius * 0.50f, dpadCy, dpadRadius * 0.22f, 3, activeKey == KeyEvent.KEYCODE_DPAD_LEFT, alpha);
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(Color.argb((int) (80 * dpadOpacity), 8, 8, 10));
            canvas.drawCircle(dpadCx, dpadCy, dpadRadius * 0.30f, fillPaint);
        }

        private void drawChevron(Canvas canvas, float x, float y, float s, int direction, boolean pressed, int alpha) {
            Path path = new Path();
            if (direction == 0) {
                path.moveTo(x, y - s); path.lineTo(x - s, y + s); path.lineTo(x + s, y + s);
            } else if (direction == 1) {
                path.moveTo(x + s, y); path.lineTo(x - s, y - s); path.lineTo(x - s, y + s);
            } else if (direction == 2) {
                path.moveTo(x, y + s); path.lineTo(x - s, y - s); path.lineTo(x + s, y - s);
            } else {
                path.moveTo(x - s, y); path.lineTo(x + s, y - s); path.lineTo(x + s, y + s);
            }
            path.close();
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(pressed ? Color.argb(255, 210, 180, 135) : Color.argb(alpha, 235, 232, 220));
            canvas.drawPath(path, fillPaint);
        }

        private void drawCursor(Canvas canvas) {
            float size = dp(11f) * cursorScale;
            float pulse = clickPulse;
            Path path = new Path();
            path.moveTo(cursorX, cursorY);
            path.lineTo(cursorX, cursorY + size * 1.68f);
            path.lineTo(cursorX + size * 0.44f, cursorY + size * 1.18f);
            path.lineTo(cursorX + size * 0.78f, cursorY + size * 1.86f);
            path.lineTo(cursorX + size * 1.08f, cursorY + size * 1.72f);
            path.lineTo(cursorX + size * 0.74f, cursorY + size * 1.04f);
            path.lineTo(cursorX + size * 1.42f, cursorY + size * 1.04f);
            path.close();
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(Color.argb((int) (255 * mouseOpacity), Color.red(cursorColor), Color.green(cursorColor), Color.blue(cursorColor)));
            canvas.drawPath(path, fillPaint);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(1.2f));
            strokePaint.setColor(Color.argb(225, 16, 14, 10));
            canvas.drawPath(path, strokePaint);
            if (pulse > 0f) {
                strokePaint.setStyle(Paint.Style.STROKE);
                strokePaint.setStrokeWidth(dp(1.8f));
                strokePaint.setColor(Color.argb(170, Color.red(cursorColor), Color.green(cursorColor), Color.blue(cursorColor)));
                canvas.drawCircle(cursorX, cursorY, pulse, strokePaint);
                clickPulse = Math.max(0f, clickPulse - dp(3.5f));
                postInvalidateOnAnimation();
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN && handleRect.contains(x, y)) {
                settingsOpen = !settingsOpen;
                invalidate();
                return true;
            }
            if (settingsOpen && settingsRect.contains(x, y)) {
                if (action == MotionEvent.ACTION_UP) handleSettingsTap(x, y);
                return true;
            }
            if (dpadBounds.contains(x, y) || draggingDpad) {
                handleDpad(action, x, y);
                return true;
            }
            if (mouseEnabled) {
                handleTrackpad(action, x, y);
                return true;
            }
            if (action == MotionEvent.ACTION_UP) maybeEscapeDoubleTap();
            return false;
        }

        private void handleSettingsTap(float x, float y) {
            switch (settingsIndex(x, y)) {
                case 0:
                    minimizeToHub();
                    break;
                case 1:
                    dpadRadius = Math.max(dp(30f), dpadRadius - dp(6f));
                    break;
                case 2:
                    dpadRadius = Math.min(dp(76f), dpadRadius + dp(6f));
                    break;
                case 3:
                    dpadDragMode = !dpadDragMode;
                    break;
                case 4:
                    mouseEnabled = !mouseEnabled;
                    break;
                case 5:
                    cursorScale = cursorScale >= 1.45f ? 0.75f : cursorScale + 0.25f;
                    cursorColor = cursorColor == Color.rgb(238, 226, 190) ? Color.rgb(126, 220, 210) : Color.rgb(238, 226, 190);
                    break;
                case 6:
                    dpadOpacity = dpadOpacity <= 0.35f ? 0.85f : dpadOpacity - 0.25f;
                    break;
                case 7:
                    mouseOpacity = mouseOpacity <= 0.35f ? 0.90f : mouseOpacity - 0.25f;
                    break;
                case 8:
                    pointerAcceleration = pointerAcceleration >= 0.75f ? 0f : pointerAcceleration + 0.25f;
                    break;
                case 9:
                    pointerSmoothing = pointerSmoothing >= 0.65f ? 0f : pointerSmoothing + 0.25f;
                    pointerSensitivity = pointerSensitivity >= 1.95f ? 0.85f : pointerSensitivity + 0.35f;
                    break;
            }
            layoutRects();
            saveControlPrefs();
            invalidate();
        }

        private void minimizeToHub() {
            String gamePath = getIntent().getStringExtra("game_path");
            getSharedPreferences("runestone", MODE_PRIVATE).edit()
                .putBoolean("game_minimized", true)
                .putString("paused_game", gamePath)
                .apply();
            Intent intent = new Intent();
            intent.setClassName(getPackageName(), "com.runestone.app.MainActivity");
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        }

        private void handleDpad(int action, float x, float y) {
            if (dpadDragMode) {
                if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                    draggingDpad = true;
                    dpadCx = x;
                    dpadCy = y;
                    layoutRects();
                    invalidate();
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    draggingDpad = false;
                    saveControlPrefs();
                }
                return;
            }
            int next = KeyEvent.KEYCODE_UNKNOWN;
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                float dx = x - dpadCx;
                float dy = y - dpadCy;
                if (Math.hypot(dx, dy) > dpadRadius * 0.24f) {
                    next = Math.abs(dx) > Math.abs(dy)
                        ? (dx > 0 ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT)
                        : (dy > 0 ? KeyEvent.KEYCODE_DPAD_DOWN : KeyEvent.KEYCODE_DPAD_UP);
                }
            }
            updateKey(next);
        }

        private void handleTrackpad(int action, float x, float y) {
            if (action == MotionEvent.ACTION_DOWN) {
                trackpadActive = true;
                trackpadMoved = false;
                downX = x;
                downY = y;
                lastX = x;
                lastY = y;
                targetCursorX = cursorX;
                targetCursorY = cursorY;
                showCursor();
            } else if (action == MotionEvent.ACTION_MOVE && trackpadActive) {
                float dx = x - lastX;
                float dy = y - lastY;
                if (Math.hypot(x - downX, y - downY) > dp(8f)) trackpadMoved = true;
                float distance = (float) Math.hypot(dx, dy);
                float gain = pointerSensitivity + pointerAcceleration * Math.min(2.4f, distance / dp(18f));
                targetCursorX = clamp(targetCursorX + dx * gain, 0f, getWidth());
                targetCursorY = clamp(targetCursorY + dy * gain, 0f, getHeight());
                float lerp = 1f - pointerSmoothing;
                cursorX = clamp(cursorX + (targetCursorX - cursorX) * lerp, 0f, getWidth());
                cursorY = clamp(cursorY + (targetCursorY - cursorY) * lerp, 0f, getHeight());
                lastX = x;
                lastY = y;
                showCursor();
                dispatchMouse(MotionEvent.ACTION_HOVER_MOVE, cursorX, cursorY, false);
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                if (trackpadActive && !trackpadMoved && action == MotionEvent.ACTION_UP) {
                    handleTapRelease();
                }
                trackpadActive = false;
            }
        }

        private void handleTapRelease() {
            long now = System.currentTimeMillis();
            if (now - lastGameTapAt < 280L) {
                sendKey(KeyEvent.KEYCODE_ESCAPE, true);
                sendKey(KeyEvent.KEYCODE_ESCAPE, false);
                lastGameTapAt = 0L;
                return;
            }
            lastGameTapAt = now;
            clickAtCursor();
        }

        private void clickAtCursor() {
            dispatchMouse(MotionEvent.ACTION_DOWN, cursorX, cursorY, true);
            dispatchMouse(MotionEvent.ACTION_BUTTON_PRESS, cursorX, cursorY, true);
            dispatchMouse(MotionEvent.ACTION_BUTTON_RELEASE, cursorX, cursorY, false);
            dispatchMouse(MotionEvent.ACTION_UP, cursorX, cursorY, false);
            clickPulse = dp(18f);
            showCursor();
        }

        private void maybeEscapeDoubleTap() {
            long now = System.currentTimeMillis();
            if (now - lastGameTapAt < 280L) {
                sendKey(KeyEvent.KEYCODE_ESCAPE, true);
                sendKey(KeyEvent.KEYCODE_ESCAPE, false);
                lastGameTapAt = 0L;
            } else {
                lastGameTapAt = now;
            }
        }

        private void updateKey(int keyCode) {
            if (keyCode == activeKey) {
                return;
            }
            if (activeKey != KeyEvent.KEYCODE_UNKNOWN) {
                sendKey(activeKey, false);
            }
            activeKey = keyCode;
            if (activeKey != KeyEvent.KEYCODE_UNKNOWN) {
                sendKey(activeKey, true);
                vibrate(0.02);
            }
            invalidate();
        }

        private void showCursor() {
            cursorVisible = true;
            handler.removeCallbacks(hideCursor);
            handler.postDelayed(hideCursor, 1600);
            invalidate();
        }

        private float clamp(float value, float min, float max) {
            return Math.max(min, Math.min(max, value));
        }

        private void loadControlPrefs() {
            android.content.SharedPreferences prefs = getSharedPreferences("renpy-controls-v1", MODE_PRIVATE);
            dpadCx = prefs.getFloat("dpad_cx", 0f);
            dpadCy = prefs.getFloat("dpad_cy", 0f);
            dpadRadius = prefs.getFloat("dpad_radius", dp(46f));
            dpadOpacity = prefs.getFloat("dpad_opacity", 0.70f);
            mouseOpacity = prefs.getFloat("mouse_opacity", 0.85f);
            cursorScale = prefs.getFloat("cursor_scale", 0.90f);
            pointerSensitivity = prefs.getFloat("pointer_sensitivity", 1.25f);
            pointerAcceleration = prefs.getFloat("pointer_acceleration", 0.35f);
            pointerSmoothing = prefs.getFloat("pointer_smoothing", 0.25f);
            mouseEnabled = prefs.getBoolean("mouse_enabled", true);
            cursorColor = prefs.getInt("cursor_color", Color.rgb(238, 226, 190));
        }

        private void saveControlPrefs() {
            getSharedPreferences("renpy-controls-v1", MODE_PRIVATE).edit()
                .putFloat("dpad_cx", dpadCx)
                .putFloat("dpad_cy", dpadCy)
                .putFloat("dpad_radius", dpadRadius)
                .putFloat("dpad_opacity", dpadOpacity)
                .putFloat("mouse_opacity", mouseOpacity)
                .putFloat("cursor_scale", cursorScale)
                .putFloat("pointer_sensitivity", pointerSensitivity)
                .putFloat("pointer_acceleration", pointerAcceleration)
                .putFloat("pointer_smoothing", pointerSmoothing)
                .putBoolean("mouse_enabled", mouseEnabled)
                .putInt("cursor_color", cursorColor)
                .apply();
        }

        private float dp(float value) {
            return value * getResources().getDisplayMetrics().density;
        }
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
