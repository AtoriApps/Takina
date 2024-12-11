
package lib.takina.core.xmpp.modules.jingle

import lib.takina.core.xml.Element
import lib.takina.core.xml.element
import kotlin.jvm.JvmStatic

class HdrExt(val id: String, val uri: String, val senders: Description.Senders) {

	fun toElement(): Element {
		return element("rtp-hdrext") {
			xmlns = "urn:xmpp:jingle:apps:rtp:rtp-hdrext:0"
			attribute("id", id)
			attribute("uri", uri)
			when (senders) {
				Description.Senders.Both -> {
				}

				else -> attribute("senders", senders.name)
			}
		}
	}

	companion object {

		@JvmStatic
		fun parse(el: Element): HdrExt? {
			if ("rtp-hdrext".equals(el.name) && "urn:xmpp:jingle:apps:rtp:rtp-hdrext:0".equals(el.xmlns)) {
				val id = el.attributes["id"] ?: return null
				val uri = el.attributes["uri"] ?: return null
				val senders =
					el.attributes["senders"]?.let { Description.Senders.valueOf(it) } ?: Description.Senders.Both
				return HdrExt(id, uri, senders)
			}
			return null
		}
	}
}