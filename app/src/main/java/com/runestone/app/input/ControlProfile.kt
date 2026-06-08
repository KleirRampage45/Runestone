package com.runestone.app.input

import android.content.Context
import com.runestone.app.data.EngineType
import com.runestone.app.data.RunnerSettings
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class ControlProfileScope {
    GLOBAL,
    ENGINE,
    GAME,
}

data class ControlButtonProfile(
    val id: String,
    val label: String,
    val key: String,
    val layout: String,
    val x: Float,
    val y: Float,
    val size: Float,
    val opacity: Float,
    val hapticIntensity: Float,
    val soundPath: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("label", label)
        put("key", key)
        put("layout", layout)
        put("x", x.toDouble())
        put("y", y.toDouble())
        put("size", size.toDouble())
        put("opacity", opacity.toDouble())
        put("hapticIntensity", hapticIntensity.toDouble())
        if (soundPath.isNotEmpty()) put("soundPath", soundPath)
    }

    companion object {
        fun fromJson(json: JSONObject): ControlButtonProfile =
            ControlButtonProfile(
                id = json.optString("id", ""),
                label = json.optString("label", ""),
                key = json.optString("key", ""),
                layout = json.optString("layout", "landscape"),
                x = json.optDouble("x", 0.5).toFloat(),
                y = json.optDouble("y", 0.5).toFloat(),
                size = json.optDouble("size", 0.1).toFloat(),
                opacity = json.optDouble("opacity", 0.72).toFloat(),
                hapticIntensity = json.optDouble("hapticIntensity", 0.55).toFloat(),
                soundPath = json.optString("soundPath", ""),
            )
    }
}

data class ControlProfile(
    val id: String,
    val name: String,
    val scope: ControlProfileScope,
    val engineType: EngineType? = null,
    val storageName: String? = null,
    val version: Int = 1,
    val buttons: List<ControlButtonProfile> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("version", version)
        put("id", id)
        put("name", name)
        put("scope", scope.name.lowercase())
        if (engineType != null) put("engineType", engineType.name)
        if (!storageName.isNullOrEmpty()) put("storageName", storageName)
        put("buttons", JSONArray().apply {
            buttons.forEach { put(it.toJson()) }
        })
    }

    companion object {
        fun fromJson(json: JSONObject): ControlProfile {
            val buttonArray = json.optJSONArray("buttons") ?: JSONArray()
            val parsedButtons = mutableListOf<ControlButtonProfile>().apply {
                for (index in 0 until buttonArray.length()) {
                    buttonArray.optJSONObject(index)?.let { add(ControlButtonProfile.fromJson(it)) }
                }
            }
            return ControlProfile(
                id = json.optString("id", "default"),
                name = json.optString("name", "Default"),
                scope = runCatching {
                    ControlProfileScope.valueOf(json.optString("scope", "global").uppercase())
                }.getOrDefault(ControlProfileScope.GLOBAL),
                engineType = json.optString("engineType", "").takeIf { it.isNotEmpty() }?.let {
                    runCatching { EngineType.valueOf(it) }.getOrNull()
                },
                storageName = json.optString("storageName", "").takeIf { it.isNotEmpty() },
                version = json.optInt("version", 1),
                buttons = parsedButtons,
            )
        }

        fun defaultFor(
            settings: RunnerSettings,
            scope: ControlProfileScope,
            engineType: EngineType? = null,
            storageName: String? = null,
        ): ControlProfile {
            val suffix = when (scope) {
                ControlProfileScope.GLOBAL -> "global"
                ControlProfileScope.ENGINE -> engineType?.name?.lowercase() ?: "engine"
                ControlProfileScope.GAME -> storageName ?: "game"
            }
            val buttons = listOf(
                button("dpad", "D-Pad", "DPAD", "landscape", 0.16f, 0.62f, 0.18f, settings),
                button("a", "A", settings.firstButtonKey, "landscape", 0.84f, 0.68f, 0.10f, settings),
                button("b", "B", settings.fourthButtonKey, "landscape", 0.76f, 0.58f, 0.10f, settings),
                button("x", "X", settings.thirdButtonKey, "landscape", 0.68f, 0.68f, 0.10f, settings),
                button("y", "Y", settings.secondButtonKey, "landscape", 0.76f, 0.48f, 0.10f, settings),
                button("select", "Select", "ESCAPE", "landscape", 0.18f, 0.88f, 0.08f, settings),
                button("start", "Start", "ENTER", "landscape", 0.50f, 0.88f, 0.08f, settings),
                button("menu", "Menu", "MENU", "landscape", 0.82f, 0.88f, 0.08f, settings),
                button("dpad", "D-Pad", "DPAD", "portrait", 0.25f, 0.40f, 0.24f, settings),
                button("a", "A", settings.firstButtonKey, "portrait", 0.76f, 0.56f, 0.13f, settings),
                button("b", "B", settings.fourthButtonKey, "portrait", 0.62f, 0.44f, 0.13f, settings),
                button("select", "Select", "ESCAPE", "portrait", 0.20f, 0.82f, 0.10f, settings),
                button("start", "Start", "ENTER", "portrait", 0.50f, 0.82f, 0.10f, settings),
                button("menu", "Menu", "MENU", "portrait", 0.80f, 0.82f, 0.10f, settings),
            )
            return ControlProfile(
                id = "default-$suffix",
                name = "Default",
                scope = scope,
                engineType = engineType,
                storageName = storageName,
                buttons = buttons,
            )
        }

        private fun button(
            id: String,
            label: String,
            key: String,
            layout: String,
            x: Float,
            y: Float,
            size: Float,
            settings: RunnerSettings,
        ) = ControlButtonProfile(
            id = id,
            label = label,
            key = key,
            layout = layout,
            x = x,
            y = y,
            size = size * settings.touchScale,
            opacity = settings.touchOpacity,
            hapticIntensity = settings.hapticIntensity,
        )
    }
}

class ControlProfileStore(private val context: Context) {
    private val baseDir: File
        get() = File(context.filesDir, "control_profiles")

    fun ensureDefaults(engineType: EngineType, storageName: String?, settings: RunnerSettings) {
        saveIfMissing(globalFile(), ControlProfile.defaultFor(settings, ControlProfileScope.GLOBAL))
        saveIfMissing(engineFile(engineType), ControlProfile.defaultFor(settings, ControlProfileScope.ENGINE, engineType))
        if (!storageName.isNullOrEmpty()) {
            saveIfMissing(gameFile(storageName), ControlProfile.defaultFor(settings, ControlProfileScope.GAME, engineType, storageName))
        }
    }

    fun loadEffective(engineType: EngineType, storageName: String?, settings: RunnerSettings): ControlProfile =
        listOfNotNull(
            storageName?.let { load(gameFile(it)) },
            load(engineFile(engineType)),
            load(globalFile()),
        ).firstOrNull() ?: ControlProfile.defaultFor(settings, ControlProfileScope.GLOBAL)

    fun save(profile: ControlProfile) {
        val file = when (profile.scope) {
            ControlProfileScope.GLOBAL -> globalFile()
            ControlProfileScope.ENGINE -> engineFile(profile.engineType ?: EngineType.UNKNOWN)
            ControlProfileScope.GAME -> gameFile(profile.storageName ?: "unknown")
        }
        saveProfile(file, profile)
    }

    private fun saveIfMissing(file: File, profile: ControlProfile) {
        if (!file.isFile) saveProfile(file, profile)
    }

    private fun load(file: File): ControlProfile? =
        runCatching {
            if (!file.isFile) return@runCatching null
            ControlProfile.fromJson(JSONObject(file.readText()))
        }.getOrNull()

    private fun saveProfile(file: File, profile: ControlProfile) {
        file.parentFile?.mkdirs()
        file.writeText(profile.toJson().toString(2))
    }

    private fun globalFile(): File = File(baseDir, "global/default.json")

    private fun engineFile(engineType: EngineType): File =
        File(baseDir, "engine/${engineType.name.lowercase()}.json")

    private fun gameFile(storageName: String): File =
        File(baseDir, "game/$storageName.json")
}
