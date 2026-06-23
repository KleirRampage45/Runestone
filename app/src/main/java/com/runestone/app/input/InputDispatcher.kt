package com.runestone.app.input

import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import com.runestone.app.data.ControllerShortcut
import com.runestone.app.engine.WebViewEngine
import org.json.JSONObject

class InputDispatcher(private val webViewEngine: WebViewEngine?) {

    private val pressedControllerKeys = mutableSetOf<Int>()
    private val activeControllerAxisButtons = mutableSetOf<ControllerMapper.GameButton>()
    private var triggerHomeComboDown = false
    private var controllerPresetId: String? = null

    fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (isControllerEvent(event)) {
            if (handleControllerCombo(event)) return true
            val mapped = mapControllerKey(event) ?: return false
            dispatchMappedGameKey(mapped, event.action)
            return true
        }

        if (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP) {
            val engine = webViewEngine ?: return false
            val isDown = event.action == KeyEvent.ACTION_DOWN
            val keyChar = event.unicodeChar
            val keyCode = event.keyCode

            engine.dispatchKeyEvent(event)

            val jsAction = if (isDown) "_onKeyDown" else "_onKeyUp"
            val js = """(function(){
                try {
                    if (window.Input && window.Input.$jsAction)
                        window.Input.$jsAction({which:$keyCode, keyCode:$keyCode});
                    if (window.TouchInput && window.TouchInput.$jsAction)
                        window.TouchInput.$jsAction({which:$keyCode, keyCode:$keyCode});
                    if ($isDown && $keyChar > 31) {
                        var c = String.fromCharCode($keyChar).toLowerCase();
                        window.dispatchEvent(new CustomEvent('rune_key', {detail:{key:c,code:$keyCode}}));
                    }
                } catch(e){}
            })();""".trimIndent()
            engine.evaluateJavascript(js, null)
        }
        return false
    }

    fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (!isControllerMotionEvent(event)) return false
        if (handleTriggerHomeCombo(event)) return true
        val preset = controllerPresetFor(event.device)
        val activeButtons = ControllerMapper.mapAxisToButtons(event, preset).toSet()
        val released = activeControllerAxisButtons - activeButtons
        val pressed = activeButtons - activeControllerAxisButtons

        released.forEach { dispatchMappedGameButton(it, KeyEvent.ACTION_UP) }
        pressed.forEach { dispatchMappedGameButton(it, KeyEvent.ACTION_DOWN) }

        activeControllerAxisButtons.clear()
        activeControllerAxisButtons.addAll(activeButtons)
        return pressed.isNotEmpty() || released.isNotEmpty()
    }

    fun releaseControllerAxes() {
        activeControllerAxisButtons.forEach { dispatchMappedGameButton(it, KeyEvent.ACTION_UP) }
        activeControllerAxisButtons.clear()
    }

    fun resetControllerState() {
        pressedControllerKeys.clear()
        activeControllerAxisButtons.clear()
        triggerHomeComboDown = false
    }

    fun sendKeyboardText(text: String) {
        val engine = webViewEngine ?: return
        text.forEach { char ->
            val keyCode = keyCodeForChar(char)
            val js = """(function(){
                try {
                    var key = ${JSONObject.quote(char.toString())};
                    var code = $keyCode;
                    window.dispatchEvent(new KeyboardEvent('keydown', {key:key, keyCode:code, which:code, bubbles:true}));
                    if (window.Input && window.Input._onKeyDown) window.Input._onKeyDown({key:key, keyCode:code, which:code});
                    window.dispatchEvent(new KeyboardEvent('keypress', {key:key, keyCode:code, which:code, bubbles:true}));
                    window.dispatchEvent(new InputEvent('input', {data:key, inputType:'insertText', bubbles:true}));
                    window.dispatchEvent(new KeyboardEvent('keyup', {key:key, keyCode:code, which:code, bubbles:true}));
                    if (window.Input && window.Input._onKeyUp) window.Input._onKeyUp({key:key, keyCode:code, which:code});
                } catch(e) {}
            })();""".trimIndent()
            if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
                engine.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                engine.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            }
            engine.evaluateJavascript(js, null)
        }
    }

    fun sendKeyboardKey(keyCode: Int) {
        val engine = webViewEngine ?: return
        engine.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        engine.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        val key = when (keyCode) {
            KeyEvent.KEYCODE_DEL -> "Backspace"
            KeyEvent.KEYCODE_ENTER -> "Enter"
            else -> ""
        }
        val inputType = if (keyCode == KeyEvent.KEYCODE_DEL) "deleteContentBackward" else "insertLineBreak"
        val js = """(function(){
            try {
                var key = ${JSONObject.quote(key)};
                var code = $keyCode;
                window.dispatchEvent(new KeyboardEvent('keydown', {key:key, keyCode:code, which:code, bubbles:true}));
                if (window.Input && window.Input._onKeyDown) window.Input._onKeyDown({key:key, keyCode:code, which:code});
                window.dispatchEvent(new InputEvent('input', {data:null, inputType:'$inputType', bubbles:true}));
                window.dispatchEvent(new KeyboardEvent('keyup', {key:key, keyCode:code, which:code, bubbles:true}));
                if (window.Input && window.Input._onKeyUp) window.Input._onKeyUp({key:key, keyCode:code, which:code});
            } catch(e) {}
        })();""".trimIndent()
        engine.evaluateJavascript(js, null)
    }

    fun sendKeyboardKeyUp(keyCode: Int) {
        val engine = webViewEngine ?: return
        engine.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        val js = """(function(){
            try {
                window.dispatchEvent(new KeyboardEvent('keyup', {keyCode:$keyCode, which:$keyCode, bubbles:true}));
                if (window.Input && window.Input._onKeyUp) window.Input._onKeyUp({keyCode:$keyCode, which:$keyCode});
            } catch(e) {}
        })();""".trimIndent()
        engine.evaluateJavascript(js, null)
    }

    fun shortcutPressed(shortcut: ControllerShortcut, settingsShortcut: ControllerShortcut): Boolean {
        if (settingsShortcut != shortcut) return false
        return when (shortcut) {
            ControllerShortcut.OFF -> false
            ControllerShortcut.L2_R2 ->
                pressedControllerKeys.contains(KeyEvent.KEYCODE_BUTTON_L2) &&
                    pressedControllerKeys.contains(KeyEvent.KEYCODE_BUTTON_R2)
            ControllerShortcut.L1_R1 ->
                pressedControllerKeys.contains(KeyEvent.KEYCODE_BUTTON_L1) &&
                    pressedControllerKeys.contains(KeyEvent.KEYCODE_BUTTON_R1)
            ControllerShortcut.START_SELECT ->
                pressedControllerKeys.contains(KeyEvent.KEYCODE_BUTTON_START) &&
                    pressedControllerKeys.contains(KeyEvent.KEYCODE_BUTTON_SELECT)
            ControllerShortcut.L2_START ->
                pressedControllerKeys.contains(KeyEvent.KEYCODE_BUTTON_L2) &&
                    pressedControllerKeys.contains(KeyEvent.KEYCODE_BUTTON_START)
            ControllerShortcut.R2_START ->
                pressedControllerKeys.contains(KeyEvent.KEYCODE_BUTTON_R2) &&
                    pressedControllerKeys.contains(KeyEvent.KEYCODE_BUTTON_START)
        }
    }

    private fun handleControllerCombo(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP) {
            pressedControllerKeys.remove(event.keyCode)
            return false
        }
        if (event.action != KeyEvent.ACTION_DOWN) return false
        pressedControllerKeys.add(event.keyCode)
        return event.repeatCount > 0
    }

    private fun handleTriggerHomeCombo(event: MotionEvent): Boolean {
        val left = maxOf(
            event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_BRAKE),
        )
        val right = maxOf(
            event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_GAS),
        )
        val bothPressed = left > 0.55f && right > 0.55f
        if (!bothPressed) {
            triggerHomeComboDown = false
            return false
        }
        if (triggerHomeComboDown) return true
        triggerHomeComboDown = true
        return true
    }

    private fun mapControllerKey(event: KeyEvent): Int? {
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return null
        if (event.repeatCount > 0 && event.action == KeyEvent.ACTION_DOWN) return null

        val directDpad = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT -> event.keyCode
            else -> null
        }
        if (directDpad != null) return directDpad

        val preset = controllerPresetFor(event.device)
        val button = ControllerMapper.mapKeyToButton(event, preset) ?: return null
        return ControllerMapper.toKeyCode(button)
    }

    private fun dispatchMappedGameButton(button: ControllerMapper.GameButton, action: Int) {
        dispatchMappedGameKey(ControllerMapper.toKeyCode(button), action)
    }

    private fun dispatchMappedGameKey(keyCode: Int, action: Int) {
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN) return
        val engine = webViewEngine ?: return
        val keyEvent = KeyEvent(action, keyCode)
        engine.dispatchKeyEvent(keyEvent)
        val jsAction = if (action == KeyEvent.ACTION_DOWN) "_onKeyDown" else "_onKeyUp"
        val js = """(function(){
            try {
                var ev = {which:$keyCode, keyCode:$keyCode};
                if (window.Input && window.Input.$jsAction) window.Input.$jsAction(ev);
                if (window.TouchInput && window.TouchInput.$jsAction) window.TouchInput.$jsAction(ev);
                window.dispatchEvent(new KeyboardEvent('${if (action == KeyEvent.ACTION_DOWN) "keydown" else "keyup"}', {
                    keyCode:$keyCode,
                    which:$keyCode,
                    bubbles:true
                }));
            } catch(e) {}
        })();""".trimIndent()
        engine.evaluateJavascript(js, null)
    }

    private fun controllerPresetFor(device: android.view.InputDevice?): ControllerMapper.ControllerPreset {
        if (device == null) return ControllerMapper.getPreset("generic")
        val current = controllerPresetId
        if (current != null) return ControllerMapper.getPreset(current)
        val detected = ControllerMapper.detectPreset(device)
        controllerPresetId = detected
        return ControllerMapper.getPreset(detected)
    }

    companion object {
        fun zoneToKeyCode(zone: TouchOverlayView.Zone): Int = when (zone) {
            TouchOverlayView.Zone.DPAD_UP -> KeyEvent.KEYCODE_DPAD_UP
            TouchOverlayView.Zone.DPAD_DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
            TouchOverlayView.Zone.DPAD_LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
            TouchOverlayView.Zone.DPAD_RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
            TouchOverlayView.Zone.BTN_CONFIRM -> KeyEvent.KEYCODE_ENTER
            TouchOverlayView.Zone.BTN_BACK -> KeyEvent.KEYCODE_ESCAPE
            TouchOverlayView.Zone.BTN_DASH -> KeyEvent.KEYCODE_SHIFT_LEFT
            TouchOverlayView.Zone.BTN_EXTRA_A -> KeyEvent.KEYCODE_A
            TouchOverlayView.Zone.BTN_EXTRA_S -> KeyEvent.KEYCODE_S
            TouchOverlayView.Zone.BTN_EXTRA_D -> KeyEvent.KEYCODE_D
            TouchOverlayView.Zone.BTN_EXTRA_Z -> KeyEvent.KEYCODE_Z
            TouchOverlayView.Zone.BTN_EXTRA_X -> KeyEvent.KEYCODE_X
            TouchOverlayView.Zone.BTN_EXTRA_C -> KeyEvent.KEYCODE_C
            TouchOverlayView.Zone.BTN_CTRL -> KeyEvent.KEYCODE_CTRL_LEFT
            TouchOverlayView.Zone.BTN_ALT -> KeyEvent.KEYCODE_ALT_LEFT
            TouchOverlayView.Zone.BTN_SHIFT -> KeyEvent.KEYCODE_SHIFT_LEFT
            TouchOverlayView.Zone.SELECT -> KeyEvent.KEYCODE_ESCAPE
            TouchOverlayView.Zone.START -> KeyEvent.KEYCODE_ENTER
            TouchOverlayView.Zone.MENU -> KeyEvent.KEYCODE_F2
            TouchOverlayView.Zone.SETTINGS -> KeyEvent.KEYCODE_F8
            TouchOverlayView.Zone.HOME -> KeyEvent.KEYCODE_HOME
            TouchOverlayView.Zone.L1 -> KeyEvent.KEYCODE_PAGE_UP
            TouchOverlayView.Zone.R1 -> KeyEvent.KEYCODE_PAGE_DOWN
            TouchOverlayView.Zone.OVERLAY_MENU -> KeyEvent.KEYCODE_MENU
            TouchOverlayView.Zone.BTN_A -> KeyEvent.KEYCODE_ENTER
            TouchOverlayView.Zone.BTN_B -> KeyEvent.KEYCODE_ESCAPE
            TouchOverlayView.Zone.BTN_X -> KeyEvent.KEYCODE_Q
            TouchOverlayView.Zone.BTN_Y -> KeyEvent.KEYCODE_W
            TouchOverlayView.Zone.ZL -> KeyEvent.KEYCODE_PAGE_UP
            TouchOverlayView.Zone.ZR -> KeyEvent.KEYCODE_PAGE_DOWN
            TouchOverlayView.Zone.L3 -> KeyEvent.KEYCODE_F5
            TouchOverlayView.Zone.R3 -> KeyEvent.KEYCODE_F6
            TouchOverlayView.Zone.GUIDE -> KeyEvent.KEYCODE_F2
            TouchOverlayView.Zone.PLUS -> KeyEvent.KEYCODE_EQUALS
            TouchOverlayView.Zone.MINUS -> KeyEvent.KEYCODE_MINUS
            TouchOverlayView.Zone.LEFT_STICK -> KeyEvent.KEYCODE_UNKNOWN
            TouchOverlayView.Zone.RIGHT_STICK -> KeyEvent.KEYCODE_UNKNOWN
            TouchOverlayView.Zone.TOOLBAR_TOGGLE -> KeyEvent.KEYCODE_UNKNOWN
            TouchOverlayView.Zone.TOOLBAR_SETTINGS -> KeyEvent.KEYCODE_UNKNOWN
            TouchOverlayView.Zone.TOOLBAR_KEYBOARD -> KeyEvent.KEYCODE_UNKNOWN
            TouchOverlayView.Zone.TOOLBAR_POINTER -> KeyEvent.KEYCODE_UNKNOWN
            TouchOverlayView.Zone.MENU_CHEATS -> KeyEvent.KEYCODE_UNKNOWN
            TouchOverlayView.Zone.MENU_MUTE -> KeyEvent.KEYCODE_UNKNOWN
            TouchOverlayView.Zone.MENU_ROTATE -> KeyEvent.KEYCODE_UNKNOWN
            TouchOverlayView.Zone.MENU_REMAP -> KeyEvent.KEYCODE_UNKNOWN
            TouchOverlayView.Zone.MENU_QUIT -> KeyEvent.KEYCODE_UNKNOWN
        }

        fun keyNameToCode(name: String): Int = when (name) {
            "ENTER" -> KeyEvent.KEYCODE_ENTER
            "ESCAPE" -> KeyEvent.KEYCODE_ESCAPE
            "SPACE" -> KeyEvent.KEYCODE_SPACE
            "TAB" -> KeyEvent.KEYCODE_TAB
            "Z" -> KeyEvent.KEYCODE_Z
            "X" -> KeyEvent.KEYCODE_X
            "Q" -> KeyEvent.KEYCODE_Q
            "B" -> KeyEvent.KEYCODE_B
            "A" -> KeyEvent.KEYCODE_A
            "S" -> KeyEvent.KEYCODE_S
            "D" -> KeyEvent.KEYCODE_D
            "W" -> KeyEvent.KEYCODE_W
            "V" -> KeyEvent.KEYCODE_V
            "C" -> KeyEvent.KEYCODE_C
            "F2" -> KeyEvent.KEYCODE_F2
            "F8" -> KeyEvent.KEYCODE_F8
            "CTRL_LEFT" -> KeyEvent.KEYCODE_CTRL_LEFT
            "SHIFT_LEFT" -> KeyEvent.KEYCODE_SHIFT_LEFT
            "ALT_LEFT" -> KeyEvent.KEYCODE_ALT_LEFT
            else -> KeyEvent.KEYCODE_UNKNOWN
        }

        fun keyCodeForChar(char: Char): Int = when (char) {
            in 'a'..'z' -> KeyEvent.KEYCODE_A + (char - 'a')
            in 'A'..'Z' -> KeyEvent.KEYCODE_A + (char - 'A')
            in '0'..'9' -> KeyEvent.KEYCODE_0 + (char - '0')
            ' ' -> KeyEvent.KEYCODE_SPACE
            else -> KeyEvent.KEYCODE_UNKNOWN
        }

        fun isControllerEvent(event: KeyEvent): Boolean {
            val controllerSources = InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK or InputDevice.SOURCE_DPAD
            return event.source and controllerSources != 0
        }

        fun isControllerMotionEvent(event: MotionEvent): Boolean {
            val controllerSources = InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK or InputDevice.SOURCE_DPAD
            return event.source and controllerSources != 0
        }
    }
}
