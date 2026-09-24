package com.example.dndsound.core.wheel

import com.example.dndsound.core.model.Mood
import com.example.dndsound.core.model.MusicMode
import com.example.dndsound.core.model.WheelPoint
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.cos
import kotlin.math.sin

@Serializable
private data class TierDef(val id: String, val folder: String)

@Serializable
private data class SectorDef(val mood: String, val color: String, val inner: TierDef, val outer: TierDef)

@Serializable
private data class TransitionDef(val id: String, val folder: String, val color: String, val between: List<String>)

@Serializable
private data class NeutralDef(val id: String, val folder: String, val color: String)

@Serializable
private data class ZoneFile(
    val neutral: NeutralDef,
    val sectors: List<SectorDef>,
    val transitions: List<TransitionDef>,
)

/** Folder name -> zone with the mode implied by the folder path. */
data class FolderZone(val zone: WheelZone, val mode: MusicMode)

/**
 * Loads wheel_zones.json from :core resources — the source of truth for zone
 * ids, folder names and colors — and offers the lookups the rest of the app
 * needs: zone <-> folder mapping, default (deterministic) track positions and
 * the 16 color anchors for the wheel gradient.
 */
object WheelZonePalette {

    private val json = Json { ignoreUnknownKeys = true }

    private val file: ZoneFile by lazy {
        val text = WheelZonePalette::class.java.classLoader
            ?.getResourceAsStream(RESOURCE)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: error("$RESOURCE not found on the classpath")
        json.decodeFromString<ZoneFile>(text)
    }

    private val sectorDefs: List<Pair<Mood, SectorDef>> by lazy {
        file.sectors.map { def ->
            val mood = Mood.entries.firstOrNull { it.name.equals(def.mood, ignoreCase = true) }
                ?: error("wheel_zones.json: unknown mood ${def.mood}")
            mood to def
        }
    }

    private val transitionDefs: List<Triple<Set<Mood>, Mood, TransitionDef>> by lazy {
        file.transitions.map { def ->
            val moods = def.between.map { name ->
                Mood.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
                    ?: error("wheel_zones.json: unknown mood $name in transition ${def.id}")
            }
            Triple(setOf(moods[0], moods[1]), moods[0], def)
        }
    }

    val neutralColor: Int get() = parseColor(file.neutral.color)

    /** Every zone id defined by the palette (25 entries). */
    val zoneIds: Set<String> by lazy {
        buildSet {
            add(file.neutral.id)
            file.sectors.forEach { add(it.inner.id); add(it.outer.id) }
            file.transitions.forEach { add(it.id) }
        }
    }

    /** Zone id of a sector tier, e.g. "happy.vivid_town". */
    fun sectorId(mood: Mood, tier: Tier): String {
        val def = sectorDefs.firstOrNull { it.first == mood }?.second
        return when {
            def == null -> "${mood.name.lowercase()}.${tier.name.lowercase()}"
            tier == Tier.INNER -> def.inner.id
            else -> def.outer.id
        }
    }

    /** Zone id -> zone; inverse of [WheelZone.id]. */
    fun zoneById(id: String): WheelZone? {
        if (id.equals(file.neutral.id, ignoreCase = true)) return WheelZone.Neutral
        sectorDefs.forEach { (mood, def) ->
            if (def.inner.id.equals(id, ignoreCase = true)) return WheelZone.Sector(mood, Tier.INNER)
            if (def.outer.id.equals(id, ignoreCase = true)) return WheelZone.Sector(mood, Tier.OUTER)
        }
        transitionDefs.forEach { (_, _, def) ->
            if (def.id.equals(id, ignoreCase = true)) {
                val (a, b) = def.between.map {
                    Mood.entries.first { m -> m.name.equals(it, ignoreCase = true) }
                }
                return WheelZone.Transition(def.id, a, b)
            }
        }
        return null
    }

    /** Transition zone for a boundary pair (order-independent), or null. */
    fun transitionZone(a: Mood, b: Mood): WheelZone.Transition? =
        transitionDefs.firstOrNull { it.first == setOf(a, b) }
            ?.let { (_, first, def) -> WheelZone.Transition(def.id, first, if (first == a) b else a) }

    /** Anchor color of a sector (both tiers share it; the radius mix mutes INNER). */
    fun sectorColor(mood: Mood): Int =
        parseColor(sectorDefs.first { it.first == mood }.second.color)

    /** Transition anchor color; falls back to the average of the two sectors. */
    fun transitionColor(a: Mood, b: Mood): Int {
        val def = transitionDefs.firstOrNull { it.first == setOf(a, b) }?.third
            ?: return averageColor(sectorColor(a), sectorColor(b))
        return parseColor(def.color)
    }

    /**
     * The 16 color anchors: 8 sector centers + 8 boundary (transition) colors,
     * sorted by angle in [0, 360). Feeds [wheelColorAt].
     */
    fun colorAnchors(): List<ColorAnchor> = buildList {
        sectorDefs.forEach { (mood, _) ->
            add(ColorAnchor(WheelZones.fold360(mood.angleDeg), sectorColor(mood)))
        }
        transitionDefs.forEach { (pair, a, def) ->
            val b = pair.first { it != a }
            add(ColorAnchor(WheelZones.fold360(WheelZones.boundaryAngleOf(a, b)), parseColor(def.color)))
        }
    }.sortedBy { it.angleDeg }

    // ------------------------------------------------------------ folders

    /** Folder path (relative to music/) for a zone, e.g. "happy/vivid-town". */
    fun folderPath(zone: WheelZone): String = when (zone) {
        WheelZone.Neutral -> file.neutral.folder
        is WheelZone.Sector -> {
            val def = sectorDefs.first { it.first == zone.mood }.second
            val tier = if (zone.tier == Tier.INNER) def.inner else def.outer
            "${zone.mood.name.lowercase()}/${tier.folder}"
        }
        is WheelZone.Transition ->
            "transitions/" + transitionDefs.first { it.first == setOf(zone.a, zone.b) }.third.folder
    }

    /**
     * Zone for a library directory ("music/happy/vivid-town"). Rules:
     *  - "battle" as the first segment switches to BATTLE mode;
     *  - a mood folder without a tier subfolder is the OUTER tier;
     *  - unknown folders return null (the track stays unplaced).
     */
    fun zoneForFolder(relativeDir: String): FolderZone? {
        val segments = relativeDir.split('/').filter { it.isNotBlank() }
        val musicIndex = segments.indexOfFirst { it.equals("music", ignoreCase = true) }
        if (musicIndex == -1) return null
        val rest = segments.drop(musicIndex + 1)
        var mode = MusicMode.EXPLORATION
        val path = if (rest.firstOrNull()?.equals(BATTLE, ignoreCase = true) == true) {
            mode = MusicMode.BATTLE
            rest.drop(1)
        } else {
            rest
        }
        val head = path.getOrNull(0) ?: return null
        if (normalize(head) == normalize(file.neutral.folder)) return FolderZone(WheelZone.Neutral, mode)
        if (normalize(head) == "transitions") {
            val name = path.getOrNull(1) ?: return null
            return transitionByAnyName(name)?.let { FolderZone(it, mode) }
        }
        val mood = Mood.entries.firstOrNull { it.name.equals(normalize(head), ignoreCase = true) }
        if (mood != null) {
            val def = sectorDefs.first { it.first == mood }.second
            val tierFolder = path.getOrNull(1)
            return when {
                tierFolder == null -> FolderZone(WheelZone.Sector(mood, Tier.OUTER), mode)
                normalize(tierFolder) == normalize(def.inner.folder) ->
                    FolderZone(WheelZone.Sector(mood, Tier.INNER), mode)
                normalize(tierFolder) == normalize(def.outer.folder) ->
                    FolderZone(WheelZone.Sector(mood, Tier.OUTER), mode)
                else -> null
            }
        }
        return transitionByAnyName(head)?.let { FolderZone(it, mode) }
    }

    /** Transition zone matched by id or folder name ("tragic_fight", "tragic-fight", "Tragic fight"). */
    private fun transitionByAnyName(name: String): WheelZone? =
        transitionDefs.firstOrNull { (_, _, def) ->
            def.id.equals(name, ignoreCase = true) || normalize(def.folder) == normalize(name)
        }?.let { (_, a, def) ->
            val b = transitionDefs.first { it.third == def }.first.first { it != a }
            WheelZone.Transition(def.id, a, b)
        }

    // -------------------------------------------------- default positions

    /**
     * Deterministic "scatter" position for a track inside its zone: the same
     * seed (usually the relative path) always produces the same point, and the
     * point is guaranteed to lie inside the zone (property-tested).
     */
    fun defaultPositionFor(zone: WheelZone, seed: String, cfg: WheelConfig = WheelConfig()): WheelPoint {
        val u1 = fraction(hashOf(seed))
        val u2 = fraction(hashOf("${seed}#radius"))
        return when (zone) {
            WheelZone.Neutral -> polar(
                angleDeg = u1 * 360f,
                radius = 0.02f + u2 * (0.16f - 0.02f),
            )

            is WheelZone.Sector -> {
                val (lo, hi) = when (zone.tier) {
                    Tier.INNER -> 0.28f to 0.52f
                    Tier.OUTER -> 0.68f to 0.92f
                }
                polar(
                    angleDeg = zone.mood.angleDeg + (u1 * 2f - 1f) * SCATTER_HALF_DEG,
                    radius = lo + u2 * (hi - lo),
                )
            }

            is WheelZone.Transition -> polar(
                angleDeg = WheelZones.fold360(WheelZones.boundaryAngleOf(zone.a, zone.b)) + (u1 * 2f - 1f) * 4.5f,
                radius = 0.30f + u2 * (0.90f - 0.30f),
            )
        }
    }

    private fun polar(angleDeg: Float, radius: Float): WheelPoint {
        val rad = Math.toRadians(angleDeg.toDouble())
        return WheelPoint(
            x = radius * cos(rad).toFloat(),
            y = radius * sin(rad).toFloat(),
        )
    }

    private fun hashOf(seed: String): Long {
        var h = -0x340d631b7bdddcdbL // FNV-1a 64 offset basis
        seed.lowercase().forEach { c ->
            h = h xor c.code.toLong()
            h *= 0x100000001b3L
        }
        return h
    }

    private fun fraction(h: Long): Float =
        ((h ushr 11) and 0x1FFFFFFFL).toFloat() / 0x20000000L.toFloat()

    // ------------------------------------------------------------ helpers

    private fun normalize(segment: String): String =
        segment.trim().lowercase().replace('-', '_').replace(' ', '_')

    private fun parseColor(hex: String): Int =
        (hex.removePrefix("#").toLong(16) or 0xFF000000L).toInt()

    private fun averageColor(a: Int, b: Int): Int {
        val r = (((a shr 16) and 0xFF) + ((b shr 16) and 0xFF)) / 2
        val g = (((a shr 8) and 0xFF) + ((b shr 8) and 0xFF)) / 2
        val bl = ((a and 0xFF) + (b and 0xFF)) / 2
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }

    private const val RESOURCE = "wheel_zones.json"
    private const val BATTLE = "battle"
    private const val SCATTER_HALF_DEG = 14.5f
}
