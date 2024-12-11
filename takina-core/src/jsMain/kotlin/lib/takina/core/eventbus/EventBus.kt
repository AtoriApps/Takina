
package lib.takina.core.eventbus

import lib.takina.core.AbstractTakina

actual class EventBus actual constructor(context: AbstractTakina) : AbstractEventBus(context) {

	override fun createHandlersMap(): MutableMap<String, MutableSet<EventHandler<*>>> = HashMap()

	override fun createHandlersSet(): MutableSet<EventHandler<*>> = HashSet()

}