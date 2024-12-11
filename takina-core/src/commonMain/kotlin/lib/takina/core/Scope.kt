
package lib.takina.core

import lib.takina.core.eventbus.Event
import lib.takina.core.eventbus.EventDefinition

enum class Scope {

	/**
	 * Properties in this scope are cleared when server sends new stream.
	 */
	Stream,

	/**
	 * Properties in this scope are cleared when connector is disconnected.
	 */
	Connection,

	/**
	 * Properties in this scope are cleared when client is manually stopped.
	 */
	Session,

	/**
	 * User property, as password, username etc. Not cleared.
	 */
	User,
}

data class ClearedEvent(val scopes: Array<Scope>) : Event(TYPE) {

	companion object : EventDefinition<ClearedEvent> {

		override val TYPE = "lib.takina.core.ClearedEvent"
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other == null || this::class != other::class) return false

		other as ClearedEvent

		if (!scopes.contentEquals(other.scopes)) return false

		return true
	}

	override fun hashCode(): Int {
		return scopes.contentHashCode()
	}
}