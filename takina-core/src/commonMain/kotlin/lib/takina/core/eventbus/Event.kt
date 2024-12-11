
package lib.takina.core.eventbus

import kotlinx.datetime.Instant
import lib.takina.core.AbstractTakina

interface EventDefinition<out M : Event> {

	val TYPE: String
}

/**
 * Base class for events.
 */
abstract class Event(
	/** Identifier of event type. */
	val eventType: String,
) {

	/**
	 * Takina context.
	 */
	lateinit var context: AbstractTakina
		internal set

	/**
	 * Event fire time.
	 */
	var eventTime: Instant = Instant.DISTANT_PAST
		internal set

}
