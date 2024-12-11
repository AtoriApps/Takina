
package lib.takina.core.xmpp.modules.jingle

import lib.takina.core.xml.Element
import lib.takina.core.xml.element
import kotlin.jvm.JvmStatic

class SSRC(val ssrc: String, val parameters: List<Parameter>) {

	fun toElement(): Element {
		return element("source") {
			xmlns = "urn:xmpp:jingle:apps:rtp:ssma:0"
			attribute("ssrc", ssrc)
			attribute("id", ssrc)
			parameters.forEach {
				addChild(it.toElement())
			}
		}
	}

	companion object {

		@JvmStatic
		fun parse(el: Element): SSRC? {
			if ("source".equals(el.name) && "urn:xmpp:jingle:apps:rtp:ssma:0".equals(el.xmlns)) {
				val ssrc = el.attributes["ssrc"] ?: el.attributes["id"] ?: return null
				val parameters = el.children.map { Parameter.parse(it) }
					.filterNotNull()
				return SSRC(ssrc, parameters)
			}
			return null
		}
	}

	class Parameter(val name: String, val value: String?) {

		fun toElement(): Element {
			return element("parameter") {
				attribute("name", name)
				this@Parameter.value?.let { attribute("value", it) }
			}
		}

		companion object {

			@JvmStatic
			fun parse(el: Element): Parameter? {
				if ("parameter".equals(el.name)) {
					val name = el.attributes["name"] ?: return null
					val value = el.attributes["value"]
					return Parameter(name, value)
				}
				return null
			}
		}
	}

}