package com.example.dndsound.ui

import com.example.dndsound.R
import com.example.dndsound.core.model.Mood

/** Mood enum -> localized label resource; shared by the wheel and status rows. */
fun moodLabelRes(mood: Mood): Int = when (mood) {
    Mood.HAPPY -> R.string.mood_happy
    Mood.EPIC -> R.string.mood_epic
    Mood.SAD -> R.string.mood_sad
    Mood.TENSE -> R.string.mood_tense
    Mood.CREEPY -> R.string.mood_creepy
    Mood.MYSTIC -> R.string.mood_mystic
    Mood.MAGICAL -> R.string.mood_magical
    Mood.FUNNY -> R.string.mood_funny
}
