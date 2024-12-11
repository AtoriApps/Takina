
package lib.takina.core.xmpp

import lib.takina.core.eventbus.Event
import lib.takina.core.eventbus.EventDefinition
import lib.takina.core.xml.Element

data class ReceivedStreamFeaturesEvent(val features: List<Element>) : Event(TYPE) {

	companion object : EventDefinition<ReceivedStreamFeaturesEvent> {

		override val TYPE = "lib.takina.core.xmpp.ReceivedStreamFeaturesEvent"
	}
}