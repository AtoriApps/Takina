
package lib.takina.core.xmpp.modules.mam

import kotlinx.datetime.Instant
import lib.takina.core.TickEvent
import lib.takina.core.eventbus.EventBus
import lib.takina.core.eventbus.handler
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ExpiringMap<K, V>(
	private val map: MutableMap<K, V> = mutableMapOf(), private val minimalTime: Duration = 30.seconds,
) : MutableMap<K, V> by map {

	var expirationChecker: ((V) -> Boolean)? = null

	private val tickHandler = TickEvent.handler(::onTick)

	private var lastCallTime = Instant.DISTANT_PAST

	var eventBus: EventBus? = null
		set(value) {
			field?.unregister(tickHandler)
			field = value
			field?.register(TickEvent, tickHandler)
		}

	private fun onTick(event: TickEvent) {
		if (lastCallTime + minimalTime <= event.eventTime) {
			lastCallTime = event.eventTime
			clearOutdated()
		}
	}

	@Suppress("unused")
	fun clearOutdated() {
		map.filter { (_, value) -> expirationChecker?.invoke(value) ?: false }
			.map { (key, _) -> key }
			.forEach { map.remove(it) }
	}

	override fun get(key: K): V? {
		val result = map.get(key) ?: return null
		if (expirationChecker?.invoke(result) == true) {
			map.remove(key)
			return null
		}
		return result
	}

}