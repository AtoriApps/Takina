
package lib.takina.core.logger.internal

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import lib.takina.core.logger.Level
import lib.takina.core.logger.LoggerInternal

class LoggerSPIBuffer(val bufferSize: Int = 100) {

	data class Entry(
		val timestamp: Instant, val level: Level, val loggerName: String, val msg: String, val caught: Throwable?,
	)

	var spiFactory: ((String, Boolean) -> LoggerInternal) = { name, enabled -> DefaultLoggerSPI(name, enabled) }

	private val buffer = mutableListOf<Entry>()

	var callback: ((Entry) -> Unit)? = null

	private fun add(entry: Entry) {
		buffer.add(entry)
		if (buffer.size > bufferSize) {
			buffer.removeAt(0)
		}
		callback?.invoke(entry)
	}

	fun getBuffer(): List<Entry> = buffer

	fun create(name: String, enabled: Boolean): LoggerInternal {
		val spi = spiFactory.invoke(name, enabled)
		return object : LoggerInternal {
			override fun isLoggable(level: Level): Boolean = spi.isLoggable(level)

			override fun log(level: Level, msg: String, caught: Throwable?) {
				add(Entry(Clock.System.now(), level, name, msg, caught))
				spi.log(level, msg, caught)
			}
		}
	}

}