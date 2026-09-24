package com.example.dndsound.core.library

import com.example.dndsound.core.model.RandomSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MetaJsonTest {

    @Test
    fun `full meta parses`() {
        val meta = parseMetaJson(
            """
            {
              "title": "Вечерний лес",
              "category": "nature",
              "icon": "forest",
              "zone": "mystic.spheric",
              "mode": "battle",
              "wheel": { "x": 0.7, "y": 0.7 },
              "gainDb": -2.0,
              "random": { "minIntervalSec": 8, "maxIntervalSec": 25 }
            }
            """.trimIndent(),
        )
        assertNotNull(meta)
        assertEquals("Вечерний лес", meta!!.title)
        assertEquals("nature", meta.category)
        assertEquals("mystic.spheric", meta.zone)
        assertEquals("battle", meta.mode)
        assertEquals(0.7f, meta.wheel!!.x)
        assertEquals(-2f, meta.gainDb)
    }

    @Test
    fun `random spec normalizes with defaults`() {
        val meta = parseMetaJson("""{"random": {"minIntervalSec": 8}}""")!!
        val spec: RandomSpec = meta.toRandomSpec()!!
        assertEquals(8f, spec.minIntervalSec)
        assertEquals(30f, spec.maxIntervalSec)
        assertEquals(2f, spec.gainJitterDb)
        assertEquals(0.5f, spec.panJitter)
        assertEquals(5f, spec.pitchJitterPct)
    }

    @Test
    fun `no random section means default spec`() {
        val meta = parseMetaJson("""{"title": "x"}""")!!
        assertNull(meta.toRandomSpec())
    }

    @Test
    fun `unknown keys are ignored`() {
        val meta = parseMetaJson("""{"title": "x", "futureField": 123}""")!!
        assertEquals("x", meta.title)
    }

    @Test
    fun `malformed json and blank text return null`() {
        assertNull(parseMetaJson("not json at all {"))
        assertNull(parseMetaJson("   "))
    }

    @Test
    fun `empty object parses with all defaults`() {
        val meta = parseMetaJson("{}")!!
        assertNull(meta.title)
        assertNull(meta.wheel)
        assertNull(meta.random)
    }
}
