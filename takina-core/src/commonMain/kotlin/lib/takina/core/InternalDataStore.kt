
package lib.takina.core

import lib.takina.core.logger.LoggerFactory
import lib.takina.core.utils.Lock

class InternalDataStore {

	private val log = LoggerFactory.logger("lib.takina.core.SessionObject")

	private val properties: MutableMap<String, Entry> = HashMap()

	private val lock = Lock();

	fun clear() {
		clear(Int.MAX_VALUE)
	}

	fun clear(scope: Scope) {
		clear(scope.ordinal)
	}

	private fun clear(ordinal: Int) {
		val scopes = Scope.values()
			.filter { s -> s.ordinal <= ordinal }
			.toTypedArray()
		log.fine { "Clearing ${scopes.asList()}" }
		lock.withLock {
			val toRemove = this.properties.entries.filter { scopes.contains(it.value.scope) }.map { it.key };
			toRemove.onEach { this.properties.remove(it) }
		}
	}

	@Suppress("UNCHECKED_CAST")
	fun <T> getData(scope: Scope?, key: String): T? {
		val entry = lock.withLock { this.properties[key] }
		return if (entry == null) {
			null
		} else if (scope == null || scope == entry.scope) {
			entry.value as T?
		} else {
			null
		}
	}

	fun <T> getData(key: String): T? {
		return getData<T>(null, key)
	}

	fun setData(scope: Scope, key: String, value: Any?): InternalDataStore {
		lock.withLock {
			if (value == null) {
				this.properties.remove(key)
			} else {
				this.properties[key] = Entry(scope, value)
			}
		}
		return this
	}

	override fun toString(): String {
		return "AbstractSessionObject{properties=$properties}"
	}

	private data class Entry(val scope: Scope, val value: Any?)

}