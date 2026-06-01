/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.runestone.app.engine

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.widget.Toast

object UnavailableEngine {

    fun show(context: Context, engineName: String) {
        val activity = context as? Activity
        if (activity == null) {
            Toast.makeText(context, "$engineName engine coming soon", Toast.LENGTH_LONG).show()
            return
        }

        AlertDialog.Builder(activity)
            .setTitle("$engineName Engine Coming Soon")
            .setMessage("This game needs the $engineName runtime. The native library is bundled, but its Android wrapper is not integrated yet.")
            .setPositiveButton("OK") { _, _ -> activity.finish() }
            .setCancelable(false)
            .show()
    }
}
