
package lib.takina.core.xmpp.modules.jingle

import lib.takina.core.xml.Element
import lib.takina.core.xml.element

data class MessageInitiationDescription(val xmlns: String, val media: String) { companion object {

	fun parse(descEl: Element): MessageInitiationDescription? {
		return descEl.xmlns?.let { xmlns ->
			descEl.attributes["media"]?.let { media ->
				MessageInitiationDescription(xmlns, media)
			}
		}
	}
}

	fun toElement(): Element {
		return element("description") {
			xmlns = this@MessageInitiationDescription.xmlns
			attribute("media", this@MessageInitiationDescription.media)
		}
	}
}