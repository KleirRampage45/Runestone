package com.runestone.app.input

import android.view.KeyEvent

class InputMapper {
    fun toAndroidKey(input: RpgInput): Int = when (input) {
        RpgInput.UP -> KeyEvent.KEYCODE_DPAD_UP
        RpgInput.DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
        RpgInput.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
        RpgInput.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
        RpgInput.CONFIRM -> KeyEvent.KEYCODE_Z
        RpgInput.CANCEL,
        RpgInput.MENU -> KeyEvent.KEYCODE_X
        RpgInput.DASH -> KeyEvent.KEYCODE_SHIFT_LEFT
        RpgInput.PAGE_UP -> KeyEvent.KEYCODE_Q
        RpgInput.PAGE_DOWN -> KeyEvent.KEYCODE_W
        RpgInput.FAST_FORWARD -> KeyEvent.KEYCODE_F
        RpgInput.QUICK_SAVE -> KeyEvent.KEYCODE_F5
        RpgInput.QUICK_LOAD -> KeyEvent.KEYCODE_F9
    }
}
