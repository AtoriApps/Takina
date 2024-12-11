
package lib.takina.core.xmpp.modules.jingle

import lib.takina.core.xml.Element
import lib.takina.core.xml.element
import kotlin.jvm.JvmStatic

class Content(
	val creator: Creator, val senders: Senders?, val name: String, val description: Description?, val transports: List<Transport>,
) {

	enum class Creator {

		initiator,
		responder;

	}

	enum class Senders {
		none,
		both,
		initiator,
		responder;

		fun streamType(localRole: Creator, direction: SDPDirection): SDP.StreamType {
			return when (this) {
				none -> SDP.StreamType.inactive
				both -> SDP.StreamType.sendrecv
				initiator -> when(direction) {
					SDPDirection.outgoing -> if (localRole == Creator.initiator) {
						SDP.StreamType.sendonly
					} else {
						SDP.StreamType.recvonly
					}
					SDPDirection.incoming -> if (localRole == Creator.responder) {
						SDP.StreamType.sendonly
					} else {
						SDP.StreamType.recvonly
					}
				}
				responder -> when(direction) {
					SDPDirection.outgoing -> if (localRole == Creator.responder) {
						SDP.StreamType.sendonly
					} else {
						SDP.StreamType.recvonly
					}
					SDPDirection.incoming -> if (localRole == Creator.initiator) {
						SDP.StreamType.sendonly
					} else {
						SDP.StreamType.recvonly
					}
				}
			}
		}
	}

	fun toElement(): Element {
		return element("content") {
			attribute("name", name)
			attribute("creator", creator.name)
			senders?.let { attribute("senders", it.name) }
			description?.let { addChild(it.toElement()) }
			transports.forEach { addChild(it.toElement()) }
		}
	}

	companion object {

		@JvmStatic
		fun parse(el: Element): Content? {
			if ("content" == el.name) {
				val name = el.attributes["name"] ?: return null
				val creator = el.attributes["creator"]?.let { Creator.valueOf(it) } ?: return null
				val description = el.getFirstChild("description")
					?.let { Description.parse(it) }
				val transports = el.children.map { Transport.parse(it) }
					.filterNotNull()
				val senders = el.attributes["senders"]?.let { Senders.valueOf(it) }
				return Content(creator, senders, name, description, transports)
			}
			return null
		}
	}
}