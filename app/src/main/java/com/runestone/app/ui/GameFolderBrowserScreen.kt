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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.runestone.app.importer.SafStorageBrowser
import java.util.concurrent.Executors

class GameFolderBrowserScreen(private val context: Context) {

    fun create(
        roots: List<SafStorageBrowser.StorageRoot>,
        currentFolder: SafStorageBrowser.Folder?,
        entries: List<SafStorageBrowser.BrowserEntry>,
        pathSegments: List<String>,
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
        root.addView(topBar(onBack, onGrantStorage))

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(14))
        }
        root.addView(content, LinearLayout.LayoutParams(MATCH, 0, 1f))

        if (currentFolder == null) {
            content.addView(locationPanel("Storage Locations", "Authorize a folder once, then browse it here."))
            content.addView(spacer(8))
            val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            content.addView(list, LinearLayout.LayoutParams(MATCH, 0, 1f))
            if (roots.isEmpty()) {
                list.addView(emptyNote("No folder access yet. Add a storage location to begin."))
            } else {
                roots.forEach { storageRoot ->
                    list.addView(rootRow(storageRoot) { onOpenRoot(storageRoot) })
                }
            }
            return root
        }

        content.addView(breadcrumbPanel(pathSegments, "${currentFolder.childFolderCount + currentFolder.fileCount} items"))
        content.addView(spacer(7))

        val commandRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        commandRow.addView(compactButton(if (canNavigateUp) "UP" else "LOCATIONS", onUp), LinearLayout.LayoutParams(dp(96), dp(36)))
        commandRow.addView(spacer(width = 8))
        commandRow.addView(compactButton("SELECT FOLDER", { onImportFolder(currentFolder) }, accent = currentFolder.gameHint != null), LinearLayout.LayoutParams(0, dp(36), 1f))
        content.addView(commandRow)
        content.addView(spacer(8))

        val search = searchBox()
        content.addView(search)
        content.addView(spacer(8))

        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(context).apply {
            overScrollMode = ScrollView.OVER_SCROLL_NEVER
            addView(list, ViewGroup.LayoutParams(MATCH, WRAP))
        }
        content.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))

        fun render(query: String) {
            list.removeAllViews()
            val visible = entries.filter {
                query.isBlank() ||
                    it.name.contains(query, ignoreCase = true) ||
                    it.gameHint?.contains(query, ignoreCase = true) == true
            }
            if (visible.isEmpty()) {
                list.addView(emptyNote(if (query.isBlank()) "This folder is empty." else "No matching files or folders."))
                return
            }
            visible.forEach { entry ->
                list.addView(entryRow(entry) {
                    if (entry.isDirectory) {
                        onOpenFolder(
                            SafStorageBrowser.Folder(
                                uri = entry.uri,
                                name = entry.name,
                                childFolderCount = entry.childFolderCount,
                                fileCount = entry.fileCount,
                                gameHint = entry.gameHint,
                            ),
                        )
                    }
                })
                list.addView(separator())
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

    private fun topBar(onBack: () -> Unit, onGrantStorage: () -> Unit): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(9), dp(12), dp(9))
            setBackgroundColor(withAlpha(Theme.PANEL_BG, 226))
            addView(compactButton("BACK", onBack), LinearLayout.LayoutParams(dp(72), dp(38)))
            addView(TextView(context).apply {
                text = "Add Game"
                setTextColor(TEXT)
                textSize = 18f
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(compactButton("ADD", onGrantStorage), LinearLayout.LayoutParams(dp(72), dp(38)))
        }

    private fun locationPanel(title: String, detail: String): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = glassBg(dp(12), alpha = 155)
            addView(TextView(context).apply {
                text = title
                setTextColor(TEXT)
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
            })
            addView(TextView(context).apply {
                text = detail
                setTextColor(MUTED)
                textSize = 12f
                setPadding(0, dp(2), 0, 0)
            })
        }

    private fun breadcrumbPanel(pathSegments: List<String>, detail: String): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            addView(TextView(context).apply {
                text = "/" + pathSegments.joinToString("/")
                setTextColor(TEXT)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 2
            })
            addView(TextView(context).apply {
                text = detail
                setTextColor(MUTED)
                textSize = 12f
                setPadding(0, dp(3), 0, 0)
            })
        }

    private fun rootRow(root: SafStorageBrowser.StorageRoot, onClick: () -> Unit): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(8))
            addView(entryIcon(FileKind.FOLDER), LinearLayout.LayoutParams(dp(58), dp(48)))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = root.name
                    setTextColor(TEXT)
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                })
                addView(TextView(context).apply {
                    text = "Authorized location"
                    setTextColor(MUTED)
                    textSize = 12f
                    maxLines = 1
                })
            }, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(TextView(context).apply {
                text = ">"
                setTextColor(MUTED)
                textSize = 18f
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dp(26), WRAP))
            setOnClickListener { animTap(this); onClick() }
            makeLiquid(this)
        }

    private fun entryRow(entry: SafStorageBrowser.BrowserEntry, onClick: () -> Unit): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(7), dp(4), dp(7))
            addView(entryIcon(entry), LinearLayout.LayoutParams(dp(58), dp(48)))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = entry.name
                    setTextColor(TEXT)
                    textSize = 16f
                    typeface = if (entry.isDirectory) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    maxLines = 1
                })
                addView(TextView(context).apply {
                    text = entry.gameHint ?: if (entry.isDirectory) "Folder" else fileDescription(entry)
                    setTextColor(if (entry.gameHint != null) ACCENT else MUTED)
                    textSize = 12f
                    maxLines = 1
                })
            }, LinearLayout.LayoutParams(0, WRAP, 1f))
            if (entry.isDirectory) {
                addView(TextView(context).apply {
                    text = ">"
                    setTextColor(MUTED)
                    textSize = 18f
                    gravity = Gravity.CENTER
                }, LinearLayout.LayoutParams(dp(24), WRAP))
                setOnClickListener { animTap(this); onClick() }
                makeLiquid(this)
            }
        }

    private fun searchBox(): EditText = EditText(context).apply {
        hint = "Search files and folders"
        setHintTextColor(MUTED_DIM)
        setTextColor(TEXT)
        textSize = 14f
        isSingleLine = true
        setPadding(dp(12), dp(9), dp(12), dp(9))
        background = glassBg(dp(12), alpha = 188)
    }

    private fun compactButton(label: String, onClick: () -> Unit, accent: Boolean = false): TextView =
        TextView(context).apply {
            text = label
            setTextColor(if (accent) Color.rgb(190, 230, 176) else ACCENT)
            textSize = 11f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            background = glassBg(dp(10), alpha = 154, accent = accent)
            setOnClickListener { animTap(this); onClick() }
            makeLiquid(this)
        }

    private fun emptyNote(message: String): TextView = TextView(context).apply {
        text = message
        setTextColor(MUTED)
        textSize = 13f
        gravity = Gravity.CENTER
        setPadding(dp(8), dp(24), dp(8), dp(24))
    }

    private fun makeLiquid(view: View) {
        if (Theme.isReducedMotion(context)) return
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(1.015f).scaleY(1.015f).setDuration(80).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(160).setInterpolator(OvershootInterpolator(1.2f)).start()
            }
            false
        }
    }

    private fun animTap(view: View) {
        if (Theme.isReducedMotion(context)) return
        view.animate().scaleX(0.98f).scaleY(0.98f).setDuration(45).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(120).setInterpolator(OvershootInterpolator(1.2f)).start()
        }.start()
    }

    private fun glassBg(radius: Int, alpha: Int = 180, accent: Boolean = false): GradientDrawable =
        GradientDrawable().apply {
            setColor(if (accent) Theme.active.accentBg else withAlpha(Theme.PANEL_BG, alpha))
            cornerRadius = radius.toFloat()
            setStroke(dp(1), if (accent) Theme.active.accentStroke else Theme.active.panelStroke)
        }

    private fun separator(): View = View(context).apply {
        setBackgroundColor(Theme.active.panelStroke)
        layoutParams = LinearLayout.LayoutParams(MATCH, dp(1)).apply {
            leftMargin = dp(66)
        }
    }

    private fun entryIcon(entry: SafStorageBrowser.BrowserEntry): View {
        val kind = fileKind(entry)
        if (kind != FileKind.IMAGE || !isDecodableImage(entry.name)) return entryIcon(kind)
        return FrameLayout(context).apply {
            background = glassBg(dp(8), alpha = 120)
            addView(FileIconView(context, kind), FrameLayout.LayoutParams(MATCH, MATCH))
            val image = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                alpha = 0f
            }
            addView(image, FrameLayout.LayoutParams(MATCH, MATCH))
            val expected = entry.uri.toString()
            image.tag = expected
            THUMBNAIL_EXECUTOR.execute {
                val bitmap = decodeThumbnail(entry.uri)
                image.post {
                    if (image.tag == expected && bitmap != null) {
                        image.setImageBitmap(bitmap)
                        image.animate().alpha(1f).setDuration(120).start()
                    }
                }
            }
        }
    }

    private fun entryIcon(kind: FileKind): View = FileIconView(context, kind)

    private fun fileKind(entry: SafStorageBrowser.BrowserEntry): FileKind {
        if (entry.isDirectory) return FileKind.FOLDER
        val name = entry.name.lowercase()
        val mime = entry.mimeType.lowercase()
        return when {
            name.endsWith(".html") || name.endsWith(".htm") || name.endsWith(".css") -> FileKind.WEB
            name.endsWith(".exe") -> FileKind.EXE
            name.endsWith(".dll") || name.endsWith(".so") || name.endsWith(".nexe") || name.endsWith(".wasm") -> FileKind.LIBRARY
            name.endsWith(".js") || name.endsWith(".json") || name.endsWith(".jsonl") || name.endsWith(".xml") || name.endsWith(".rpy") || name.endsWith(".sh") || name.endsWith(".bat") -> FileKind.CODE
            name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".ini") || name.endsWith(".log") || name.endsWith(".vtt") || name.endsWith(".url") -> FileKind.TEXT
            name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z") || name.endsWith(".gz") || name.endsWith(".pak") || name.endsWith(".xp3") -> FileKind.ARCHIVE
            name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp") || name.endsWith(".gif") || name.endsWith(".avif") || name.endsWith(".ico") || name.endsWith(".svg") || mime.startsWith("image/") -> FileKind.IMAGE
            name.endsWith(".mp4") || name.endsWith(".mov") || name.endsWith(".webm") || name.endsWith(".m3u8") || mime.startsWith("video/") -> FileKind.VIDEO
            name.endsWith(".ogg") || name.endsWith(".mp3") || name.endsWith(".wav") || mime.startsWith("audio/") -> FileKind.AUDIO
            name.endsWith(".rpgmvp") || name.endsWith(".rpgmvo") || name.endsWith(".png_") || name.endsWith(".ogg_") || name.endsWith(".rvdata2") || name.endsWith(".lmu") || name.endsWith(".ldb") || name.endsWith(".lmt") || name.endsWith(".rpgproject") || name.endsWith(".data") || name.endsWith(".save") || name.endsWith(".rpgsave") || name.endsWith(".gme") -> FileKind.GAME_DATA
            name.endsWith(".ttf") || name.endsWith(".fon") -> FileKind.FONT
            name.endsWith(".pdf") || name.endsWith(".docx") || name.endsWith(".xlsx") -> FileKind.DOCUMENT
            name.endsWith(".swf") -> FileKind.FLASH
            name.endsWith(".apk") || name.endsWith(".mcpack") -> FileKind.PACKAGE
            else -> FileKind.FILE
        }
    }

    private fun fileDescription(entry: SafStorageBrowser.BrowserEntry): String =
        when (fileKind(entry)) {
            FileKind.WEB -> "Web file"
            FileKind.EXE -> "Windows executable"
            FileKind.LIBRARY -> "Runtime library"
            FileKind.CODE -> "Script/config"
            FileKind.TEXT -> "Text document"
            FileKind.ARCHIVE -> "Archive"
            FileKind.IMAGE -> "Image"
            FileKind.VIDEO -> "Video"
            FileKind.AUDIO -> "Audio"
            FileKind.GAME_DATA -> "Game data"
            FileKind.FONT -> "Font"
            FileKind.DOCUMENT -> "Document"
            FileKind.FLASH -> "Flash file"
            FileKind.PACKAGE -> "Package"
            FileKind.FILE -> entry.mimeType.ifBlank { "File" }
            FileKind.FOLDER -> "Folder"
        }

    private fun isDecodableImage(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp")
    }

    private fun decodeThumbnail(uri: android.net.Uri): Bitmap? =
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val target = dp(64)
            var sample = 1
            while (bounds.outWidth / sample > target * 2 || bounds.outHeight / sample > target * 2) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull()

    private enum class FileKind {
        FOLDER, TEXT, WEB, EXE, LIBRARY, CODE, ARCHIVE, IMAGE, VIDEO, AUDIO, GAME_DATA, FONT, DOCUMENT, FLASH, PACKAGE, FILE
    }

    private inner class FileIconView(context: Context, private val kind: FileKind) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(1.5f)
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            textSize = dp(8.5f)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val r = RectF(dp(7f), dp(7f), w - dp(7f), h - dp(6f))
            when (kind) {
                FileKind.FOLDER -> drawFolder(canvas, r)
                else -> drawFile(canvas, r, kind)
            }
        }

        private fun drawFolder(canvas: Canvas, r: RectF) {
            paint.style = Paint.Style.FILL
            paint.color = tone(0.72f)
            val tab = Path().apply {
                moveTo(r.left, r.top + r.height() * 0.28f)
                lineTo(r.left + r.width() * 0.12f, r.top + r.height() * 0.06f)
                lineTo(r.left + r.width() * 0.46f, r.top + r.height() * 0.06f)
                lineTo(r.left + r.width() * 0.58f, r.top + r.height() * 0.28f)
                lineTo(r.right, r.top + r.height() * 0.28f)
                lineTo(r.right, r.bottom)
                lineTo(r.left, r.bottom)
                close()
            }
            canvas.drawPath(tab, paint)
            stroke.color = Theme.active.accentBright
            canvas.drawPath(tab, stroke)
        }

        private fun drawFile(canvas: Canvas, r: RectF, kind: FileKind) {
            paint.style = Paint.Style.FILL
            paint.color = when (kind) {
                FileKind.EXE -> tone(0.95f)
                FileKind.LIBRARY -> tone(0.84f)
                FileKind.ARCHIVE -> tone(0.76f)
                FileKind.GAME_DATA -> tone(0.68f)
                FileKind.IMAGE, FileKind.VIDEO, FileKind.AUDIO -> tone(0.88f)
                else -> Theme.TEXT
            }
            val path = Path().apply {
                moveTo(r.left, r.top)
                lineTo(r.right - r.width() * 0.24f, r.top)
                lineTo(r.right, r.top + r.height() * 0.24f)
                lineTo(r.right, r.bottom)
                lineTo(r.left, r.bottom)
                close()
            }
            canvas.drawPath(path, paint)
            stroke.color = Theme.active.accentStroke
            canvas.drawPath(path, stroke)
            stroke.color = Color.argb(120, 20, 20, 20)
            canvas.drawLine(r.right - r.width() * 0.24f, r.top, r.right - r.width() * 0.24f, r.top + r.height() * 0.24f, stroke)
            canvas.drawLine(r.right - r.width() * 0.24f, r.top + r.height() * 0.24f, r.right, r.top + r.height() * 0.24f, stroke)
            drawGlyph(canvas, r, kind)
        }

        private fun drawGlyph(canvas: Canvas, r: RectF, kind: FileKind) {
            stroke.color = if (kind == FileKind.TEXT || kind == FileKind.FILE) Color.rgb(70, 70, 70) else Color.WHITE
            paint.color = stroke.color
            paint.style = Paint.Style.FILL
            when (kind) {
                FileKind.EXE -> {
                    canvas.drawRoundRect(RectF(r.left + dp(10f), r.top + dp(14f), r.right - dp(10f), r.bottom - dp(10f)), dp(4f), dp(4f), stroke)
                    canvas.drawCircle(r.centerX(), r.centerY(), dp(3f), paint)
                }
                FileKind.LIBRARY -> {
                    repeat(3) { i ->
                        val x = r.left + dp(13f + i * 9f)
                        canvas.drawLine(x, r.top + dp(17f), x, r.bottom - dp(11f), stroke)
                    }
                    canvas.drawRect(r.left + dp(11f), r.top + dp(20f), r.right - dp(11f), r.bottom - dp(14f), stroke)
                }
                FileKind.IMAGE -> {
                    canvas.drawCircle(r.left + dp(15f), r.top + dp(17f), dp(3f), paint)
                    val mountain = Path().apply {
                        moveTo(r.left + dp(10f), r.bottom - dp(11f))
                        lineTo(r.left + dp(22f), r.top + dp(24f))
                        lineTo(r.left + dp(29f), r.bottom - dp(11f))
                    }
                    canvas.drawPath(mountain, stroke)
                }
                FileKind.VIDEO -> {
                    val play = Path().apply {
                        moveTo(r.left + dp(17f), r.top + dp(17f))
                        lineTo(r.left + dp(17f), r.bottom - dp(12f))
                        lineTo(r.right - dp(13f), r.centerY())
                        close()
                    }
                    canvas.drawPath(play, paint)
                }
                FileKind.AUDIO -> {
                    canvas.drawLine(r.left + dp(16f), r.bottom - dp(13f), r.left + dp(16f), r.top + dp(16f), stroke)
                    canvas.drawLine(r.left + dp(16f), r.top + dp(16f), r.right - dp(12f), r.top + dp(12f), stroke)
                    canvas.drawCircle(r.left + dp(13f), r.bottom - dp(12f), dp(4f), paint)
                }
                FileKind.ARCHIVE -> {
                    repeat(4) { i ->
                        canvas.drawLine(r.left + dp(14f), r.top + dp(13f + i * 6f), r.right - dp(14f), r.top + dp(13f + i * 6f), stroke)
                    }
                }
                FileKind.GAME_DATA -> {
                    canvas.drawCircle(r.centerX(), r.centerY(), dp(8f), stroke)
                    canvas.drawCircle(r.centerX(), r.centerY(), dp(2.5f), paint)
                    canvas.drawLine(r.centerX() - dp(11f), r.centerY(), r.centerX() + dp(11f), r.centerY(), stroke)
                    canvas.drawLine(r.centerX(), r.centerY() - dp(11f), r.centerX(), r.centerY() + dp(11f), stroke)
                }
                FileKind.WEB -> drawLabel(canvas, r, "</>")
                FileKind.CODE -> drawLabel(canvas, r, "{}")
                FileKind.TEXT -> drawLabel(canvas, r, "TXT")
                FileKind.FONT -> drawLabel(canvas, r, "Aa")
                FileKind.DOCUMENT -> drawLabel(canvas, r, "DOC")
                FileKind.FLASH -> drawLabel(canvas, r, "SWF")
                FileKind.PACKAGE -> drawLabel(canvas, r, "APK")
                else -> drawLabel(canvas, r, "FILE")
            }
        }

        private fun drawLabel(canvas: Canvas, r: RectF, label: String) {
            labelPaint.color = if (label == "TXT" || label == "FILE" || label == "DOC") Color.rgb(70, 70, 70) else Color.WHITE
            canvas.drawText(label, r.centerX(), r.centerY() + dp(10f), labelPaint)
        }

        private fun tone(multiplier: Float): Int {
            val accent = Theme.active.accent
            return Color.rgb(
                (Color.red(accent) * multiplier).toInt().coerceIn(0, 255),
                (Color.green(accent) * multiplier).toInt().coerceIn(0, 255),
                (Color.blue(accent) * multiplier).toInt().coerceIn(0, 255),
            )
        }
    }

    private fun spacer(height: Int = 0, width: Int = 0): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(width), dp(height))
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
    private fun dp(value: Float): Float = value * context.resources.displayMetrics.density

    private companion object {
        val THUMBNAIL_EXECUTOR = Executors.newFixedThreadPool(2)
        val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        val TEXT: Int get() = Theme.TEXT
        val MUTED: Int get() = Theme.MUTED
        val MUTED_DIM: Int get() = Theme.MUTED_DIM
        val ACCENT: Int get() = Theme.active.accent

        fun withAlpha(color: Int, alpha: Int): Int =
            Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }
}
