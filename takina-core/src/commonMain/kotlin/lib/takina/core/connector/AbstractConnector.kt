
package lib.takina.core.connector

import lib.takina.core.AbstractTakina
import lib.takina.core.eventbus.Event
import lib.takina.core.xml.Element
import lib.takina.core.xmpp.modules.sm.StreamManagementModule

abstract class AbstractConnector(val takina: AbstractTakina) {

	protected var eventsEnabled = true

	var state: State = State.Disconnected
		protected set(value) {
			val old = field
			field = value
			if (old != field) fire(ConnectorStateChangeEvent(old, field))
		}

	abstract fun createSessionController(): SessionController

	abstract fun send(data: CharSequence)

	abstract fun start()

	abstract fun stop()

	protected fun handleReceivedElement(element: Element) {
		if (takina.getModuleOrNull(StreamManagementModule)?.processElementReceived(element) == true) {
			return
		}
		fire(ReceivedXMLElementEvent(element))
	}

	protected fun fire(e: Event) {
		if (eventsEnabled) takina.eventBus.fire(e)
	}
}