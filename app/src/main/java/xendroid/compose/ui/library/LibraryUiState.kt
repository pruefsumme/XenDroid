package xendroid.compose.ui.library

import xendroid.compose.data.Game

/** Drives the library screen. NoFolder vs empty Loaded are deliberately distinct
 *  so the UI shows "pick a folder" vs "folder has no games". */
sealed interface LibraryUiState {
    /** Not even the default games folder exists (storage unmounted / full) -> retry. */
    data object NoFolder : LibraryUiState
    /** Scanning the chosen folder. */
    data object Loading : LibraryUiState
    /** Scan finished. [games] may be empty (folder has no recognized titles). */
    data class Loaded(val games: List<Game>) : LibraryUiState
    /** The saved games dir can't be read: All Files Access turned off, SD card pulled... */
    data object PermissionLost : LibraryUiState
    /** Device has no Vulkan -> emulator cannot run; hard gate. */
    data object NoVulkan : LibraryUiState
    /** Scan/load failed unexpectedly -> show the message + a Retry. */
    data class Error(val message: String) : LibraryUiState
}
