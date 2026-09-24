package com.example.dndsound.core.oneshot

import com.example.dndsound.core.model.OneShot
import com.example.dndsound.core.model.SoundFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OneShotDisplayTest {

    private fun shot(id: String, name: String) = OneShot(
        id = id,
        name = name,
        variants = listOf(SoundFile(id = id, title = name, uri = "content://$id", durationMs = 0L)),
    )

    @Test
    fun `favorites come first, then alphabetical ignoring case`() {
        val groups = listOf(
            shot("sounds/b/Bell", "Bell"),
            shot("sounds/c/chest", "Сундук"),
            shot("sounds/a/arrow", "arrow"),
            shot("sounds/d/dragon", "Dragon"),
        )
        val favorites = setOf("sounds/d/dragon", "sounds/b/Bell")

        val sorted = OneShotDisplay.sortedForDisplay(groups, favorites)

        assertEquals(
            listOf("sounds/b/Bell", "sounds/d/dragon", "sounds/a/arrow", "sounds/c/chest"),
            sorted.map { it.id },
        )
    }

    @Test
    fun `no favorites keeps a stable alphabetical order`() {
        val groups = listOf(shot("2", "Sword"), shot("1", "axe"))
        val sorted = OneShotDisplay.sortedForDisplay(groups, emptySet())
        assertEquals(listOf("1", "2"), sorted.map { it.id })
    }
}
