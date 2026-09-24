package com.example.dndsound.core.model

/** Music selection mode. BATTLE uses the same wheel but battle tracks only. */
enum class MusicMode { EXPLORATION, BATTLE }

/** Global weather layer played on top of any environment. */
enum class Weather { NONE, RAIN, STORM, WIND, SNOW }

/** Day/night variant of the environment base loop. */
enum class TimeOfDay { DAY, NIGHT }
