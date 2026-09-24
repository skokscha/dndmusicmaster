package com.example.dndsound.core.model

/**
 * The eight mood sectors of the mood wheel.
 *
 * Angle convention: 0 degrees points right (east), positive angles go
 * counter-clockwise. Happy sits at the top (90), the rest follow clockwise
 * from there as per the UI spec. The calm center is not a Mood — it is
 * any point with a small radius.
 */
enum class Mood(val angleDeg: Float) {
    HAPPY(90f),
    EPIC(45f),
    SAD(0f),
    TENSE(-45f),
    CREEPY(-90f),
    MYSTIC(-135f),
    MAGICAL(180f),
    FUNNY(135f),
}
