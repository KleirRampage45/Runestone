/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.runestone.app.data

enum class LayoutMode(val displayName: String) {
    LANDSCAPE("Landscape"),
    PORTRAIT_CONSOLE("Portrait"),
    GAMEPAD("Gamepad"),
    ;

    fun normalized(): LayoutMode =
        if (this == GAMEPAD) LANDSCAPE else this

    companion object {
        val visibleModes: List<LayoutMode> = listOf(PORTRAIT_CONSOLE, LANDSCAPE)

        fun parse(value: String?, fallback: LayoutMode = PORTRAIT_CONSOLE): LayoutMode {
            val normalized = value.orEmpty().trim().replace('-', '_').replace(' ', '_')
            return values().firstOrNull {
                it.name.equals(normalized, ignoreCase = true) ||
                    it.displayName.equals(value.orEmpty(), ignoreCase = true)
            } ?: fallback
        }
    }
}
