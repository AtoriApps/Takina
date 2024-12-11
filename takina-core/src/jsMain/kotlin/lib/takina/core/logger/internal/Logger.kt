
package lib.takina.core.logger.internal

import lib.takina.core.logger.Level
import lib.takina.core.logger.LoggerInternal
import kotlin.js.Date

actual class DefaultLoggerSPI actual constructor(val name: String, val enabled: Boolean) : LoggerInternal {

	companion object {

		var levelFilter: Level = Level.INFO
		var nameFilter: String? = null
	}

	actual override fun isLoggable(level: Level): Boolean = levelFilter.value <= level.value

	private fun log(level: Level, msg: String) {
//		if (nameFilter != null && !name.matches(nameFilter!!)) {
//			return
//		}
		if (!enabled) return
		val dt = Date()
		val formattedMsg = "${dt.toUTCString()} [$level] $name: $msg"

		if (isLoggable(level)) when (level) {
			Level.SEVERE -> console.error(formattedMsg)
			Level.WARNING -> console.warn(formattedMsg)
			Level.INFO -> console.info(formattedMsg)
			Level.CONFIG -> console.info(formattedMsg)
			Level.FINE, Level.FINER, Level.FINEST -> console.log(formattedMsg)
			else -> {
			}
		}
	}

	actual override fun log(level: Level, msg: String, caught: Throwable?) {
		if (caught == null) log(level, msg) else log(level, msg + '\n' + caught.toString())
	}

}