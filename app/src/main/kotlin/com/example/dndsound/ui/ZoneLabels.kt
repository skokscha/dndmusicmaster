package com.example.dndsound.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.dndsound.R
import com.example.dndsound.core.model.Mood
import com.example.dndsound.core.wheel.WheelZone

/**
 * Zone id -> label resource. Keys mirror wheel_zones.json with dots replaced
 * by underscores (zone_happy_vivid_town); adding a zone to the JSON means
 * adding a branch here and two string entries.
 */
fun zoneLabelRes(zoneId: String): Int = when (zoneId) {
    "neutral" -> R.string.zone_neutral
    "happy.calm" -> R.string.zone_happy_calm
    "happy.vivid_town" -> R.string.zone_happy_vivid_town
    "epic.soaring" -> R.string.zone_epic_soaring
    "epic.epic" -> R.string.zone_epic_epic
    "sad.defeat" -> R.string.zone_sad_defeat
    "sad.tragic" -> R.string.zone_sad_tragic
    "tense.thrilling" -> R.string.zone_tense_thrilling
    "tense.tense" -> R.string.zone_tense_tense
    "creepy.eerie" -> R.string.zone_creepy_eerie
    "creepy.creepy" -> R.string.zone_creepy_creepy
    "mystic.spheric" -> R.string.zone_mystic_spheric
    "mystic.mystic" -> R.string.zone_mystic_mystic
    "magical.slight_magic" -> R.string.zone_magical_slight_magic
    "magical.magical" -> R.string.zone_magical_magical
    "funny.noble" -> R.string.zone_funny_noble
    "funny.funny" -> R.string.zone_funny_funny
    "transition.victory" -> R.string.zone_transition_victory
    "transition.tragic_fight" -> R.string.zone_transition_tragic_fight
    "transition.moody" -> R.string.zone_transition_moody
    "transition.dread" -> R.string.zone_transition_dread
    "transition.haunted" -> R.string.zone_transition_haunted
    "transition.enchanted" -> R.string.zone_transition_enchanted
    "transition.whimsical" -> R.string.zone_transition_whimsical
    "transition.festive" -> R.string.zone_transition_festive
    else -> R.string.zone_neutral
}

@Composable
fun zoneName(zone: WheelZone): String = stringResource(zoneLabelRes(zone.id))

@Composable
private fun moodName(mood: Mood): String = stringResource(moodLabelRes(mood))

/** Small line under the wheel: "mode · sector", "sector ↔ sector", or the mode in the center. */
@Composable
fun zoneSubtitle(zone: WheelZone, modeLabel: String): String = when (zone) {
    WheelZone.Neutral -> modeLabel
    is WheelZone.Sector -> "$modeLabel · ${moodName(zone.mood)}"
    is WheelZone.Transition -> "${moodName(zone.a)} ↔ ${moodName(zone.b)}"
}
