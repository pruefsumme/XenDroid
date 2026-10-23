package xendroid.compose.core

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.util.Log
import xendroid.compose.Utils
import java.io.File

/**
 * Where the library is allowed to look for games.
 *
 * By default we don't ask for All Files Access. The catch is that the native core needs
 * real filesystem paths, and without that permission the only real paths an app can read
 * on shared storage are its own app-specific dirs (Android/data/<pkg>/files on each
 * volume). So out of the box every games folder lives in one of those.
 *
 * Internal storage uses <storage_root>/Games, i.e. inside the same dir the app's
 * DocumentsProvider exposes. That way the folder shows up as "XenDroid" in the system Files
 * app and games can be copied in on the phone itself, no PC needed. SD cards / USB volumes
 * get their own Android/data dir, which is reachable over USB from a PC.
 *
 * Heads up: Android wipes these dirs when the app is uninstalled, games included.
 *
 * If the user opted into All Files Access ([AllFilesAccess]), the whole of each storage
 * volume is added on top, so games can stay wherever they already are.
 */
object GamesFolder {
    private const val TAG = "GamesFolder"
    private const val DIR_NAME = "Games"

    /** A volume we can keep games on, e.g. internal storage or an SD card. */
    data class Location(val label: String, val dir: File)

    /**
     * All usable games folders, internal storage first. The dirs are created on the way so
     * the user can find them right away in a file manager.
     */
    fun locations(context: Context): List<Location> {
        val result = mutableListOf<Location>()

        val internal = File(Utils.get_storage_root_path(), DIR_NAME)
        if (ensureDir(internal)) result += Location("App folder (internal storage)", internal)

        // getExternalFilesDirs() returns one entry per mounted volume, primary first.
        // The primary one is already covered above (same volume, different subdir), and an
        // entry can be null while a card is being mounted/ejected, so skip both.
        val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        context.getExternalFilesDirs("compose").drop(1).filterNotNull().forEach { base ->
            if (Environment.getExternalStorageState(base) != Environment.MEDIA_MOUNTED) {
                return@forEach
            }
            val dir = File(base, DIR_NAME)
            if (!ensureDir(dir)) return@forEach
            val label = runCatching { sm?.getStorageVolume(base)?.getDescription(context) }
                .getOrNull() ?: "external storage"
            result += Location("App folder ($label)", dir)
        }

        if (AllFilesAccess.isGranted()) result += wholeVolumes(context, sm)
        return result
    }

    /** Root of every mounted volume. Only readable with All Files Access. */
    private fun wholeVolumes(context: Context, sm: StorageManager?): List<Location> {
        val result = mutableListOf(
            Location("Internal storage", Environment.getExternalStorageDirectory()),
        )
        // StorageVolume.directory is API 30, which isGranted() already implies.
        if (sm == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return result
        for (volume in sm.storageVolumes) {
            if (volume.isPrimary) continue
            if (volume.state != Environment.MEDIA_MOUNTED &&
                volume.state != Environment.MEDIA_MOUNTED_READ_ONLY
            ) {
                continue
            }
            val dir = volume.directory ?: continue
            result += Location(volume.getDescription(context) ?: dir.name, dir)
        }
        return result
    }

    /** The folder new users start with. */
    fun default(context: Context): File? = locations(context).firstOrNull()?.dir

    /**
     * The folder the library should scan, given what's saved in the prefs.
     * Falls back to the default folder when nothing is saved yet, or when the saved
     * path points somewhere we can't read anymore (All Files Access turned off).
     */
    fun resolve(context: Context, savedPath: String?): File? {
        if (!savedPath.isNullOrBlank() && contains(context, savedPath)) {
            val dir = File(savedPath)
            if (dir.isDirectory) return dir
        }
        return default(context)
    }

    /**
     * True when [path] is one of our games folders or somewhere below one.
     * Used to spot a saved path we can no longer read, e.g. Documents/Games after the
     * user turned All Files Access off again in Settings.
     */
    fun contains(context: Context, path: String): Boolean {
        val target = runCatching { File(path).canonicalFile }.getOrNull() ?: return false
        return locations(context).any { loc ->
            val root = loc.dir.canonicalFile
            target == root || target.path.startsWith(root.path + File.separator)
        }
    }

    private fun ensureDir(dir: File): Boolean {
        if (dir.isDirectory || dir.mkdirs()) return true
        Log.w(TAG, "Can't create games folder at ${dir.absolutePath}")
        return false
    }
}
