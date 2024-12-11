
package lib.takina.core.connector

import lib.takina.core.eventbus.Event
import lib.takina.core.eventbus.EventDefinition
import lib.takina.core.requests.Request
import lib.takina.core.xml.Element

data class ConnectorStateChangeEvent(val oldState: State, val newState: State) : Event(TYPE) {

	companion object : EventDefinition<ConnectorStateChangeEvent> {

		override val TYPE = "lib.takina.core.connector.ConnectorStateChangeEvent"
	}
}

data class ReceivedXMLElementEvent(val element: Element) : Event(TYPE) {

	companion object : EventDefinition<ReceivedXMLElementEvent> {

		override val TYPE = "lib.takina.core.connector.ReceivedXMLElementEvent"
	}
}

data class StreamStartedEvent(val attrs: Map<String, String>) : Event(TYPE) {

	companion object : EventDefinition<StreamStartedEvent> {

		override val TYPE = "lib.takina.core.connector.StreamStartedEvent"
	}
}

class StreamTerminatedEvent : Event(TYPE) {

	companion object : EventDefinition<StreamStartedEvent> {

		override val TYPE = "lib.takina.core.connector.StreamTerminatedEvent"
	}
}

data class ParseErrorEvent(val errorMessage: String) : Event(TYPE) {

	companion object : EventDefinition<ParseErrorEvent> {

		override val TYPE = "lib.takina.core.connector.ParseErrorEvent"
	}
}

data class SentXMLElementEvent(val element: Element, val request: Request<*, *>?) : Event(TYPE) {

	companion object : EventDefinition<SentXMLElementEvent> {

		override val TYPE = "lib.takina.core.connector.SentXMLElementEvent"
	}
}

abstract class ConnectionErrorEvent : Event(TYPE) {

	companion object : EventDefinition<ConnectionErrorEvent> {

		override val TYPE = "lib.takina.core.connector.ConnectionErrorEvent"
	}
}