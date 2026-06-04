/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.runestone.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.runestone.app.importer.SafStorageBrowser

class GameFolderBrowserScreen(private val context: Context) {

    fun create(
        roots: List<SafStorageBrowser.StorageRoot>,
        currentFolder: SafStorageBrowser.Folder?,
        folders: List<SafStorageBrowser.Folder>,
        canNavigateUp: Boolean,
        onBack: () -> Unit,
        onUp: () -> Unit,
        onOpenRoot: (SafStorageBrowser.StorageRoot) -> Unit,
        onOpenFolder: (SafStorageBrowser.Folder) -> Unit,
        onImportFolder: (SafStorageBrowser.Folder) -> Unit,
        onGrantStorage: () -> Unit,
    ): LinearLayout {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)
        }
        root.addView(topBar(onBack))

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(24))
        }
        val scroll = ScrollView(context).apply {
            overScrollMode = ScrollView.OVER_SCROLL_NEVER
            addView(content, ViewGroup.LayoutParams(MATCH, WRAP))
        }
        root.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))

        if (currentFolder == null) {
            content.addView(introPanel())
            content.addView(spacer(12))
            roots.forEach { storageRoot ->
                content.addView(rootRow(storageRoot) { onOpenRoot(storageRoot) })
                content.addView(spacer(8))
            }
            if (roots.isEmpty()) {
                content.addView(emptyNote("No storage locations authorized yet."))
                content.addView(spacer(12))
            }
            content.addView(actionButton("ADD STORAGE LOCATION", "Authorize another folder with Android") { onGrantStorage() })
            return root
        }

        content.addView(folderHeader(currentFolder, canNavigateUp, onUp))
        content.addView(spacer(10))
        content.addView(actionButton("USE THIS FOLDER", importDetail(currentFolder), accent = true) {
            onImportFolder(currentFolder)
        })
        content.addView(spacer(12))

        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val search = EditText(context).apply {
            hint = "Search folders"
            setHintTextColor(MUTED_DIM)
            setTextColor(TEXT)
            textSize = 14f
            isSingleLine = true
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = glassBg(dp(12))
        }
        content.addView(search)
        content.addView(spacer(10))
        content.addView(list)
        content.addView(spacer(14))
        content.addView(actionButton("ADD STORAGE LOCATION", "Authorize another folder with Android") { onGrantStorage() })

        fun render(query: String) {
            list.removeAllViews()
            val visible = folders.filter { it.name.contains(query, ignoreCase = true) }
            if (visible.isEmpty()) {
                list.addView(emptyNote(if (query.isBlank()) "No child folders here." else "No matching folders."))
            } else {
                visible.forEach { folder ->
                    list.addView(folderRow(folder) { onOpenFolder(folder) })
                    list.addView(spacer(8))
                }
            }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = render(s?.toString().orEmpty())
            override fun afterTextChanged(s: Editable?) = Unit
        })
        render("")
        return root
    }

    private fun topBar(onBack: () -> Unit): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(10), dp(12), dp(10))
        setBackgroundColor(Color.rgb(15, 14, 18))
        addView(smallButton("Back", onBack), LinearLayout.LayoutParams(dp(84), WRAP))
        addView(TextView(context).apply {
            text = "Add Game"
            setTextColor(TEXT)
            textSize = 19f
            gravity = Gravity.CENTER
            typeface = Typeface.create("serif", Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, WRAP, 1f))
        addView(View(context), LinearLayout.LayoutParams(dp(84), 1))
    }

    private fun introPanel(): TextView = TextView(context).apply {
        text = "Choose a storage location, then open the folder that contains your game files. Runestone highlights folders that look importable."
        setTextColor(MUTED)
        textSize = 12.5f
        setLineSpacing(2f, 1f)
        setPadding(dp(12), dp(11), dp(12), dp(11))
        background = glassBg(dp(12))
    }

    private fun folderHeader(folder: SafStorageBrowser.Folder, canNavigateUp: Boolean, onUp: () -> Unit): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            if (canNavigateUp) {
                addView(smallButton("UP", onUp), LinearLayout.LayoutParams(dp(64), WRAP))
                addView(spacer(width = 10))
            }
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = folder.name
                    setTextColor(TEXT)
                    textSize = 18f
                    typeface = Typeface.DEFAULT_BOLD
                })
                addView(TextView(context).apply {
                    text = "${folder.childFolderCount} folders, ${folder.fileCount} files"
                    setTextColor(MUTED)
                    textSize = 12f
                })
            }, LinearLayout.LayoutParams(0, WRAP, 1f))
        }

    private fun rootRow(root: SafStorageBrowser.StorageRoot, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = "${root.name}\nAuthorized storage location"
            setTextColor(TEXT)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(11), dp(12), dp(11))
            background = glassBg(dp(12))
            setOnClickListener { animTap(this); onClick() }
            makeLiquid(this)
        }

    private fun folderRow(folder: SafStorageBrowser.Folder, onClick: () -> Unit): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = glassBg(dp(12), accent = folder.gameHint != null)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = folder.name
                    setTextColor(TEXT)
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                })
                addView(TextView(context).apply {
                    text = folder.gameHint ?: "${folder.childFolderCount} folders, ${folder.fileCount} files"
                    setTextColor(if (folder.gameHint != null) ACCENT else MUTED)
                    textSize = 11f
                })
            }, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(TextView(context).apply {
                text = "OPEN"
                setTextColor(ACCENT)
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
            })
            setOnClickListener { animTap(this); onClick() }
            makeLiquid(this)
        }

    private fun importDetail(folder: SafStorageBrowser.Folder): String =
        folder.gameHint?.let { "Import as $it" } ?: "Run engine detection and import"

    private fun actionButton(label: String, detail: String, accent: Boolean = false, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = "$label\n$detail"
            setTextColor(TEXT)
            textSize = 12.5f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), dp(9), dp(10), dp(9))
            background = glassBg(dp(12), accent = accent)
            setOnClickListener { animTap(this); onClick() }
            makeLiquid(this)
        }

    private fun smallButton(label: String, onClick: () -> Unit): TextView = TextView(context).apply {
        text = label
        setTextColor(ACCENT)
        textSize = 14f
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(8), dp(6), dp(8), dp(6))
        background = glassBg(dp(8), accent = true)
        setOnClickListener { animTap(this); onClick() }
        makeLiquid(this)
    }

    private fun emptyNote(message: String): TextView = TextView(context).apply {
        text = message
        setTextColor(MUTED)
        textSize = 13f
        gravity = Gravity.CENTER
        setPadding(dp(8), dp(18), dp(8), dp(18))
    }

    private fun makeLiquid(view: View) { if (Theme.isReducedMotion(context)) return
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(1.04f).scaleY(1.04f).setDuration(100).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(OvershootInterpolator(1.5f)).start()
            }
            false
        }
    }

    private fun animTap(view: View) { if (Theme.isReducedMotion(context)) return
        view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(60).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(180).setInterpolator(OvershootInterpolator(1.5f)).start()
        }.start()
    }

    private fun glassBg(radius: Int, accent: Boolean = false): GradientDrawable = GradientDrawable().apply {
        setColor(Color.argb(200, if (accent) 50 else 22, if (accent) 40 else 20, if (accent) 30 else 26))
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), Color.argb(if (accent) 85 else 45, if (accent) 180 else 100, if (accent) 140 else 90, if (accent) 100 else 80))
    }

    private fun spacer(height: Int = 0, width: Int = 0): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(width), dp(height))
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        val TEXT = Color.rgb(232, 229, 220)
        val MUTED = Color.rgb(140, 130, 112)
        val MUTED_DIM = Color.rgb(120, 112, 104)
        val ACCENT: Int get() = Theme.active.accent
    }
}
