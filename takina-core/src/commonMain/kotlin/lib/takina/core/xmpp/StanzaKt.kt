
package lib.takina.core.xmpp

fun lib.takina.core.xml.Element.getFromAttr(): FullJID? = this.attributes["from"]?.toFullJID()

fun lib.takina.core.xml.Element.getToAttr(): FullJID? = this.attributes["to"]?.toFullJID()

fun lib.takina.core.xml.Element.getIdAttr(): String? {
	return this.attributes["id"]
}