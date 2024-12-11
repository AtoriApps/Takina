
package lib.takina.core.logger.internal

import lib.takina.core.logger.Level
import lib.takina.core.logger.LoggerInternal

expect class DefaultLoggerSPI(name: String, enabled: Boolean) : LoggerInternal {

	override fun isLoggable(level: Level): Boolean
	override fun log(level: Level, msg: String, caught: Throwable?)

}