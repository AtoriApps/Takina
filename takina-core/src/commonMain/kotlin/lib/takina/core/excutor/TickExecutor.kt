
package lib.takina.core.excutor

import kotlinx.datetime.Instant
import lib.takina.core.TickEvent
import lib.takina.core.eventbus.AbstractEventBus
import lib.takina.core.eventbus.handler
import kotlin.time.Duration

class TickExecutor(
	private val eventBus: AbstractEventBus, val minimalTime: Duration, private val runnable: () -> Unit,
) {

	private val handler = TickEvent.handler(::onTick)

	init {
		start()
	}

	private var lastCallTime = Instant.DISTANT_PAST

	private fun onTick(event: TickEvent) {
		if (lastCallTime + minimalTime <= event.eventTime) {
			lastCallTime = event.eventTime
			runnable.invoke()
		}
	}

	fun start() {
		eventBus.register(TickEvent, handler)
	}

	fun stop() {
		eventBus.unregister(TickEvent, handler)
	}

}