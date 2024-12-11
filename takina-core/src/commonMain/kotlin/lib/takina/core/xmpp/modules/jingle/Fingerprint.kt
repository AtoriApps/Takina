
package lib.takina.core.xmpp.modules.jingle

import lib.takina.core.xml.Element
import lib.takina.core.xml.element
import kotlin.jvm.JvmStatic

class Fingerprint(val hash: String, val value: String, val setup: Setup) {

	enum class Setup {
		actpass,
		active,
		passive
	}

	fun toElement(): Element {
		return element("fingerprint") {
			xmlns = "urn:xmpp:jingle:apps:dtls:0"
			attribute("hash", hash)
			attribute("setup", setup.name)
			value = this@Fingerprint.value
		}
	}

	companion object {

		@JvmStatic
		fun parse(el: Element): Fingerprint? {
			if ("fingerprint".equals(el.name) && "urn:xmpp:jingle:apps:dtls:0".equals(el.xmlns)) {
				val hash = el.attributes["hash"] ?: return null
				val setup = el.attributes["setup"]?.let { Setup.valueOf(it) } ?: return null
				val value = el.value ?: return null
				return Fingerprint(hash, value, setup)
			}
			return null
		}
	}
}