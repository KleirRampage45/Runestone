/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * Per-game patch/mod/translation tracking.
 * Patches install on top of the installed playable game directory. The
 * directory is still named original/ for compatibility, but it is not an
 * immutable clean copy. Only overwritten files get backed up, and only added
 * files get tracked by path.
 * Zero full-game duplication for clean games; space-efficient for patched ones.
 */

package com.runestone.app.data

import org.json.JSONArray
import org.json.JSONObject

data class PatchesSection(
    val installedPatches: List<InstalledPatch> = emptyList(),
) {
    companion object {
        fun fromJson(j: JSONObject?): PatchesSection {
            if (j == null) return PatchesSection()
            val arr = j.optJSONArray("installedPatches") ?: return PatchesSection()
            val list = mutableListOf<InstalledPatch>()
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { list.add(InstalledPatch.fromJson(it)) }
            }
            return PatchesSection(installedPatches = list)
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        val arr = JSONArray()
        installedPatches.forEach { arr.put(it.toJson()) }
        put("installedPatches", arr)
    }
}

data class InstalledPatch(
    val patchId: String,
    val name: String,
    val description: String = "",
    val installedAtMillis: Long,
    val sourceFileName: String = "",
    val isTranslation: Boolean = false,
    val isActive: Boolean = true,
    val overwrittenCount: Int = 0,
    val addedCount: Int = 0,
) {
    companion object {
        fun fromJson(j: JSONObject): InstalledPatch = InstalledPatch(
            patchId = j.getString("patchId"),
            name = j.getString("name"),
            description = j.optString("description", ""),
            installedAtMillis = j.getLong("installedAtMillis"),
            sourceFileName = j.optString("sourceFileName", ""),
            isTranslation = j.optBoolean("isTranslation", false),
            isActive = j.optBoolean("isActive", true),
            overwrittenCount = j.optInt("overwrittenCount", 0),
            addedCount = j.optInt("addedCount", 0),
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("patchId", patchId)
        put("name", name)
        putOpt("description", description.takeIf { it.isNotBlank() })
        put("installedAtMillis", installedAtMillis)
        putOpt("sourceFileName", sourceFileName.takeIf { it.isNotBlank() })
        put("isTranslation", isTranslation)
        put("isActive", isActive)
        put("overwrittenCount", overwrittenCount)
        put("addedCount", addedCount)
    }
}
