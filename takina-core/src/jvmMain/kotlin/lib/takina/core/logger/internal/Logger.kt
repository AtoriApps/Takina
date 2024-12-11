
package lib.takina.core.logger.internal

import lib.takina.core.logger.Level
import lib.takina.core.logger.LoggerInternal
import java.util.logging.LogRecord

actual class DefaultLoggerSPI actual constructor(name: String, val enabled: Boolean) : LoggerInternal {

	private val log = java.util.logging.Logger.getLogger(name)

	private fun cnv(level: Level): java.util.logging.Level = when (level) {
		Level.OFF -> java.util.logging.Level.OFF
		Level.SEVERE -> java.util.logging.Level.SEVERE
		Level.WARNING -> java.util.logging.Level.WARNING
		Level.INFO -> java.util.logging.Level.INFO
		Level.CONFIG -> java.util.logging.Level.CONFIG
		Level.FINE -> java.util.logging.Level.FINE
		Level.FINER -> java.util.logging.Level.FINER
		Level.FINEST -> java.util.logging.Level.FINEST
		Level.ALL -> java.util.logging.Level.ALL
	}

	actual override fun isLoggable(level: Level): Boolean {
		return log.isLoggable(cnv(level))
	}

	private fun doLog(level: Level, msg: String, caught: Throwable?) {
		if (!enabled) return
		val lr = LogRecord(cnv(level), msg)
		if (caught != null) lr.thrown = caught

		fillCaller(lr)

		log.log(lr)
	}

	private fun fillCaller(lr: LogRecord) {
		val trace = Throwable()
		val list = trace.stackTrace

		list.find { stackTraceElement ->
			!stackTraceElement.className.startsWith(
				"lib.takina.core.logger."
			)
		}.let { stackTraceElement ->
				if (stackTraceElement != null) {
					lr.sourceClassName = stackTraceElement.className
					lr.sourceMethodName = stackTraceElement.methodName
				}
			}
	}

	actual override fun log(level: Level, msg: String, caught: Throwable?) {
		doLog(level, msg, caught)
	}

}