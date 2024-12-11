
package lib.takina.core.eventbus

import kotlinx.datetime.Clock
import lib.takina.core.AbstractTakina

abstract class AbstractEventBus(val context: AbstractTakina) : NoContextEventBus() {

	override fun updateBeforeFire(event: Event) {
		event.eventTime = Clock.System.now()
		event.context = context
	}

}