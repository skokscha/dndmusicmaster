package com.example.dndsound.core.ambience

import com.example.dndsound.core.audio.FadeCoordinator
import com.example.dndsound.core.audio.PlayerEvent
import com.example.dndsound.core.audio.PlayerHandle
import com.example.dndsound.core.mixer.GainMath
import com.example.dndsound.core.model.AmbienceLayer
import com.example.dndsound.core.model.Environment
import com.example.dndsound.core.model.LayerKind
import com.example.dndsound.core.model.TimeOfDay
import com.example.dndsound.core.model.Weather
import com.example.dndsound.core.scheduler.RandomSpots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Ambience engine: one base loop (day/night switch), up to [LAYER_SLOTS]
 * manual loop layers, random spot sounds and a global weather loop.
 *
 * Player budget (6 ambience slots): 1 base + 1 weather + 4 shared layer
 * slots. LOOP layers occupy layer slots; RANDOM spots borrow a free layer
 * slot while playing and give it back on end — a spot with no free slot is
 * skipped that round, so manual layers always keep priority.
 *
 * Base loops normally use the player's repeat mode (gapless for well-encoded
 * OGG/Opus). An environment with seamless = false and a known base duration
 * runs its base on a [SeamlessFallbackLooper] (two extra temporary players).
 * Day/night switching with a single base handle fades through silence
 * (half crossfade out, half in) — a rare, deliberate user action.
 */
class AmbienceEngine(
    private val scope: CoroutineScope,
    playerFactory: () -> PlayerHandle,
    private val weatherFiles: suspend (Weather) -> List<String>,
    private val crossfadeMs: Long = 3_000,
    private val rampStepMs: Long = 25,
    private val random: Random = Random.Default,
) {

    private val _state = MutableStateFlow(AmbienceState())
    val state: StateFlow<AmbienceState> = _state.asStateFlow()

    sealed interface Command {
        data class SetEnvironment(val environment: Environment?, val timeOfDay: TimeOfDay) : Command
        data class SetTimeOfDay(val timeOfDay: TimeOfDay) : Command
        data class SetLayerEnabled(val layerId: String, val enabled: Boolean) : Command
        data class SetLayerGain(val layerId: String, val gainDb: Float) : Command
        data class SetWeather(val weather: Weather, val intensity: Float) : Command
        /** Internal: a spot scheduler fired for a layer. */
        data class FireSpot(val layerId: String) : Command
        data object Pause : Command
        data object Resume : Command
        data object Release : Command
    }

    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val fader = FadeCoordinator(rampStepMs)
    private val baseHandle: PlayerHandle = playerFactory()
    private val weatherHandle: PlayerHandle = playerFactory()
    private val pool: List<PlayerHandle> = List(LAYER_SLOTS) { playerFactory() }
    private val looperHandles = listOf(playerFactory(), playerFactory())
    private val fallbackLooper = SeamlessFallbackLooper(scope, looperHandles[0], looperHandles[1])

    private var environment: Environment? = null
    private val userEnabled = mutableMapOf<String, Boolean>()
    private val layerGains = mutableMapOf<String, Float>()

    /** pool slot -> loop layer id currently assigned to it. */
    private val assignedSlots = mutableMapOf<Int, String>()

    /** pool slot -> spot layer id borrowing it while playing. */
    private val activeSpots = mutableMapOf<Int, String>()
    private val lastSpotUri = mutableMapOf<String, String>()
    private val spotJobs = mutableMapOf<String, Job>()

    private var timeOfDay = TimeOfDay.DAY
    private var weather = Weather.NONE
    private var weatherIntensity = 0.6f
    private var paused = false

    init {
        (listOf(baseHandle, weatherHandle) + pool).forEachIndexed { index, handle ->
            scope.launch {
                handle.events.collect { event ->
                    val slot = index - POOL_FIRST
                    if (event is PlayerEvent.Ended && slot >= 0) {
                        // Spots are the only non-looping pool sounds; free the slot.
                        activeSpots.remove(slot)
                    }
                }
            }
        }
        scope.launch {
            for (command in commands) {
                try {
                    handleCommand(command)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.update { it.copy(error = e.message) }
                }
            }
        }
    }

    // ------------------------------------------------------------- public API

    fun setEnvironment(environment: Environment?, timeOfDay: TimeOfDay = this.timeOfDay) {
        commands.trySend(Command.SetEnvironment(environment, timeOfDay))
    }

    fun setTimeOfDay(timeOfDay: TimeOfDay) {
        commands.trySend(Command.SetTimeOfDay(timeOfDay))
    }

    fun setLayerEnabled(layerId: String, enabled: Boolean) {
        commands.trySend(Command.SetLayerEnabled(layerId, enabled))
    }

    fun setLayerGain(layerId: String, gainDb: Float) {
        commands.trySend(Command.SetLayerGain(layerId, gainDb))
    }

    fun setWeather(weather: Weather, intensity: Float = this.weatherIntensity) {
        commands.trySend(Command.SetWeather(weather, intensity))
    }

    fun pause() {
        commands.trySend(Command.Pause)
    }

    fun resume() {
        commands.trySend(Command.Resume)
    }

    fun release() {
        commands.trySend(Command.Release)
    }

    // -------------------------------------------------------------- internals

    private suspend fun handleCommand(command: Command) {
        when (command) {
            is Command.SetEnvironment -> switchEnvironment(command.environment, command.timeOfDay)
            is Command.SetTimeOfDay -> changeTimeOfDay(command.timeOfDay)
            is Command.SetLayerEnabled -> {
                userEnabled[command.layerId] = command.enabled
                reassignLayers()
            }

            is Command.SetLayerGain -> {
                layerGains[command.layerId] = command.gainDb
                applyLayerGain(command.layerId)
            }

            is Command.SetWeather -> changeWeather(command.weather, command.intensity)
            is Command.FireSpot -> fireSpot(command.layerId)
            Command.Pause -> pauseAll()
            Command.Resume -> resumeAll()
            Command.Release -> releaseNow()
        }
    }

    private suspend fun switchEnvironment(env: Environment?, newTimeOfDay: TimeOfDay) {
        cancelSpotJobs()
        environment = env
        timeOfDay = newTimeOfDay
        if (env == null) {
            stopBase()
            stopAllPool()
            weather = Weather.NONE
            stopWeather()
            publishState()
            return
        }

        // Keep user toggles/gains only for layers present in the new env.
        val newLayerIds = env.layers.map { it.id }.toSet()
        userEnabled.keys.retainAll(newLayerIds)
        layerGains.keys.retainAll(newLayerIds)

        startBase(env)
        reassignLayers()
        if (weather != Weather.NONE) refreshWeather() // same engine, new random pick
        publishState()
    }

    private suspend fun startBase(env: Environment) {
        val uri = baseUriFor(env) ?: return
        paused = false
        if (!env.seamless && env.baseDurationMs > 0) {
            baseHandle.pause()
            fallbackLooper.start(uri, loopLengthMs = env.baseDurationMs, volume = 1f)
        } else {
            fallbackLooper.stop()
            baseHandle.setSource(uri)
            baseHandle.setLooping(true)
            baseHandle.setVolume(0f)
            baseHandle.play()
            val generation = fader.begin()
            scope.launch { fader.rampTo(generation, baseHandle, 1f, crossfadeMs) }
        }
    }

    private suspend fun changeTimeOfDay(newTimeOfDay: TimeOfDay) {
        val env = environment ?: return
        if (newTimeOfDay == timeOfDay) return
        val uri = when (newTimeOfDay) {
            TimeOfDay.DAY -> env.baseDayUri ?: return
            TimeOfDay.NIGHT -> env.baseNightUri ?: return
        }
        timeOfDay = newTimeOfDay
        if (!env.seamless && env.baseDurationMs > 0) {
            fallbackLooper.start(uri, loopLengthMs = env.baseDurationMs, volume = 1f)
            publishState()
            return
        }
        // Single base handle: fade through silence (half out, half in).
        val generation = fader.begin()
        if (fader.fadeOutAll(generation, listOf(baseHandle), crossfadeMs / 2)) {
            baseHandle.setSource(uri)
            baseHandle.setLooping(true)
            baseHandle.play()
            fader.rampTo(generation, baseHandle, 1f, crossfadeMs / 2)
        }
        publishState()
    }

    private fun baseUriFor(env: Environment): String? = when (timeOfDay) {
        TimeOfDay.DAY -> env.baseDayUri ?: env.baseNightUri
        TimeOfDay.NIGHT -> env.baseNightUri ?: env.baseDayUri
    }

    private suspend fun stopBase() {
        fallbackLooper.stop()
        val generation = fader.begin()
        if (fader.fadeOutAll(generation, listOf(baseHandle), crossfadeMs)) {
            baseHandle.pause()
        }
    }

    /**
     * (Re)distributes the layer pool: enabled LOOP layers occupy slots in
     * layer order, freed slots fade out, spot schedulers restart.
     */
    private suspend fun reassignLayers() {
        val env = environment ?: return
        val layers = env.layers
        val wanted = layers.filter { it.kind == LayerKind.LOOP && isEnabled(it) }.take(LAYER_SLOTS)
        val wantedIds = wanted.map { it.id }.toSet()

        // Fade out slots whose layer is gone, toggled off or over capacity.
        assignedSlots.entries.toList().forEach { (slot, layerId) ->
            if (layerId !in wantedIds) {
                assignedSlots.remove(slot)
                val handle = pool[slot]
                val generation = fader.begin()
                scope.launch {
                    if (fader.rampTo(generation, handle, 0f, crossfadeMs)) handle.pause()
                }
            }
        }

        // Assign enabled loop layers to free slots in order.
        var nextSlot = 0
        wanted.forEach { layer ->
            if (assignedSlots.values.contains(layer.id)) return@forEach
            while (nextSlot < LAYER_SLOTS && assignedSlots.containsKey(nextSlot)) nextSlot++
            if (nextSlot >= LAYER_SLOTS) return@forEach
            val slot = nextSlot++
            assignedSlots[slot] = layer.id
            val handle = pool[slot]
            handle.setSource(layer.uris.first())
            handle.setLooping(true)
            handle.setVolume(0f)
            handle.play()
            val gain = layerGainLinear(layer)
            val generation = fader.begin()
            scope.launch { fader.rampTo(generation, handle, gain, crossfadeMs) }
        }

        restartSpotJobs(layers)
        publishState()
    }

    private suspend fun applyLayerGain(layerId: String) {
        val env = environment ?: return
        val layer = env.layers.firstOrNull { it.id == layerId } ?: return
        val slot = assignedSlots.entries.firstOrNull { it.value == layerId }?.key
        if (slot != null && isEnabled(layer)) {
            val generation = fader.begin()
            fader.rampTo(generation, pool[slot], layerGainLinear(layer), 100)
        }
        publishState()
    }

    private fun restartSpotJobs(layers: List<AmbienceLayer>) {
        cancelSpotJobs()
        layers.filter { it.kind == LayerKind.RANDOM && isEnabled(it) }.forEach { layer ->
            spotJobs[layer.id] = scope.launch { spotLoop(layer) }
        }
    }

    private suspend fun spotLoop(layer: AmbienceLayer) {
        val spec = layer.random ?: return
        while (true) {
            val intervalSec = RandomSpots.nextIntervalSec(spec.minIntervalSec, spec.maxIntervalSec, random)
            delay((intervalSec * 1000).toLong().coerceAtLeast(1))
            commands.trySend(Command.FireSpot(layer.id))
        }
    }

    private suspend fun fireSpot(layerId: String) {
        if (paused) return
        val env = environment ?: return
        val layer = env.layers.firstOrNull { it.id == layerId } ?: return
        val spec = layer.random ?: return
        val slot = (0 until LAYER_SLOTS).firstOrNull { it !in assignedSlots && it !in activeSpots } ?: return
        val variantUri = RandomSpots.pickVariant(layer.uris, lastSpotUri[layerId], random) ?: return
        lastSpotUri[layerId] = variantUri

        val gainDb = RandomSpots.jitterGainDb(layer.baseGainDb, spec.gainJitterDb, random)
        val speed = RandomSpots.jitterPitch(spec.pitchJitterPct, random)
        val handle = pool[slot]
        handle.setSource(variantUri)
        handle.setLooping(false)
        handle.setSpeedFactor(speed)
        handle.setVolume(GainMath.dbToLinear(gainDb))
        handle.play()
        activeSpots[slot] = layerId
    }

    private suspend fun changeWeather(newWeather: Weather, intensity: Float) {
        val changed = newWeather != weather
        weather = newWeather
        weatherIntensity = intensity.coerceIn(0f, 1f)
        when {
            newWeather == Weather.NONE -> stopWeather()
            changed -> refreshWeather() // new weather -> pick a new loop file
            else -> {
                val generation = fader.begin()
                fader.rampTo(generation, weatherHandle, weatherTarget(), crossfadeMs)
            }
        }
    }

    private suspend fun refreshWeather() {
        val files = weatherFiles(weather)
        if (files.isEmpty()) {
            stopWeather()
            return
        }
        val uri = files[random.nextInt(files.size)]
        weatherHandle.setSource(uri)
        weatherHandle.setLooping(true)
        weatherHandle.setVolume(0f)
        weatherHandle.play()
        val generation = fader.begin()
        fader.rampTo(generation, weatherHandle, weatherTarget(), crossfadeMs)
    }

    private suspend fun stopWeather() {
        val generation = fader.begin()
        if (fader.rampTo(generation, weatherHandle, 0f, crossfadeMs)) {
            weatherHandle.pause()
        }
    }

    private fun pauseAll() {
        if (environment == null) return
        paused = true
        fallbackLooper.pause()
        (listOf(baseHandle, weatherHandle) + pool).forEach { it.pause() }
        publishState()
    }

    private fun resumeAll() {
        if (environment == null) return
        paused = false
        fallbackLooper.resume()
        baseHandle.play()
        if (weather != Weather.NONE) weatherHandle.play()
        assignedSlots.keys.forEach { slot -> pool[slot].play() }
        // Spots paused mid-playback must resume too, or their slots leak.
        activeSpots.keys.forEach { slot -> pool[slot].play() }
        restartSpotJobs(environment?.layers.orEmpty())
        publishState()
    }

    private fun releaseNow() {
        cancelSpotJobs()
        fallbackLooper.stop()
        (listOf(baseHandle, weatherHandle) + pool + looperHandles).forEach { it.release() }
        environment = null
        assignedSlots.clear()
        publishState()
    }

    private fun cancelSpotJobs() {
        spotJobs.values.forEach { it.cancel() }
        spotJobs.clear()
        activeSpots.clear()
    }

    private fun stopAllPool() {
        pool.forEach { it.pause() }
        assignedSlots.clear()
        activeSpots.clear()
    }

    private fun publishState() {
        val env = environment
        _state.update { previous ->
            AmbienceState(
                environmentId = env?.id,
                environmentName = env?.name,
                timeOfDay = timeOfDay,
                playing = env != null && !paused,
                layers = env?.layers?.map { layer ->
                    LayerState(
                        id = layer.id,
                        name = layer.name,
                        kind = layer.kind,
                        enabled = isEnabled(layer),
                        gainDb = layerGains[layer.id] ?: layer.baseGainDb,
                    )
                }.orEmpty(),
                weather = weather,
                weatherIntensity = weatherIntensity,
                error = previous.error,
            )
        }
    }

    private fun isEnabled(layer: AmbienceLayer): Boolean =
        userEnabled[layer.id] ?: layer.defaultEnabled

    /** Weather loudness scales with intensity between -24 dB and 0 dB. */
    private fun weatherTarget(): Float =
        GainMath.dbToLinear(WEATHER_MIN_DB + weatherIntensity * (0f - WEATHER_MIN_DB))

    private fun layerGainLinear(layer: AmbienceLayer): Float =
        GainMath.dbToLinear(layerGains[layer.id] ?: layer.baseGainDb)

    private companion object {
        const val LAYER_SLOTS = 4
        const val POOL_FIRST = 2
        const val WEATHER_MIN_DB = -24f
    }
}
