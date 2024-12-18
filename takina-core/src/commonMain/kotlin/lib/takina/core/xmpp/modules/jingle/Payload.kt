
package lib.takina.core.xmpp.modules.jingle

import lib.takina.core.xml.Element
import lib.takina.core.xml.element
import kotlin.jvm.JvmStatic

class Payload(
	val id: Int,
	val channels: Int,
	val clockrate: Int?,
	val maxptime: Int? = null,
	val name: String?,
	val ptime: Int? = null,
	val parameters: List<Parameter>,
	val rtcpFeedbacks: List<RtcpFeedback>,
) {

	fun toElement(): Element {
		return element("payload-type") {
			attribute("id", id.toString())
			if (channels != 1) {
				attribute("channels", channels.toString())
			}
			name?.let { attribute("name", it) }
			clockrate?.let { attribute("clockrate", it.toString()) }
			ptime?.let { attribute("ptime", it.toString()) }
			maxptime?.let { attribute("maxptime", it.toString()) }
			parameters.forEach {
				addChild(it.toElement())
			}
			rtcpFeedbacks.forEach {
				addChild(it.toElement())
			}
		}
	}

	companion object {

		@JvmStatic
		fun parse(el: Element): Payload? {
			if ("payload-type".equals(el.name)) {
				val id = el.attributes["id"]?.toInt() ?: return null
				val channels = el.attributes["channels"]?.toInt() ?: 1
				val clockrate = el.attributes["clockrate"]?.toInt()
				val ptime = el.attributes["ptime"]?.toInt()
				val maxptime = el.attributes["maxptime"]?.toInt()
				val name = el.attributes["name"]
				val parameters = el.children.map { Parameter.parse(it) }
					.filterNotNull()
				val rtcpFeedbacks = el.children.map { RtcpFeedback.parse(it) }
					.filterNotNull()

				return Payload(id, channels, clockrate, maxptime, name, ptime, parameters, rtcpFeedbacks)
			}
			return null
		}
	}

	class Parameter(val name: String, val value: String) {

		fun toElement(): Element {
			return element("parameter") {
				xmlns = "urn:xmpp:jingle:apps:rtp:1"
				attribute("name", name)
				attribute("value", this@Parameter.value)
			}
		}

		companion object {

			@JvmStatic
			fun parse(el: Element): Parameter? {
				if ("parameter".equals(el.name) && "urn:xmpp:jingle:apps:rtp:1".equals(el.xmlns)) {
					val name = el.attributes["name"] ?: return null
					val value = el.attributes["value"] ?: return null
					return Parameter(name, value)
				}
				return null
			}
		}
	}

	class RtcpFeedback(val type: String, val subtype: String?) {

		fun toElement(): Element {
			return element("rtcp-fb") {
				xmlns = "urn:xmpp:jingle:apps:rtp:rtcp-fb:0"
				attribute("type", type)
				subtype?.let { attribute("subtype", it) }
			}
		}

		companion object {

			@JvmStatic
			fun parse(el: Element): RtcpFeedback? {
				if ("rtcp-fb".equals(el.name) && "urn:xmpp:jingle:apps:rtp:rtcp-fb:0".equals(el.xmlns)) {
					val type = el.attributes["type"] ?: return null
					return RtcpFeedback(type, el.attributes["subtype"])
				}
				return null
			}
		}
	}
}