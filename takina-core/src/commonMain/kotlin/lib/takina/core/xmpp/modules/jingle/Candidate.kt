
package lib.takina.core.xmpp.modules.jingle

import lib.takina.core.xml.Element
import lib.takina.core.xml.element
import lib.takina.core.xmpp.nextUID
import kotlin.jvm.JvmStatic

class Candidate(
	val component: String,
	val foundation: String,
	val generation: Int,
	val id: String,
	val ip: String,
	val network: Int?,
	val port: Int,
	val priority: Int,
	val protocolType: ProtocolType,
	val relAddr: String?,
	val relPort: Int?,
	val type: CandidateType?,
	val tcpType: String?,
) {

	enum class ProtocolType {
		udp,
		tcp
	}

	enum class CandidateType {
		host,
		prlfx,
		relay,
		srflx
	}

	fun toElement(): Element {
		return element("candidate") {
			attribute("component", component)
			attribute("foundation", foundation)
			attribute("generation", generation.toString())
			attribute("id", id)
			attribute("ip", ip)
			network?.let { attribute("network", it.toString()) }
			attribute("port", port.toString())
			attribute("protocol", protocolType.name)
			attribute("priority", priority.toString())
			relAddr?.let { attribute("rel-addr", it) }
			relPort?.let { attribute("rel-port", it.toString()) }
			type?.let { attribute("type", it.name) }
			tcpType?.let { attribute("tcptype", it) }
		}
	}

	companion object {

		const val CID_ATTR = "cid"
		const val HOST_ATTR = "host"
		const val JID_ATTR = "jid"
		const val PORT_ATTR = "port"
		const val PRIORITY_ATTR = "priority"
		const val TYPE_ATTR = "type"

		@JvmStatic
		fun parse(el: Element): Candidate? {
			if (!"candidate".equals(el.name)) {
				return null
			}
			val component = el.attributes["component"] ?: return null
			val foundation = el.attributes["foundation"] ?: return null
			val generation = el.attributes["generation"]?.toInt() ?: return null
			val id = el.attributes["id"] ?: nextUID()
			val ip = el.attributes["ip"] ?: return null
			val network = el.attributes["network"]?.toInt() ?: return null
			val port = el.attributes["port"]?.toInt() ?: return null
			val priority = el.attributes["priority"]?.toInt() ?: return null
			val protocolType = el.attributes["protocol"]?.let { ProtocolType.valueOf(it) } ?: return null
			val relAddr = el.attributes["rel-addr"]
			val relPort = el.attributes["rel-port"]?.toInt()
			val cancidateType = el.attributes["type"]?.let { CandidateType.valueOf(it) }
			val tcpType = el.attributes["tcptype"]

			return Candidate(
				component,
				foundation,
				generation,
				id,
				ip,
				network,
				port,
				priority,
				protocolType,
				relAddr,
				relPort,
				cancidateType,
				tcpType
			)
		}
	}
}