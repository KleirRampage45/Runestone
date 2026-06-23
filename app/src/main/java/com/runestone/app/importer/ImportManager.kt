package com.runestone.app.importer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.runestone.app.util.AppScope
import com.runestone.app.workspace.SaveManager
import com.runestone.app.workspace.WorkspaceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ImportManager(
    private val activity: Activity,
    private val workspaceManager: WorkspaceManager,
    private val saveManager: SaveManager,
    private val callbacks: Callbacks,
) {
    companion object {
        private const val TAG = "ImportManager"
        const val REQUEST_IMPORT_FOLDER = 9001
        const val REQUEST_COVER_IMAGE = 9002
        const val REQUEST_PATCH_ZIP = 9003
        const val REQUEST_SAVE_EXPORT_ZIP = 9004
        const val REQUEST_SAVE_IMPORT_ZIP = 9005
    }

    interface Callbacks {
        fun showOverlay(panel: android.view.View, dismissOnBgClick: Boolean)
        fun dismissOverlay(onDismissed: () -> Unit = {})
        fun showHome()
        fun showManageFiles(storageName: String? = null)
        fun refreshGames()
        fun showRtpDownloadDialog(storageName: String, missing: List<com.runestone.app.rtp.RtpPack>)
        fun showImportProgress(message: String)
        fun getGames(): List<WorkspaceManager.GameInfo>
        fun getSettingsDefaultGameFolder(): String
    }

    var importMessage: String? = null
    var activeImportProgressView: com.runestone.app.ui.ImportProgressView? = null
    var pendingImportStorage: String? = null
    var pendingCoverStorage: String? = null
    var pendingCoverCallback: ((String) -> Unit)? = null
    var pendingPatchStorage: String? = null
    var pendingPatchCallback: ((String) -> Unit)? = null
    var pendingSaveExportStorage: String? = null
    var pendingSaveImportStorage: String? = null

    val importBrowserStack = mutableListOf<SafStorageBrowser.Folder>()
    var importBrowserShowLocations = false

    fun startFolderImport(requestedName: String? = null) {
        Log.i(TAG, "startFolderImport: requestedName=$requestedName")
        importMessage = null
        pendingImportStorage = requestedName
        importBrowserStack.clear()
        importBrowserShowLocations = false
        showGameFolderBrowser()
    }

    fun requestStorageAccess() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        }
        activity.startActivityForResult(intent, REQUEST_IMPORT_FOLDER)
    }

    fun showGameFolderBrowser() {
        val browser = SafStorageBrowser(activity.contentResolver)
        val roots = browser.listRoots()
        if (!importBrowserShowLocations && importBrowserStack.isEmpty() && roots.isNotEmpty()) {
            val preferred = roots.firstOrNull { it.name.equals(callbacks.getSettingsDefaultGameFolder(), ignoreCase = true) }
                ?: roots.first()
            importBrowserStack += browser.describeFolder(preferred.documentUri)
        }
        val current = importBrowserStack.lastOrNull()
        val entries = current?.let { runCatching { browser.listEntries(it.uri) }.getOrDefault(emptyList()) } ?: emptyList()
        val screen = com.runestone.app.ui.GameFolderBrowserScreen(activity).create(
            roots = roots,
            currentFolder = current,
            entries = entries,
            pathSegments = importBrowserStack.map { it.name },
            canNavigateUp = importBrowserStack.size > 1,
            onBack = {
                if (current == null) {
                    importBrowserShowLocations = false
                    callbacks.dismissOverlay()
                } else if (importBrowserStack.size > 1) {
                    importBrowserStack.removeAt(importBrowserStack.lastIndex)
                    showGameFolderBrowser()
                } else {
                    importBrowserShowLocations = true
                    importBrowserStack.clear()
                    showGameFolderBrowser()
                }
            },
            onUp = {
                if (importBrowserStack.size > 1) {
                    importBrowserStack.removeAt(importBrowserStack.lastIndex)
                    showGameFolderBrowser()
                } else {
                    importBrowserShowLocations = true
                    importBrowserStack.clear()
                    showGameFolderBrowser()
                }
            },
            onOpenRoot = { storageRoot ->
                importBrowserShowLocations = false
                importBrowserStack.clear()
                importBrowserStack += browser.describeFolder(storageRoot.documentUri)
                showGameFolderBrowser()
            },
            onOpenFolder = { folder ->
                importBrowserStack += folder
                showGameFolderBrowser()
            },
            onImportFolder = { folder -> importSelectedFolder(folder.uri) },
            onGrantStorage = { requestStorageAccess() },
        )
        callbacks.showOverlay(screen, true)
    }

    private fun importSelectedFolder(folderUri: Uri) {
        if (pendingImportStorage != null) {
            val backedUp = saveManager.syncFromActive(pendingImportStorage!!)
            Log.i(TAG, "Backed up $backedUp saves for $pendingImportStorage before import")
        }

        callbacks.showImportProgress("Importing game")
        Log.i(TAG, "importSelectedFolder: progress screen shown, starting coroutine uri=$folderUri")

        AppScope.io.launch {
            val importer = SafGameImporter(
                contentResolver = activity.contentResolver,
                workspaceManager = workspaceManager,
                rtpManager = com.runestone.app.rtp.RtpManager(activity),
                onProgress = { msg ->
                    activity.runOnUiThread {
                        Log.d(TAG, "import progress: $msg")
                        val pv = activeImportProgressView
                        if (pv != null) {
                            when {
                                msg.startsWith("Copying game") -> { pv.phaseView.text = msg; pv.fileView.text = ""; pv.countView.text = "" }
                                msg.startsWith("Copying ") -> pv.fileView.text = msg.removePrefix("Copying ")
                                else -> { pv.phaseView.text = msg; pv.fileView.text = "" }
                            }
                        }
                        importMessage = msg
                    }
                },
            )
            val result = importer.importTree(folderUri, pendingImportStorage)
            Log.i(TAG, "import finished: $result")

            withContext(Dispatchers.Main) {
                pendingImportStorage = null
                importBrowserStack.clear()
                when (result) {
                    is SafImportResult.Success -> {
                        Log.i(TAG, "Import OK: ${result.storageName} (${result.fileCount} files)")
                        importMessage = null
                        saveManager.restoreToActive(result.storageName)
                        activeImportProgressView = null
                        workspaceManager.invalidateGameScanCache()
                        callbacks.refreshGames()
                        callbacks.dismissOverlay {
                            callbacks.showHome()
                            if (result.missingRtps.isNotEmpty()) {
                                callbacks.showRtpDownloadDialog(result.storageName, result.missingRtps)
                            }
                        }
                    }
                    is SafImportResult.Failure -> {
                        Log.e(TAG, "Import FAILED: ${result.reason}")
                        val pv = activeImportProgressView
                        if (pv != null) { pv.phaseView.text = "[FAIL] Import failed"; pv.fileView.text = result.reason; pv.countView.text = "" }
                        importMessage = "Import failed: ${result.reason}"
                        android.os.Handler(activity.mainLooper).postDelayed({
                            callbacks.refreshGames(); activeImportProgressView = null
                            callbacks.dismissOverlay { callbacks.showManageFiles() }
                        }, 3000)
                    }
                }
            }
        }
    }

    fun handleCoverImageResult(resultCode: Int, data: Intent?) {
        val callback = pendingCoverCallback
        pendingCoverCallback = null
        val storageName = pendingCoverStorage
        pendingCoverStorage = null

        if (resultCode != Activity.RESULT_OK || data?.data == null || storageName == null) return

        val uri = data.data!!
        val coverDir = File(activity.filesDir, "game_covers").apply { mkdirs() }
        val destFile = File(coverDir, "${storageName}.jpg")
        try {
            val inputStream = activity.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Unable to open selected cover image")
            inputStream.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            callback?.invoke(destFile.absolutePath)
            activity.runOnUiThread { callbacks.showHome() }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save cover image", e)
            activity.runOnUiThread {
                Toast.makeText(activity, "Failed to set cover image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun handlePatchZipResult(resultCode: Int, data: Intent?) {
        val callback = pendingPatchCallback
        pendingPatchCallback = null
        val storageName = pendingPatchStorage
        pendingPatchStorage = null

        if (resultCode != Activity.RESULT_OK || data?.data == null || storageName == null) return

        val uri = data.data!!
        val patchDir = File(activity.cacheDir, "patch_zips").apply { mkdirs() }
        val destFile = File(patchDir, "${storageName}_patch_${System.currentTimeMillis()}.zip")
        try {
            val inputStream = activity.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Unable to open ZIP file")
            inputStream.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            callback?.invoke(destFile.absolutePath)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to copy patch ZIP", e)
            callback?.invoke("")
            activity.runOnUiThread {
                Toast.makeText(activity, "Failed to read patch file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun handleSaveExportResult(resultCode: Int, data: Intent?) {
        val storageName = pendingSaveExportStorage
        pendingSaveExportStorage = null

        if (resultCode != Activity.RESULT_OK || data?.data == null || storageName == null) return

        val uri = data.data!!
        try {
            val outputStream = activity.contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("Unable to open export destination")
            val count = outputStream.use { output ->
                saveManager.exportAllSavesZip(storageName, output)
            }
            Toast.makeText(activity, "Exported $count save files", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to export saves", e)
            Toast.makeText(activity, "Failed to export saves", Toast.LENGTH_SHORT).show()
        }
    }

    fun handleSaveImportResult(resultCode: Int, data: Intent?) {
        val storageName = pendingSaveImportStorage
        pendingSaveImportStorage = null

        if (resultCode != Activity.RESULT_OK || data?.data == null || storageName == null) return

        val uri = data.data!!
        val importDir = File(activity.cacheDir, "save_import_zips").apply { mkdirs() }
        val destFile = File(importDir, "${storageName}_saves_${System.currentTimeMillis()}.zip")
        try {
            val inputStream = activity.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Unable to open selected save ZIP")
            inputStream.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            val count = saveManager.importSavesZip(storageName, destFile)
            if (count > 0) {
                Toast.makeText(activity, "Imported $count save files", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(activity, "No save files found in the selected archive", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to import saves", e)
            Toast.makeText(activity, "Failed to import saves", Toast.LENGTH_SHORT).show()
        } finally {
            destFile.delete()
        }
    }

    fun handleImportFolderResult(resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) {
            Log.w(TAG, "onActivityResult: result not OK")
            return
        }
        val treeUri = data?.data ?: run {
            Log.w(TAG, "onActivityResult: no data URI"); return
        }
        Log.i(TAG, "onActivityResult: treeUri=$treeUri pending=$pendingImportStorage")

        runCatching { activity.contentResolver.takePersistableUriPermission(
            treeUri, data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        ) }
        val browser = SafStorageBrowser(activity.contentResolver)
        importBrowserStack.clear()
        runCatching {
            importBrowserStack += browser.describeFolder(browser.rootFromTreeUri(treeUri).documentUri)
        }.onFailure { error ->
            Log.w(TAG, "Could not open authorized storage location", error)
            Toast.makeText(activity, "Could not open that storage location", Toast.LENGTH_SHORT).show()
        }
        showGameFolderBrowser()
    }
}
