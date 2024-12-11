
package lib.takina.core.xmpp.modules.jingle

import lib.takina.core.xml.Element
import lib.takina.core.xml.element
import kotlin.jvm.JvmStatic

class SSRCGroup(val semantics: String, val sources: List<String>) {

	fun toElement(): Element {
		return element("ssrc-group") {
			xmlns = "urn:xmpp:jingle:apps:rtp:ssma:0"
			attribute("semantics", semantics)
			sources.forEach {
				element("source") {
					attribute("ssrc", it)
				}
			}
		}
	}

	companion object {

		@JvmStatic
		fun parse(el: Element): SSRCGroup? {
			if ("ssrc-group".equals(el.name) && "urn:xmpp:jingle:apps:rtp:ssma:0".equals(el.xmlns)) {
				val semantics = el.attributes["semantics"] ?: return null
				val sources = el.children.filter { "source".equals(it.name) }
					.map { it.attributes["ssrc"] }
					.filterNotNull()
				return SSRCGroup(semantics, sources)
			}
			return null
		}
	}
}