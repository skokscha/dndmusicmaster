package com.example.dndsound.core.library

import com.example.dndsound.core.model.EnvironmentCategory
import com.example.dndsound.core.model.LayerKind
import com.example.dndsound.core.model.Mood
import com.example.dndsound.core.model.MusicMode
import com.example.dndsound.core.model.RandomSpec
import com.example.dndsound.core.model.SoundCategory
import com.example.dndsound.core.model.WheelPoint
import com.example.dndsound.core.model.Weather
import com.example.dndsound.core.wheel.WheelMath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LibraryIndexBuilderTest {

    private fun audio(
        path: String,
        durationMs: Long? = 5_000L,
    ) = IndexedFile(
        relativePath = path,
        uri = "content://test/$path",
        sizeBytes = 100L,
        lastModifiedMs = 1L,
        durationMs = durationMs,
    )

    private fun metaFile(dir: String, json: String) = IndexedFile(
        relativePath = "$dir/meta.json",
        uri = "content://test/$dir/meta.json",
        metaText = json,
    )

    private fun build(vararg files: IndexedFile) = LibraryIndexBuilder.build(files.toList())

    @Test
    fun `mood folder tracks get wheel position and exploration mode`() {
        val result = build(audio("music/happy/march.ogg"), audio("music/happy/tune_02.ogg"))
        assertEquals(2, result.library.tracks.size)
        val march = result.library.tracks.first { it.id == "music/happy/march.ogg" }
        assertEquals("march", march.title)
        assertEquals(MusicMode.EXPLORATION, march.mode)
        assertEquals(Mood.HAPPY, WheelMath.nearestMood(march.position.angleDeg))
        assertEquals(0.7f, march.position.radius, 1e-4f)
        assertEquals(5_000L, march.durationMs)
    }

    @Test
    fun `battle folder tracks use battle mode and center position`() {
        val result = build(audio("music/battle/charge.ogg"))
        val track = result.library.tracks.single()
        assertEquals(MusicMode.BATTLE, track.mode)
        assertEquals(WheelPoint.CENTER, track.position)
    }

    @Test
    fun `calm folder tracks sit inside calm zone`() {
        val track = build(audio("music/calm/quiet.ogg")).library.tracks.single()
        assertTrue(track.position.isCalm())
    }

    @Test
    fun `unknown music folder is skipped with a warning`() {
        val result = build(audio("music/oops/theme.ogg"), audio("music/sad/blue.ogg"))
        assertEquals(1, result.library.tracks.size)
        val warning = result.warnings.single()
        assertEquals("music/oops", warning.path)
        assertEquals(WarningReason.UNKNOWN_MUSIC_FOLDER, warning.reason)
    }

    @Test
    fun `meta json overrides title position mode and gain`() {
        val result = build(
            metaFile("music/epic", """{"title":"Epic main","wheel":{"x":0.2,"y":0.3},"gainDb":-3.0,"mode":"battle"}"""),
            audio("music/epic/theme.ogg"),
        )
        val track = result.library.tracks.single()
        assertEquals("Epic main", track.title)
        assertEquals(0.2f, track.position.x, 1e-6f)
        assertEquals(0.3f, track.position.y, 1e-6f)
        assertEquals(-3f, track.gainDb, 1e-6f)
        assertEquals(MusicMode.BATTLE, track.mode)
    }

    @Test
    fun `meta mood override moves the track to another mood`() {
        val result = build(
            metaFile("music/happy", """{"mood":"creepy"}"""),
            audio("music/happy/odd.ogg"),
        )
        val track = result.library.tracks.single()
        assertEquals(Mood.CREEPY, WheelMath.nearestMood(track.position.angleDeg))
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `malformed meta json adds warning but indexing continues`() {
        val result = build(
            metaFile("music/happy", "{not json"),
            audio("music/happy/tune.ogg"),
        )
        assertEquals(1, result.library.tracks.size)
        assertEquals(WarningReason.MALFORMED_META, result.warnings.single().reason)
    }

    @Test
    fun `day night pair builds base uris and layers`() {
        val result = build(
            audio("ambience/forest/base_day.ogg", 60_000),
            audio("ambience/forest/base_night.ogg", 60_000),
            audio("ambience/forest/cover.png"),
            metaFile("ambience/forest", """{"title":"Forest","category":"nature"}"""),
            audio("ambience/forest/spots/birds_01.ogg", 3_000),
            audio("ambience/forest/spots/birds_02.ogg", 3_100),
            audio("ambience/forest/layers/stream.ogg", 30_000),
        )
        assertTrue(result.warnings.isEmpty())
        val env = result.library.environments.single()
        assertEquals("Forest", env.name)
        assertEquals(EnvironmentCategory.NATURE, env.category)
        assertEquals("content://test/ambience/forest/cover.png", env.coverUri)
        assertEquals("content://test/ambience/forest/base_day.ogg", env.baseDayUri)
        assertEquals("content://test/ambience/forest/base_night.ogg", env.baseNightUri)

        assertEquals(2, env.layers.size)
        val birds = env.layers.first { it.kind == LayerKind.RANDOM }
        assertEquals(
            listOf(
                "content://test/ambience/forest/spots/birds_01.ogg",
                "content://test/ambience/forest/spots/birds_02.ogg",
            ),
            birds.uris,
        )
        assertEquals(RandomSpec(), birds.random)
        val stream = env.layers.first { it.kind == LayerKind.LOOP }
        assertEquals(listOf("content://test/ambience/forest/layers/stream.ogg"), stream.uris)
    }

    @Test
    fun `single base file becomes the day base`() {
        val env = build(audio("ambience/tavern/base.ogg")).library.environments.single()
        assertEquals("content://test/ambience/tavern/base.ogg", env.baseDayUri)
        assertNull(env.baseNightUri)
        assertEquals("Tavern", env.name)
    }

    @Test
    fun `base day without night warns and becomes main base`() {
        val result = build(audio("ambience/forest/base_day.ogg"))
        val env = result.library.environments.single()
        assertEquals("content://test/ambience/forest/base_day.ogg", env.baseDayUri)
        assertNull(env.baseNightUri)
        assertEquals(WarningReason.DAY_WITHOUT_NIGHT, result.warnings.single().reason)
    }

    @Test
    fun `environment without base but with layers warns base missing`() {
        val result = build(audio("ambience/cave/spots/drip.ogg"))
        val env = result.library.environments.single()
        assertNull(env.baseDayUri)
        assertEquals(1, env.layers.size)
        assertEquals(WarningReason.BASE_MISSING, result.warnings.single().reason)
    }

    @Test
    fun `folder with nothing playable is skipped`() {
        val result = build(
            IndexedFile(relativePath = "ambience/empty/meta.json", uri = "u", metaText = "{}"),
        )
        assertTrue(result.library.environments.isEmpty())
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `stray audio in environment root becomes a loop layer`() {
        val result = build(
            audio("ambience/forest/base.ogg"),
            audio("ambience/forest/crickets_01.ogg"),
            audio("ambience/forest/crickets_02.ogg"),
        )
        val env = result.library.environments.single()
        assertTrue(result.warnings.isEmpty())
        val crickets = env.layers.single()
        assertEquals("ambience/forest/crickets", crickets.id)
        assertEquals(LayerKind.LOOP, crickets.kind)
        assertEquals(2, crickets.uris.size)
    }

    @Test
    fun `spots meta json overrides random spec`() {
        val result = build(
            metaFile("ambience/cave/spots", """{"random":{"minIntervalSec":5,"maxIntervalSec":9}}"""),
            audio("ambience/cave/spots/drip.ogg"),
        )
        val layer = result.library.environments.single().layers.single()
        val spec = requireNotNull(layer.random)
        assertEquals(5f, spec.minIntervalSec, 1e-6f)
        assertEquals(9f, spec.maxIntervalSec, 1e-6f)
        assertEquals(RandomSpec().gainJitterDb, spec.gainJitterDb, 1e-6f)
    }

    @Test
    fun `one shot variants are grouped by base name`() {
        val result = build(
            audio("sounds/creature/goblin_02.ogg"),
            audio("sounds/creature/goblin_01.ogg"),
            audio("sounds/creature/sword_hit.ogg"),
        )
        val byId = result.library.oneShots.associateBy { it.id }
        assertEquals(setOf("sounds/creature/goblin", "sounds/creature/sword_hit"), byId.keys)
        val goblin = byId.getValue("sounds/creature/goblin")
        assertEquals(SoundCategory.CREATURE, goblin.category)
        assertEquals(2, goblin.variants.size)
        assertEquals("goblin", goblin.name)
    }

    @Test
    fun `single group one shot inherits meta title`() {
        val result = build(
            metaFile("sounds/creature", """{"title":"Goblin","icon":"goblin"}"""),
            audio("sounds/creature/goblin_01.ogg"),
            audio("sounds/creature/goblin_02.ogg"),
        )
        val shot = result.library.oneShots.single()
        assertEquals("Goblin", shot.name)
        assertEquals("goblin", shot.iconKey)
    }

    @Test
    fun `one shot directly in sounds root gets custom category`() {
        val shot = build(audio("sounds/bell.ogg")).library.oneShots.single()
        assertEquals(SoundCategory.CUSTOM, shot.category)
        assertEquals("sounds/bell", shot.id)
        assertEquals("bell", shot.name)
    }

    @Test
    fun `weather folders map to weather loops and unknown warns`() {
        val result = build(
            audio("weather/rain/drizzle.ogg"),
            audio("weather/fog/mist.ogg"),
        )
        val rain = result.library.weatherLoops.getValue(Weather.RAIN).single()
        assertEquals("content://test/weather/rain/drizzle.ogg", rain.uri)
        assertEquals("drizzle", rain.title)
        val warning = result.warnings.single()
        assertEquals("weather/fog", warning.path)
        assertEquals(WarningReason.UNKNOWN_WEATHER_FOLDER, warning.reason)
    }

    @Test
    fun `empty scan produces empty library`() {
        val result = build()
        assertTrue(result.library.isEmpty)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `ids are derived from paths so rescans keep identity`() {
        val result = build(audio("music/happy/march.ogg"))
        assertEquals("music/happy/march.ogg", result.library.tracks.single().id)
    }
}
