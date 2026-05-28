/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * Physical controller detection and mapping system.
 * Auto-detects gamepads (Xbox, PS4, Switch, generic HID) and maps
 * physical inputs to virtual keyboard keys sent to the game engine.
 */

package com.runestone.app.input

import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent

/**
 * Maps physical controller inputs to game keyboard keys.
 *
 * Button mapping: physical button → GameButton → keyboard keycode
 * Axis mapping: analog stick axis → direction buttons
 *
 * Presets: Xbox 360/One, PS4/PS5, Switch Pro, Retroid Pocket, Anbernic, AYN Odin, Generic
 */
object ControllerMapper {

    private const val TAG = "ControllerMapper"

    // ── Button definitions ──────────────────────────────────────

    enum class GameButton(val defaultKeyCode: Int) {
        DPAD_UP(KeyEvent.KEYCODE_DPAD_UP),
        DPAD_DOWN(KeyEvent.KEYCODE_DPAD_DOWN),
        DPAD_LEFT(KeyEvent.KEYCODE_DPAD_LEFT),
        DPAD_RIGHT(KeyEvent.KEYCODE_DPAD_RIGHT),
        BTN_A(KeyEvent.KEYCODE_Z),
        BTN_B(KeyEvent.KEYCODE_X),
        BTN_X(KeyEvent.KEYCODE_Q),
        BTN_Y(KeyEvent.KEYCODE_W),
        BTN_L1(KeyEvent.KEYCODE_A),
        BTN_R1(KeyEvent.KEYCODE_S),
        BTN_L2(KeyEvent.KEYCODE_D),
        BTN_R2(KeyEvent.KEYCODE_F),
        SELECT(KeyEvent.KEYCODE_ESCAPE),
        START(KeyEvent.KEYCODE_ENTER),
        MENU(KeyEvent.KEYCODE_M),
        SPEED_TOGGLE(KeyEvent.KEYCODE_TAB),
    }

    data class ControllerPreset(
        val name: String,
        val vendorIds: Set<Int>,         // USB vendor IDs for auto-detection
        val productIds: Set<Int>,        // USB product IDs
        val buttonMap: Map<Int, GameButton>,  // Android keyCode → GameButton
        val axisMap: Map<Int, AxisConfig>,     // MotionEvent.AXIS_* → direction pair
    )

    data class AxisConfig(
        val positiveButton: GameButton,
        val negativeButton: GameButton,
        val deadZone: Float = 0.3f,
    )

    // ── Built-in presets ────────────────────────────────────────

    val presets: Map<String, ControllerPreset> = mapOf(
        "xbox" to ControllerPreset(
            name = "Xbox 360 / One / Series",
            vendorIds = setOf(0x045E, 0x24C6),
            productIds = setOf(0x028E, 0x02D1, 0x02DD, 0x0B12),
            buttonMap = mapOf(
                KeyEvent.KEYCODE_BUTTON_A to GameButton.BTN_A,
                KeyEvent.KEYCODE_BUTTON_B to GameButton.BTN_B,
                KeyEvent.KEYCODE_BUTTON_X to GameButton.BTN_X,
                KeyEvent.KEYCODE_BUTTON_Y to GameButton.BTN_Y,
                KeyEvent.KEYCODE_BUTTON_L1 to GameButton.BTN_L1,
                KeyEvent.KEYCODE_BUTTON_R1 to GameButton.BTN_R1,
                KeyEvent.KEYCODE_BUTTON_L2 to GameButton.BTN_L2,
                KeyEvent.KEYCODE_BUTTON_R2 to GameButton.BTN_R2,
                KeyEvent.KEYCODE_BUTTON_SELECT to GameButton.SELECT,
                KeyEvent.KEYCODE_BUTTON_START to GameButton.START,
                KeyEvent.KEYCODE_BUTTON_THUMBL to GameButton.MENU,
            ),
            axisMap = mapOf(
                MotionEvent.AXIS_HAT_X to AxisConfig(GameButton.DPAD_RIGHT, GameButton.DPAD_LEFT, 0.5f),
                MotionEvent.AXIS_HAT_Y to AxisConfig(GameButton.DPAD_DOWN, GameButton.DPAD_UP, 0.5f),
                MotionEvent.AXIS_X to AxisConfig(GameButton.DPAD_RIGHT, GameButton.DPAD_LEFT, 0.3f),
                MotionEvent.AXIS_Y to AxisConfig(GameButton.DPAD_DOWN, GameButton.DPAD_UP, 0.3f),
            ),
        ),
        "ps4" to ControllerPreset(
            name = "PS4 / PS5 DualSense",
            vendorIds = setOf(0x054C),
            productIds = setOf(0x05C4, 0x09CC, 0x0CE6),
            buttonMap = mapOf(
                KeyEvent.KEYCODE_BUTTON_A to GameButton.BTN_A,     // Cross
                KeyEvent.KEYCODE_BUTTON_B to GameButton.BTN_B,     // Circle
                KeyEvent.KEYCODE_BUTTON_X to GameButton.BTN_Y,     // Square → Y
                KeyEvent.KEYCODE_BUTTON_Y to GameButton.BTN_X,     // Triangle → X
                KeyEvent.KEYCODE_BUTTON_L1 to GameButton.BTN_L1,
                KeyEvent.KEYCODE_BUTTON_R1 to GameButton.BTN_R1,
                KeyEvent.KEYCODE_BUTTON_L2 to GameButton.BTN_L2,
                KeyEvent.KEYCODE_BUTTON_R2 to GameButton.BTN_R2,
                KeyEvent.KEYCODE_BUTTON_SELECT to GameButton.SELECT,
                KeyEvent.KEYCODE_BUTTON_START to GameButton.START,
                KeyEvent.KEYCODE_BUTTON_THUMBL to GameButton.MENU,
            ),
            axisMap = mapOf(
                MotionEvent.AXIS_HAT_X to AxisConfig(GameButton.DPAD_RIGHT, GameButton.DPAD_LEFT, 0.5f),
                MotionEvent.AXIS_HAT_Y to AxisConfig(GameButton.DPAD_DOWN, GameButton.DPAD_UP, 0.5f),
                MotionEvent.AXIS_X to AxisConfig(GameButton.DPAD_RIGHT, GameButton.DPAD_LEFT, 0.3f),
                MotionEvent.AXIS_Y to AxisConfig(GameButton.DPAD_DOWN, GameButton.DPAD_UP, 0.3f),
            ),
        ),
        "switch" to ControllerPreset(
            name = "Switch Pro / Joy-Con",
            vendorIds = setOf(0x057E),
            productIds = setOf(0x2009, 0x2006, 0x2007),
            buttonMap = mapOf(
                KeyEvent.KEYCODE_BUTTON_A to GameButton.BTN_B,     // A → B (reversed)
                KeyEvent.KEYCODE_BUTTON_B to GameButton.BTN_A,
                KeyEvent.KEYCODE_BUTTON_X to GameButton.BTN_Y,
                KeyEvent.KEYCODE_BUTTON_Y to GameButton.BTN_X,
                KeyEvent.KEYCODE_BUTTON_L1 to GameButton.BTN_L1,
                KeyEvent.KEYCODE_BUTTON_R1 to GameButton.BTN_R1,
                KeyEvent.KEYCODE_BUTTON_L2 to GameButton.BTN_L2,
                KeyEvent.KEYCODE_BUTTON_R2 to GameButton.BTN_R2,
                KeyEvent.KEYCODE_BUTTON_SELECT to GameButton.SELECT,
                KeyEvent.KEYCODE_BUTTON_START to GameButton.START,
                KeyEvent.KEYCODE_BUTTON_THUMBL to GameButton.MENU,
            ),
            axisMap = mapOf(
                MotionEvent.AXIS_HAT_X to AxisConfig(GameButton.DPAD_RIGHT, GameButton.DPAD_LEFT, 0.5f),
                MotionEvent.AXIS_HAT_Y to AxisConfig(GameButton.DPAD_DOWN, GameButton.DPAD_UP, 0.5f),
                MotionEvent.AXIS_X to AxisConfig(GameButton.DPAD_RIGHT, GameButton.DPAD_LEFT, 0.3f),
                MotionEvent.AXIS_Y to AxisConfig(GameButton.DPAD_DOWN, GameButton.DPAD_UP, 0.3f),
            ),
        ),
        "generic" to ControllerPreset(
            name = "Generic HID Gamepad",
            vendorIds = emptySet(),
            productIds = emptySet(),
            buttonMap = mapOf(
                KeyEvent.KEYCODE_BUTTON_A to GameButton.BTN_A,
                KeyEvent.KEYCODE_BUTTON_B to GameButton.BTN_B,
                KeyEvent.KEYCODE_BUTTON_X to GameButton.BTN_X,
                KeyEvent.KEYCODE_BUTTON_Y to GameButton.BTN_Y,
                KeyEvent.KEYCODE_BUTTON_L1 to GameButton.BTN_L1,
                KeyEvent.KEYCODE_BUTTON_R1 to GameButton.BTN_R1,
                KeyEvent.KEYCODE_BUTTON_L2 to GameButton.BTN_L2,
                KeyEvent.KEYCODE_BUTTON_R2 to GameButton.BTN_R2,
                KeyEvent.KEYCODE_BUTTON_SELECT to GameButton.SELECT,
                KeyEvent.KEYCODE_BUTTON_START to GameButton.START,
                KeyEvent.KEYCODE_BUTTON_THUMBL to GameButton.MENU,
            ),
            axisMap = mapOf(
                MotionEvent.AXIS_HAT_X to AxisConfig(GameButton.DPAD_RIGHT, GameButton.DPAD_LEFT, 0.5f),
                MotionEvent.AXIS_HAT_Y to AxisConfig(GameButton.DPAD_DOWN, GameButton.DPAD_UP, 0.5f),
                MotionEvent.AXIS_X to AxisConfig(GameButton.DPAD_RIGHT, GameButton.DPAD_LEFT, 0.3f),
                MotionEvent.AXIS_Y to AxisConfig(GameButton.DPAD_DOWN, GameButton.DPAD_UP, 0.3f),
            ),
        ),
    )

    // ── Detection ───────────────────────────────────────────────

    /** Auto-detect the controller preset for a given InputDevice, or "generic". */
    fun detectPreset(device: InputDevice): String {
        val vid = device.vendorId
        val pid = device.productId
        for ((id, preset) in presets) {
            if (vid in preset.vendorIds && pid in preset.productIds) {
                Log.i(TAG, "Detected controller: ${preset.name} (vid=$vid pid=$pid)")
                return id
            }
        }
        Log.d(TAG, "Unknown controller: vid=$vid pid=$pid — using generic")
        return "generic"
    }

    /** Get the preset by name, falling back to generic. */
    fun getPreset(name: String): ControllerPreset = presets[name] ?: presets["generic"]!!

    // ── Mapping ─────────────────────────────────────────────────

    /** Map a physical KeyEvent to a GameButton using the given preset. */
    fun mapKeyToButton(event: KeyEvent, preset: ControllerPreset): GameButton? {
        return preset.buttonMap[event.keyCode]
    }

    /** Map analog axis motion to a list of active GameButtons. */
    fun mapAxisToButtons(event: MotionEvent, preset: ControllerPreset): List<GameButton> {
        val buttons = mutableListOf<GameButton>()
        for ((axis, config) in preset.axisMap) {
            val value = event.getAxisValue(axis)
            if (value > config.deadZone) buttons.add(config.positiveButton)
            if (value < -config.deadZone) buttons.add(config.negativeButton)
        }
        return buttons
    }

    /** Convert a GameButton to its default keyboard keycode. */
    fun toKeyCode(button: GameButton): Int = button.defaultKeyCode
}
