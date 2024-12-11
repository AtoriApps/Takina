
package lib.takina.core.xmpp.modules.jingle

import lib.takina.core.xml.Element
import lib.takina.core.xml.element
import kotlin.jvm.JvmStatic

class Transport(val ufrag: String?, val pwd: String?, val candidates: List<Candidate>, val fingerprint: Fingerprint?) {

	fun toElement(): Element {
		return element("transport") {
			xmlns = XMLNS
			fingerprint?.let {
				this.addChild(it.toElement())
			}
			candidates.forEach {
				this.addChild(it.toElement())
			}
			ufrag?.let { attribute("ufrag", it) }
			pwd?.let { attribute("pwd", it) }
		}
	}

	companion object {

		const val XMLNS = "urn:xmpp:jingle:transports:ice-udp:1"

		val supportedFeatures = arrayOf(XMLNS, "urn:xmpp:jingle:apps:dtls:0")

		@JvmStatic
		fun parse(el: Element): Transport? {
			if (!("transport".equals(el.name) && XMLNS.equals(el.xmlns))) {
				return null
			}
			val candidates: List<Candidate> = el.children.map { Candidate.parse(it) }
				.filterNotNull()
			val fingerprint = el.children.map { Fingerprint.parse(it) }
				.firstOrNull()
			return Transport(el.attributes["ufrag"], el.attributes["pwd"], candidates, fingerprint)
		}
	}
}