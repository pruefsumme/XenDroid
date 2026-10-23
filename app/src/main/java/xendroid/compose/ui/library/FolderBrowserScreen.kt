package xendroid.compose.ui.library

import android.content.Context
import xendroid.compose.core.AllFilesAccess
import xendroid.compose.core.GamesFolder
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File

/** A browsable storage root: internal storage or a mounted removable volume. */
private data class StorageRoot(val label: String, val dir: File)

/**
 * Small java.io.File directory browser over the places [GamesFolder] says we can read:
 * the app's own games folders, and the whole storage if the user opted into All Files
 * Access. Lists sub-directories only (sorted, hidden skipped) and lets the user confirm
 * the current one as the games folder.
 *
 * There's a "volumes" level on top whenever there's more than one root, or when the
 * "Other folders on this phone" opt-in can still be offered.
 *
 * Two modes (the caller picks via which callback it passes):
 *  - folder-pick (default): [onFolderChosen] non-null -> a "Use this folder" button
 *    confirms the current directory; only sub-dirs are listed.
 *  - file-pick: [onFileChosen] non-null -> regular files are ALSO listed and tapping one
 *    returns its absolute path (used by "Install content / DLC" to pick a package).
 *
 * The user can't navigate above a root: going "up" from a root goes back to the volume
 * list or closes the browser, never to the parent dir (which we couldn't read anyway).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderBrowserScreen(
    onFolderChosen: ((path: String) -> Unit)? = null,
    onFileChosen: ((path: String) -> Unit)? = null,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current

    // Granting All Files Access happens in the Settings app and returns no result, so
    // re-check whenever we come back to the foreground. The roots depend on it.
    var allFilesGranted by remember { mutableStateOf(AllFilesAccess.isGranted()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) allFilesGranted = AllFilesAccess.isGranted()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val roots = remember(allFilesGranted) { enumerateStorageRoots(context) }

    // Offer the opt-in only where it exists (API 30+) and isn't granted yet.
    val offerAllFiles = AllFilesAccess.isSupported && !allFilesGranted
    // The volume list is skipped when there's just one place to go and nothing to offer.
    val hasVolumeList = roots.size > 1 || offerAllFiles

    // null = the volume-list level.
    var current by remember {
        mutableStateOf<File?>(if (hasVolumeList) null else roots.firstOrNull()?.dir)
    }

    // Subdirectories of the current dir: directories only, no hidden, sorted by name.
    val subDirs = remember(current?.absolutePath) {
        current?.listFiles()
            ?.filter { it.isDirectory && !it.isHidden }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }

    // File-pick mode only: regular files in the current dir, sorted, hidden skipped.
    val files = remember(current?.absolutePath) {
        val dir = current
        if (onFileChosen == null || dir == null) emptyList()
        else dir.listFiles()
            ?.filter { it.isFile && !it.isHidden }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }

    val atVolumeList = current == null
    val atVolumeRoot = current != null &&
        roots.any { it.dir.absolutePath == current!!.absolutePath }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        current?.absolutePath ?: "Storage",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            val dir = current
                            when {
                                dir == null -> onCancel()
                                atVolumeRoot ->
                                    if (hasVolumeList) current = null else onCancel()
                                dir.parentFile != null -> current = dir.parentFile
                                else -> onCancel()
                            }
                        },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription =
                                if (atVolumeList || (atVolumeRoot && !hasVolumeList))
                                    "Cancel" else "Up",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Action row: confirm the CURRENT directory (folder-pick only), or cancel.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                if (onFolderChosen != null && !atVolumeList) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { onFolderChosen(current!!.absolutePath) }) {
                        Text("Use this folder")
                    }
                }
            }

            if (atVolumeList) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(roots, key = { it.dir.absolutePath }) { root ->
                        ListItem(
                            headlineContent = {
                                Text(root.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                Text(
                                    root.dir.absolutePath,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            trailingContent = {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Open",
                                )
                            },
                            modifier = Modifier.clickable { current = root.dir },
                        )
                    }
                    if (offerAllFiles) {
                        item(key = "all-files") {
                            ListItem(
                                headlineContent = { Text("Other folders on this phone") },
                                supportingContent = {
                                    Text(
                                        "e.g. Documents/Games. Needs \"All files access\", " +
                                            "which lets the app read all your storage.",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                },
                                modifier = Modifier.clickable {
                                    AllFilesAccess.requestAccess(context)
                                },
                            )
                        }
                    }
                }
            } else if (subDirs.isEmpty() && files.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(24.dp)) {
                    Text(
                        if (onFolderChosen != null)
                            "No sub-folders here. Use \"Use this folder\" to pick this directory."
                        else
                            "Nothing here. Go up and pick another folder.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(subDirs, key = { it.absolutePath }) { dir ->
                        ListItem(
                            headlineContent = {
                                Text(dir.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            trailingContent = {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Open",
                                )
                            },
                            modifier = Modifier.clickable { current = dir },
                        )
                    }
                    if (onFileChosen != null) {
                        items(files, key = { it.absolutePath }) { file ->
                            ListItem(
                                headlineContent = {
                                    Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                modifier = Modifier.clickable { onFileChosen(file.absolutePath) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Our own games folders, plus whole volumes once All Files Access is granted. */
private fun enumerateStorageRoots(context: Context): List<StorageRoot> =
    GamesFolder.locations(context).map { StorageRoot(it.label, it.dir) }
