package com.winlator.cmod.ui.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.winlator.cmod.MainActivity
import com.winlator.cmod.R
import com.winlator.cmod.ShortcutsFragment
import com.winlator.cmod.container.Container
import com.winlator.cmod.container.ContainerManager
import com.winlator.cmod.ui.ThemedAlertHost
import com.winlator.cmod.ui.filemanager.FileManagerController
import java.io.File
import java.util.Locale

// HEXA: "+ Adicionar jogo" opens the phone's own file picker (the same one used to attach a file
// anywhere on Android). The picked .exe is resolved to its real path, because Wine needs a path,
// and added to the library; tapping its cover runs it full screen, without the Windows desktop.

private val GAME_EXTENSIONS = listOf(".exe", ".msi", ".bat")

@Composable
internal fun rememberHexaGamePicker(activity: MainActivity?): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && activity != null) addPickedGame(activity, uri)
    }
    return remember(activity, launcher) {
        {
            if (activity != null) {
                if (!hasAllFilesAccess()) {
                    requestAllFilesAccess(activity)
                } else {
                    try {
                        launcher.launch(arrayOf("*/*"))
                    } catch (_: Exception) {
                        Toast.makeText(activity, "Não foi possível abrir o seletor de arquivos.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}

private fun hasAllFilesAccess(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

private fun requestAllFilesAccess(activity: MainActivity) {
    Toast.makeText(
        activity,
        "Ative \"Acesso a todos os arquivos\" para o HEXA e toque em Adicionar jogo de novo.",
        Toast.LENGTH_LONG
    ).show()
    try {
        activity.startActivity(
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + activity.packageName))
        )
    } catch (_: Exception) {
        try {
            activity.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        } catch (_: Exception) {
        }
    }
}

private fun addPickedGame(activity: MainActivity, uri: Uri) {
    val file = resolvePickedFile(activity, uri)
    if (file == null) {
        Toast.makeText(
            activity,
            "Não consegui abrir esse arquivo. Escolha o jogo pela memória do celular ou cartão SD (não pela nuvem).",
            Toast.LENGTH_LONG
        ).show()
        return
    }
    val name = file.name.lowercase(Locale.ENGLISH)
    if (GAME_EXTENSIONS.none { name.endsWith(it) }) {
        Toast.makeText(activity, "Escolha o arquivo .exe do jogo.", Toast.LENGTH_LONG).show()
        return
    }
    val containers = ContainerManager(activity).containers
    if (containers.isNullOrEmpty()) {
        Toast.makeText(activity, "Crie um container primeiro.", Toast.LENGTH_LONG).show()
        return
    }
    if (containers.size == 1) {
        addGameToContainer(activity, file, containers[0])
    } else {
        ThemedAlertHost.actions(activity, "Escolha o container", containers.map { container ->
            ThemedAlertHost.ActionItem(container.name, Runnable { addGameToContainer(activity, file, container) })
        })
    }
}

// Reuses the file manager's "Add game" (drive mapping, .desktop entry, icon) as it is.
private fun addGameToContainer(activity: MainActivity, file: File, container: Container) {
    try {
        val controller = FileManagerController(activity) { }
        val method = FileManagerController::class.java.getDeclaredMethod("createShortcutDirectly", File::class.java, Container::class.java)
        method.isAccessible = true
        method.invoke(controller, file, container)
    } catch (e: Exception) {
        Toast.makeText(activity, "Não foi possível adicionar o jogo: " + (e.cause?.message ?: e.message), Toast.LENGTH_LONG).show()
        return
    }
    val fragment = activity.supportFragmentManager.findFragmentById(R.id.FLFragmentContainer)
    if (fragment is ShortcutsFragment) fragment.loadShortcutsList()
}

internal fun resolvePickedFile(context: Context, uri: Uri): File? {
    try {
        if (uri.scheme == "file") return uri.path?.let { File(it) }?.takeIf { it.isFile }
        if (DocumentsContract.isDocumentUri(context, uri)) {
            val id = DocumentsContract.getDocumentId(uri)
            when (uri.authority) {
                "com.android.externalstorage.documents" -> {
                    val volume = id.substringBefore(':')
                    val relative = id.substringAfter(':', "")
                    val base = if (volume.equals("primary", true)) Environment.getExternalStorageDirectory() else File("/storage/$volume")
                    File(base, relative).takeIf { it.isFile }?.let { return it }
                }
                "com.android.providers.downloads.documents" -> {
                    if (id.startsWith("raw:")) File(id.removePrefix("raw:")).takeIf { it.isFile }?.let { return it }
                    val number = id.substringAfter(':').toLongOrNull()
                    if (number != null) {
                        queryDataPath(context, MediaStore.Files.getContentUri("external"), "_id=?", arrayOf(number.toString()))?.let { return it }
                    }
                }
            }
        }
        queryDataPath(context, uri, null, null)?.let { return it }
    } catch (_: Exception) {
    }
    // Last resort: a file with the same name in Downloads.
    val name = displayName(context, uri) ?: return null
    val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    return File(downloads, name).takeIf { it.isFile }
}

private fun queryDataPath(context: Context, uri: Uri, selection: String?, args: Array<String>?): File? = try {
    context.contentResolver.query(uri, arrayOf("_data"), selection, args, null)?.use { cursor ->
        val index = cursor.getColumnIndex("_data")
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index)?.let { File(it) }?.takeIf { it.isFile } else null
    }
} catch (_: Exception) {
    null
}

private fun displayName(context: Context, uri: Uri): String? = try {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }
} catch (_: Exception) {
    null
}
