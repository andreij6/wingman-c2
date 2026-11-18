package com.wingmanc2.core.simulation.uas

import com.wingmanc2.core.common.Clock
import com.wingmanc2.core.model.TelemetryFrame
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UasPhysicsEngine @Inject constructor(private val clock: Clock) {
    private val commands = Channel<FlightCommand>(Channel.UNLIMITED)
    private val threats = MutableStateFlow<List<PopupThreat>>(emptyList())
    private val mutableEvents = MutableSharedFlow<SimEvent>(
        extraBufferCapacity = EVENT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events: SharedFlow<SimEvent> = mutableEvents.asSharedFlow()

    fun fly(route: FlightRoute, speedFactor: Double = DEFAULT_SPEED_FACTOR, seed: Long = DEFAULT_SEED): Flow<TelemetryFrame> = flow {
        while (commands.tryReceive().isSuccess) continue
        val session = FlightSession(route, TICK_MS / 1_000.0 * speedFactor, seed, mutableEvents)
        while (true) {
            var command = commands.tryReceive().getOrNull()
            while (command != null) {
                session.execute(command)
                command = commands.tryReceive().getOrNull()
            }
            emit(session.step(clock.nowMs(), threats.value))
            delay(TICK_MS)
        }
    }

    fun command(command: FlightCommand) {
        commands.trySend(command)
    }

    fun injectThreat(threat: PopupThreat) {
        threats.update { current -> if (current.any { it.key == threat.key }) current else current + threat }
    }

    companion object {
        const val TICK_MS = 200L
        const val DEFAULT_SPEED_FACTOR = 4.0
        const val DEFAULT_SEED = 0x5EED_C2L
        private const val EVENT_BUFFER = 64
    }
}
