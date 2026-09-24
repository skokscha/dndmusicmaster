package com.example.dndsound.core.library

import com.example.dndsound.core.model.EnvironmentCategory
import com.example.dndsound.core.model.SoundCategory
import com.example.dndsound.core.model.Weather

/**
 * File-name and folder-convention rules for the user's library
 * (see docs/CONTENT.md). Pure string logic, shared by the SAF scanner.
 */
object LibraryRules {

    val AUDIO_EXTENSIONS = setOf("ogg", "oga", "opus", "mp3", "flac", "wav", "m4a")

    /** Only used for optional environment covers (cover.jpg/png/webp). */
    val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    private val SUFFIX_REGEX = Regex("_\\d{1,3}$")

    fun isAudioFile(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS

    fun isImageFile(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS

    /** File name without extension: "goblin_01.ogg" -> "goblin_01". */
    fun withoutExtension(name: String): String =
        name.substringBeforeLast('.')

    /** Base name with the numeric variant suffix stripped: "goblin_01" -> "goblin". */
    fun baseName(name: String): String =
        withoutExtension(name).replace(SUFFIX_REGEX, "")

    /** Display title: base name with underscores as spaces, trimmed. */
    fun displayTitle(fileName: String): String =
        baseName(fileName).replace('_', ' ').trim()

    /**
     * Groups file names into variants by base name. Values keep the original
     * names sorted, so "_01" stays the first element.
     */
    fun groupVariants(fileNames: List<String>): Map<String, List<String>> =
        fileNames.filter { isAudioFile(it) }
            .groupBy { baseName(it) }
            .mapValues { (_, files) -> files.sorted() }

    private val ENVIRONMENT_CATEGORIES = EnvironmentCategory.entries
        .map { it.name.lowercase() to it }
        .toMap()

    private val SOUND_CATEGORIES = SoundCategory.entries
        .map { it.name.lowercase() to it }
        .toMap()

    /** Folder name to environment category; unknown folders become CUSTOM. */
    fun environmentCategory(folder: String): EnvironmentCategory =
        ENVIRONMENT_CATEGORIES[folder.lowercase()] ?: EnvironmentCategory.CUSTOM

    /** Folder name to one-shot category; unknown folders become CUSTOM. */
    fun soundCategory(folder: String): SoundCategory =
        SOUND_CATEGORIES[folder.lowercase()] ?: SoundCategory.CUSTOM

    /** Weather subfolder name ("rain") to Weather; NONE is not a folder name. */
    fun weatherFromFolder(folder: String): Weather? =
        Weather.entries.firstOrNull {
            it != Weather.NONE && it.name.equals(folder, ignoreCase = true)
        }
}
