package com.example.dndsound.core.wheel

import com.example.dndsound.core.model.Mood
import com.example.dndsound.core.model.WheelPoint
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Geometry knobs of the 25-zone wheel. All values relate to the unit circle:
 * the wheel center is (0, 0), the rim is radius 1.
 */
data class WheelConfig(
    /** r below this is the neutral center. */
    val centerRadius: Float = 0.20f,
    /** INNER tier below this radius, OUTER tier above. */
    val tierSplitRadius: Float = 0.60f,
    /** A sector boundary +- this many degrees is a transition band. */
    val transitionHalfWidthDeg: Float = 6f,
    /** How deep into a new zone the marker must go before the zone changes. */
    val zoneHysteresis: Float = 0.03f,
)

/** The 25 wheel zones: neutral center, 8 sectors x 2 tiers, 8 transitions. */
sealed interface WheelZone {

    /** Stable id matching wheel_zones.json, e.g. "happy.vivid_town". */
    val id: String

    /** The calm-less center of the wheel ("neutral" folder). */
    data object Neutral : WheelZone {
        override val id = "neutral"
    }

    /** One tier of one mood sector; id comes from the zone palette. */
    data class Sector(val mood: Mood, val tier: Tier) : WheelZone {
        override val id: String get() = WheelZonePalette.sectorId(mood, tier)
    }

    /** A narrow band along a boundary between two neighbouring sectors. */
    data class Transition(override val id: String, val a: Mood, val b: Mood) : WheelZone
}

/** Tier of a sector: INNER is closer to the center, OUTER is closer to the rim. */
enum class Tier { INNER, OUTER }

/**
 * Pure zone geometry for the mood wheel (docs/CONTENT.md "Зоны"):
 * [zoneAt] classifies a point, [distanceToZone] measures how far a point is
 * from a zone's area (used by fallback and hysteresis), and
 * [zoneAtWithHysteresis] keeps the zone stable when the marker jitters on a
 * boundary so the music does not stutter.
 */
object WheelZones {

    /** Angular half-width of the clear (non-transition) part of a sector. */
    fun sectorClearHalfDeg(cfg: WheelConfig = WheelConfig()): Float =
        SECTOR_HALF_DEG - cfg.transitionHalfWidthDeg

    /**
     * Zone of a wheel point:
     * 1. r < centerRadius -> [WheelZone.Neutral];
     * 2. angle within transitionHalfWidthDeg of a sector boundary -> transition;
     * 3. otherwise the nearest sector, INNER below tierSplitRadius else OUTER.
     */
    fun zoneAt(p: WheelPoint, cfg: WheelConfig = WheelConfig()): WheelZone {
        if (p.radius < cfg.centerRadius) return WheelZone.Neutral
        val theta = fold360(p.angleDeg)
        val boundary = boundaryAt(theta, cfg)
        if (boundary != null) {
            return WheelZonePalette.transitionZone(boundary.first, boundary.second)
                ?: WheelZone.Transition(
                    "transition.${boundary.first.name.lowercase()}_${boundary.second.name.lowercase()}",
                    boundary.first,
                    boundary.second,
                )
        }
        val mood = WheelMath.nearestMood(p.angleDeg)
        val tier = if (p.radius < cfg.tierSplitRadius) Tier.INNER else Tier.OUTER
        return WheelZone.Sector(mood, tier)
    }

    /**
     * Zone accounting for hysteresis: when the raw zone differs from
     * [previous] but the point is still within [WheelConfig.zoneHysteresis]
     * of the previous zone's area, the previous zone wins.
     */
    fun zoneAtWithHysteresis(
        p: WheelPoint,
        previous: WheelZone?,
        cfg: WheelConfig = WheelConfig(),
    ): WheelZone {
        val zone = zoneAt(p, cfg)
        if (previous == null || zone == previous) return zone
        return if (distanceToZone(p, previous, cfg) < cfg.zoneHysteresis) previous else zone
    }

    /**
     * Euclidean distance from a point to a zone's area: the point's polar
     * coordinates are clamped into the zone's angular/radial extents and the
     * distance to the clamped point is returned (0 inside the zone).
     */
    fun distanceToZone(p: WheelPoint, zone: WheelZone, cfg: WheelConfig = WheelConfig()): Float {
        val r = p.radius
        val theta = fold360(p.angleDeg)
        return when (zone) {
            WheelZone.Neutral -> (r - cfg.centerRadius).coerceAtLeast(0f)

            is WheelZone.Sector -> {
                val (lo, hi) = tierRadii(zone.tier, cfg)
                val thetaClamped = zone.mood.angleDeg +
                    signedAngleDiff(theta, zone.mood.angleDeg).coerceIn(-sectorClearHalfDeg(cfg), sectorClearHalfDeg(cfg))
                distance(r, theta, r.coerceIn(lo, hi), thetaClamped)
            }

            is WheelZone.Transition -> {
                val boundary = fold360(boundaryAngleOf(zone.a, zone.b))
                val thetaClamped = boundary +
                    signedAngleDiff(theta, boundary).coerceIn(-cfg.transitionHalfWidthDeg, cfg.transitionHalfWidthDeg)
                distance(r, theta, r.coerceIn(cfg.centerRadius, 1f), thetaClamped)
            }
        }
    }

    /** Radial extent of a tier: INNER spans [centerRadius, tierSplit), OUTER [tierSplit, 1]. */
    fun tierRadii(tier: Tier, cfg: WheelConfig = WheelConfig()): Pair<Float, Float> =
        when (tier) {
            Tier.INNER -> cfg.centerRadius to cfg.tierSplitRadius
            Tier.OUTER -> cfg.tierSplitRadius to 1f
        }

    /**
     * The boundary the angle belongs to, as the pair of neighbouring moods,
     * or null when the angle is outside every transition band.
     */
    fun boundaryAt(thetaDeg: Float, cfg: WheelConfig = WheelConfig()): Pair<Mood, Mood>? =
        BOUNDARIES.firstOrNull { (angle, _, _) ->
            WheelMath.angularDistance(thetaDeg, angle) <= cfg.transitionHalfWidthDeg
        }
            ?.let { (_, a, b) -> a to b }

    /** Midpoint angle between two neighbouring sector centers, in [0, 360). */
    fun boundaryAngleOf(a: Mood, b: Mood): Float {
        val ax = cos(Math.toRadians(a.angleDeg.toDouble()))
        val ay = sin(Math.toRadians(a.angleDeg.toDouble()))
        val bx = cos(Math.toRadians(b.angleDeg.toDouble()))
        val by = sin(Math.toRadians(b.angleDeg.toDouble()))
        return fold360(Math.toDegrees(kotlin.math.atan2(ay + by, ax + bx)).toFloat())
    }

    /** Signed difference a-b folded into (-180, 180]. */
    fun signedAngleDiff(a: Float, b: Float): Float = WheelMath.normalize(a - b)

    /** Angle folded into [0, 360). */
    fun fold360(angleDeg: Float): Float {
        var a = angleDeg % 360f
        if (a < 0f) a += 360f
        return a
    }

    /** Distance between two polar points on the wheel plane. */
    private fun distance(r1: Float, theta1: Float, r2: Float, theta2: Float): Float {
        val dTheta = Math.toRadians(signedAngleDiff(theta1, theta2).toDouble())
        return hypot(r1 * cos(dTheta) - r2, r1 * sin(dTheta)).toFloat()
    }

    /** Boundaries between consecutive Mood.entries (closing funny -> happy). */
    private val BOUNDARIES: List<Triple<Float, Mood, Mood>> = buildList {
        val moods = Mood.entries
        for (i in moods.indices) {
            val a = moods[i]
            val b = moods[(i + 1) % moods.size]
            add(Triple(boundaryAngleOf(a, b), a, b))
        }
    }

    private const val SECTOR_HALF_DEG = 22.5f
}
