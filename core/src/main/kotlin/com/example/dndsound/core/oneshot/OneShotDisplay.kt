package com.example.dndsound.core.oneshot

import com.example.dndsound.core.model.OneShot

/** Display ordering rules for the one-shot panel (pure, unit-tested). */
object OneShotDisplay {

    /**
     * Favorites first, then alphabetical (case-insensitive). Used by the
     * sounds panel so the GM's go-to sounds stay at the top of the flow.
     */
    fun sortedForDisplay(groups: List<OneShot>, favorites: Set<String>): List<OneShot> =
        groups.sortedWith(
            compareByDescending<OneShot> { it.id in favorites }
                .thenBy { it.name.lowercase() },
        )
}
