package com.example.dndsound.core.library

import com.example.dndsound.core.model.AmbienceLayer
import com.example.dndsound.core.model.Environment
import com.example.dndsound.core.model.EnvironmentCategory
import com.example.dndsound.core.model.LayerKind
import com.example.dndsound.core.model.MusicMode
import com.example.dndsound.core.model.OneShot
import com.example.dndsound.core.model.RandomSpec
import com.example.dndsound.core.model.SoundFile
import com.example.dndsound.core.model.SoundCategory
import com.example.dndsound.core.model.Track
import com.example.dndsound.core.model.Weather
import com.example.dndsound.core.model.WheelPoint
import com.example.dndsound.core.repo.Library
import com.example.dndsound.core.wheel.FolderZone
import com.example.dndsound.core.wheel.WheelZonePalette

/** Why a scanned path produced no playable content (or needs user attention). */
enum class WarningReason {
    UNKNOWN_MUSIC_FOLDER,
    DAY_WITHOUT_NIGHT,
    NIGHT_WITHOUT_DAY,
    BASE_MISSING,
    MALFORMED_META,
    UNKNOWN_WEATHER_FOLDER,
}

data class IndexWarning(val path: String, val reason: WarningReason)

data class IndexResult(val library: Library, val warnings: List<IndexWarning>)

/**
 * Turns a flat list of scanned files into a playable [Library] using the
 * folder/name conventions from docs/CONTENT.md. Pure logic: no Android APIs,
 * no I/O — the SAF scanner and the Room cache feed it [IndexedFile]s.
 *
 * Conventions applied here:
 *  - music/[battle/]<zone folders>/<file>  -> [Track] (zone from wheel_zones.json,
 *    unknown folders import as unplaced tracks with position = null)
 *  - ambience/<env>/{base*, cover*}        -> [Environment] base loop(s)
 *  - ambience/<env>/layers|spots/<file>    -> LOOP / RANDOM layers (variants grouped)
 *  - sounds/[<category>/]<file>            -> [OneShot] groups (variants grouped)
 *  - weather/<rain|storm|wind|snow>/<file> -> weather loop candidates
 *  - meta.json in a directory applies to that directory's files only
 */
object LibraryIndexBuilder {

    private const val META_FILE = "meta.json"
    private const val BATTLE = "battle"
    private const val BASE = "base"
    private const val BASE_DAY = "base_day"
    private const val BASE_NIGHT = "base_night"

    fun build(files: List<IndexedFile>): IndexResult {
        val warnings = mutableListOf<IndexWarning>()
        val metas = collectMetas(files, warnings)
        val audio = files.filter { it.isAudio }.sortedBy { it.relativePath.lowercase() }

        val library = Library(
            tracks = buildMusic(audio, metas, warnings),
            environments = buildEnvironments(files, metas, warnings),
            oneShots = buildOneShots(audio, metas),
            weatherLoops = buildWeather(audio, metas, warnings),
        )
        return IndexResult(library, warnings)
    }

    /** meta.json files -> directory -> parsed meta; malformed ones get a warning. */
    private fun collectMetas(
        files: List<IndexedFile>,
        warnings: MutableList<IndexWarning>,
    ): Map<String, MetaJson> {
        val metas = mutableMapOf<String, MetaJson>()
        for (file in files) {
            if (!file.name.equals(META_FILE, ignoreCase = true) || file.metaText == null) continue
            val parsed = parseMetaJson(file.metaText)
            if (parsed == null) {
                warnings += IndexWarning(file.relativePath, WarningReason.MALFORMED_META)
            } else {
                metas[file.directory] = parsed
            }
        }
        return metas
    }

    // ---------------------------------------------------------------- music

    private fun buildMusic(
        audio: List<IndexedFile>,
        metas: Map<String, MetaJson>,
        warnings: MutableList<IndexWarning>,
    ): List<Track> {
        val tracks = mutableListOf<Track>()
        val warnedDirs = mutableSetOf<String>()
        for (file in audio) {
            if (!file.directory.equals("music", ignoreCase = true) &&
                !file.directory.startsWith("music/", ignoreCase = true)
            ) {
                continue
            }
            val meta = metas[file.directory]
            val match = WheelZonePalette.zoneForFolder(file.directory)
            if (match == null) {
                // Unknown folder: the track still imports but stays unplaced
                // (position = null, never plays until the user picks a zone).
                if (warnedDirs.add(file.directory)) {
                    warnings += IndexWarning(file.directory, WarningReason.UNKNOWN_MUSIC_FOLDER)
                }
            }
            tracks += file.toTrack(match, meta)
        }
        return tracks
    }

    private fun IndexedFile.toTrack(match: FolderZone?, meta: MetaJson?): Track {
        // Position priority: manual (Room overrides) -> meta.json x/y ->
        // meta.json zone -> folder-derived default scatter.
        val metaPosition = meta?.wheel?.let { w ->
            if (w.x != null && w.y != null) WheelPoint(w.x!!, w.y!!).clamped() else null
        }
        val metaZonePosition = meta?.zone
            ?.let { id -> WheelZonePalette.zoneById(id) }
            ?.let { zone -> WheelZonePalette.defaultPositionFor(zone, relativePath) }
        val folderPosition = match?.let { WheelZonePalette.defaultPositionFor(it.zone, relativePath) }
        val mode = when {
            meta?.mode?.equals(BATTLE, ignoreCase = true) == true -> MusicMode.BATTLE
            meta?.mode != null -> MusicMode.EXPLORATION
            match != null -> match.mode
            directory.split('/').getOrNull(1)?.equals(BATTLE, ignoreCase = true) == true -> MusicMode.BATTLE
            else -> MusicMode.EXPLORATION
        }
        return Track(
            id = relativePath,
            title = meta?.title ?: LibraryRules.displayTitle(name),
            uri = uri,
            durationMs = durationMs ?: 0L,
            mode = mode,
            position = metaPosition ?: metaZonePosition ?: folderPosition,
            gainDb = meta?.gainDb ?: 0f,
        )
    }

    // ----------------------------------------------------------- environments

    private fun buildEnvironments(
        files: List<IndexedFile>,
        metas: Map<String, MetaJson>,
        warnings: MutableList<IndexWarning>,
    ): List<Environment> {
        val envDirs = files.map { it.directory }
            .filter { it.startsWith("ambience/") }
            .map { "ambience/" + it.removePrefix("ambience/").substringBefore('/') }
            .distinct()
            .sorted()
        return envDirs.map { envDir -> buildEnvironment(envDir, files, metas, warnings) }
            .filterNotNull()
    }

    private fun buildEnvironment(
        envDir: String,
        files: List<IndexedFile>,
        metas: Map<String, MetaJson>,
        warnings: MutableList<IndexWarning>,
    ): Environment? {
        val inEnv = files.filter { it.directory == envDir || it.directory.startsWith("$envDir/") }
        val rootAudio = inEnv.filter { it.directory == envDir && it.isAudio }
        val meta = metas[envDir]

        val byBase = rootAudio.groupBy { LibraryRules.baseName(it.name) }
        val baseDay = byBase[BASE_DAY]?.maxByOrNull { it.sizeBytes }
        val baseNight = byBase[BASE_NIGHT]?.maxByOrNull { it.sizeBytes }
        val plainBase = byBase[BASE]?.maxByOrNull { it.sizeBytes }

        var baseDayUri: String? = null
        var baseNightUri: String? = null
        when {
            baseDay != null && baseNight != null -> {
                baseDayUri = baseDay.uri
                baseNightUri = baseNight.uri
            }
            baseDay != null -> {
                baseDayUri = baseDay.uri
                warnings += IndexWarning(envDir, WarningReason.DAY_WITHOUT_NIGHT)
            }
            baseNight != null -> {
                baseDayUri = baseNight.uri
                warnings += IndexWarning(envDir, WarningReason.NIGHT_WITHOUT_DAY)
            }
            plainBase != null -> baseDayUri = plainBase.uri
        }

        val layers = mutableListOf<AmbienceLayer>()
        layers += rootLoopsAsLayers(envDir, byBase, metas)
        layers += groupedLayers("$envDir/layers", inEnv, metas, envDir)
        layers += spotsAsLayers("$envDir/spots", inEnv, metas, envDir)

        val hasBase = baseDayUri != null
        if (!hasBase && layers.isEmpty()) return null // nothing playable in this folder
        if (!hasBase) warnings += IndexWarning(envDir, WarningReason.BASE_MISSING)

        return Environment(
            id = envDir,
            name = meta?.title ?: prettifyName(envDir.substringAfter('/')),
            category = meta?.category
                ?.let { LibraryRules.environmentCategory(it) }
                ?: EnvironmentCategory.CUSTOM,
            coverUri = inEnv.firstOrNull {
                it.directory == envDir && LibraryRules.isImageFile(it.name) &&
                    LibraryRules.baseName(it.name).equals("cover", ignoreCase = true)
            }?.uri,
            baseDayUri = baseDayUri,
            baseNightUri = baseNightUri,
            layers = layers,
            seamless = meta?.seamless ?: true,
            baseDurationMs = (baseDay ?: baseNight ?: plainBase)?.durationMs ?: 0L,
        )
    }

    /** Audio dropped directly into the env root (not base/pair) acts as loop layers. */
    private fun rootLoopsAsLayers(
        envDir: String,
        byBase: Map<String, List<IndexedFile>>,
        metas: Map<String, MetaJson>,
    ): List<AmbienceLayer> {
        val reserved = setOf(BASE, BASE_DAY, BASE_NIGHT)
        return byBase.filterKeys { it !in reserved }
            .map { (base, variants) ->
                AmbienceLayer(
                    id = "$envDir/$base",
                    name = LibraryRules.displayTitle(variants.first().name),
                    kind = LayerKind.LOOP,
                    uris = variants.map { it.uri },
                    baseGainDb = metas[envDir]?.gainDb ?: 0f,
                )
            }
    }

    /** layers/ subtree: files grouped by base name into manual LOOP layers. */
    private fun groupedLayers(
        layerDir: String,
        inEnv: List<IndexedFile>,
        metas: Map<String, MetaJson>,
        envDir: String,
    ): List<AmbienceLayer> {
        val layerFiles = inEnv.filter {
            (it.directory == layerDir || it.directory.startsWith("$layerDir/")) && it.isAudio
        }
        if (layerFiles.isEmpty()) return emptyList()
        return LibraryRules.groupVariants(layerFiles.map { it.name }).map { (base, names) ->
            val first = layerFiles.first { it.name == names.first() }
            AmbienceLayer(
                id = "$layerDir/$base",
                name = LibraryRules.displayTitle(first.name),
                kind = LayerKind.LOOP,
                uris = names.map { name -> layerFiles.first { it.name == name }.uri },
                baseGainDb = metas[first.directory]?.gainDb ?: metas[envDir]?.gainDb ?: 0f,
            )
        }
    }

    /** spots/ subtree: groups become RANDOM layers with the default or meta spec. */
    private fun spotsAsLayers(
        spotDir: String,
        inEnv: List<IndexedFile>,
        metas: Map<String, MetaJson>,
        envDir: String,
    ): List<AmbienceLayer> {
        val spotFiles = inEnv.filter {
            (it.directory == spotDir || it.directory.startsWith("$spotDir/")) && it.isAudio
        }
        if (spotFiles.isEmpty()) return emptyList()
        val envSpec = metas[envDir]?.toRandomSpec()
        return LibraryRules.groupVariants(spotFiles.map { it.name }).map { (base, names) ->
            val first = spotFiles.first { it.name == names.first() }
            AmbienceLayer(
                id = "$spotDir/$base",
                name = LibraryRules.displayTitle(first.name),
                kind = LayerKind.RANDOM,
                uris = names.map { name -> spotFiles.first { it.name == name }.uri },
                baseGainDb = metas[first.directory]?.gainDb ?: metas[envDir]?.gainDb ?: 0f,
                random = metas[first.directory]?.toRandomSpec() ?: envSpec ?: RandomSpec(),
            )
        }
    }

    // -------------------------------------------------------------- one-shots

    private fun buildOneShots(
        audio: List<IndexedFile>,
        metas: Map<String, MetaJson>,
    ): List<OneShot> {
        val soundFiles = audio.filter { it.directory == "sounds" || it.directory.startsWith("sounds/") }
        val byDir = soundFiles.groupBy { it.directory }
        val shots = mutableListOf<OneShot>()
        for ((dir, filesInDir) in byDir) {
            val meta = metas[dir]
            val category = dir.split('/').getOrNull(1)
                ?.let { LibraryRules.soundCategory(it) }
                ?: SoundCategory.CUSTOM
            val groups = LibraryRules.groupVariants(filesInDir.map { it.name })
            for ((base, names) in groups) {
                val first = filesInDir.first { it.name == names.first() }
                shots += OneShot(
                    id = "$dir/$base",
                    // A lone group inherits the meta title; multi-group folders
                    // fall back to file names so every button keeps its own name.
                    name = (if (groups.size == 1) meta?.title else null)
                        ?: LibraryRules.displayTitle(first.name),
                    category = category,
                    variants = names.map { name ->
                        val file = filesInDir.first { it.name == name }
                        SoundFile(
                            id = file.relativePath,
                            title = LibraryRules.displayTitle(name),
                            uri = file.uri,
                            durationMs = file.durationMs ?: 0L,
                        )
                    },
                    iconKey = meta?.icon,
                    gainDb = meta?.gainDb ?: 0f,
                    random = meta?.toRandomSpec(),
                )
            }
        }
        return shots.sortedBy { it.id }
    }

    // ---------------------------------------------------------------- weather

    private fun buildWeather(
        audio: List<IndexedFile>,
        metas: Map<String, MetaJson>,
        warnings: MutableList<IndexWarning>,
    ): Map<Weather, List<SoundFile>> {
        val weatherFiles = audio.filter {
            it.directory.startsWith("weather/") && it.directory.split('/').size >= 2
        }
        val result = mutableMapOf<Weather, List<SoundFile>>()
        val warnedDirs = mutableSetOf<String>()
        for ((dir, filesInDir) in weatherFiles.groupBy { it.directory }) {
            val weather = LibraryRules.weatherFromFolder(dir.split('/')[1])
            if (weather == null) {
                if (warnedDirs.add(dir)) {
                    warnings += IndexWarning(dir, WarningReason.UNKNOWN_WEATHER_FOLDER)
                }
                continue
            }
            val meta = metas[dir]
            result[weather] = filesInDir.map {
                SoundFile(
                    id = it.relativePath,
                    title = LibraryRules.displayTitle(it.name),
                    uri = it.uri,
                    durationMs = it.durationMs ?: 0L,
                    gainDb = meta?.gainDb ?: 0f,
                )
            }
        }
        return result
    }

    /** "dark_forest" -> "Dark forest". */
    private fun prettifyName(raw: String): String =
        raw.replace('_', ' ').trim().replaceFirstChar { it.uppercase() }
}
