
package lib.takina.core.xmpp.modules.jingle

import lib.takina.core.xml.Element
import lib.takina.core.xml.element
import kotlin.jvm.JvmStatic

class Encryption(val cryptoSuite: String, val keyParams: String, val sessionParams: String?, val tag: String) {

	fun toElement(): Element {
		return element("crypto") {
			attribute("crypto-suite", cryptoSuite)
			attribute("key-params", keyParams)
			sessionParams?.let { attribute("session-params", it) }
			attribute("tag", tag)
		}
	}

	companion object {

		@JvmStatic
		fun parse(el: Element): Encryption? {
			if ("crypto".equals(el.name)) {
				val cryptoSuite = el.attributes["crypto-suite"] ?: return null
				val keyParams = el.attributes["key-params"] ?: return null
				val sessionParams = el.attributes["session-params"]
				val tag = el.attributes["tag"] ?: return null

				return Encryption(cryptoSuite, keyParams, sessionParams, tag)
			}
			return null
		}
	}
}