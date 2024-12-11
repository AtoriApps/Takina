
package lib.takina.core.logger

import lib.takina.core.logger.internal.DefaultLoggerSPI

/**
 * Service Provider Interface.
 */
interface LoggerInternal {

	fun isLoggable(level: Level): Boolean
	fun log(level: Level, msg: String, caught: Throwable?)

}

object LoggerFactory {

	var spiFactory: ((String, Boolean) -> LoggerInternal) = { name, enabled -> DefaultLoggerSPI(name, enabled) }

	fun logger(name: String, enabled: Boolean = true): Logger {
		return LoggerWrapper(spiFactory.invoke(name, enabled))
	}

}

interface Logger {

	fun isLoggable(level: Level): Boolean
	fun log(level: Level, msg: String)
	fun log(level: Level, msg: String, caught: Throwable)

	fun fine(msg: String) = log(Level.FINE, msg)
	fun finer(msg: String) = log(Level.FINER, msg)
	fun finest(msg: String) = log(Level.FINEST, msg)

	fun config(msg: String) = log(Level.CONFIG, msg)
	fun info(msg: String) = log(Level.INFO, msg)
	fun warning(msg: String) = log(Level.WARNING, msg)
	fun severe(msg: String) = log(Level.SEVERE, msg)

	fun log(level: Level, caught: Throwable? = null, msg: () -> Any?) {
		if (isLoggable(level)) {
			if (caught == null) log(
				level, msg.invoke().toString()
			)
			else log(
				level, msg.invoke().toString(), caught
			)
		}
	}

	fun fine(caught: Throwable? = null, msg: () -> Any?) = log(level = Level.FINE, caught = caught, msg = msg)
	fun finer(caught: Throwable? = null, msg: () -> Any?) = log(level = Level.FINER, caught = caught, msg = msg)
	fun finest(caught: Throwable? = null, msg: () -> Any?) = log(level = Level.FINEST, caught = caught, msg = msg)

	fun config(caught: Throwable? = null, msg: () -> Any?) = log(level = Level.CONFIG, caught = caught, msg = msg)
	fun info(caught: Throwable? = null, msg: () -> Any?) = log(level = Level.INFO, caught = caught, msg = msg)
	fun warning(caught: Throwable? = null, msg: () -> Any?) = log(level = Level.WARNING, caught = caught, msg = msg)
	fun severe(caught: Throwable? = null, msg: () -> Any?) = log(level = Level.SEVERE, caught = caught, msg = msg)
}

class LoggerWrapper(private val spi: LoggerInternal) : Logger {

	override fun isLoggable(level: Level): Boolean = spi.isLoggable(level)

	override fun log(level: Level, msg: String) = spi.log(level, msg, null)

	override fun log(level: Level, msg: String, caught: Throwable) = spi.log(level, msg, caught)

}