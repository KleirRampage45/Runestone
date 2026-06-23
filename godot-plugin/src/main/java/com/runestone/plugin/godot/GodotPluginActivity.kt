package com.runestone.plugin.godot

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast

class GodotPluginActivity : Activity() {

    companion object {
        private const val TAG = "GodotPlugin"
        private const val EXTRA_GAME_PATH = "game_path"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "Godot plugin activity started")

        val gamePath = intent.getStringExtra(EXTRA_GAME_PATH)
            ?: intent.data?.getQueryParameter("path")
            ?: run {
                Toast.makeText(this, "No game path provided", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

        Toast.makeText(this, "Godot engine: $gamePath", Toast.LENGTH_LONG).show()
        // TODO: Launch Godot native activity with game path
        finish()
    }
}
