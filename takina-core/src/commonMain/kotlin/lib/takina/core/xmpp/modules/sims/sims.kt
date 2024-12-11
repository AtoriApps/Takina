
package lib.takina.core.xmpp.modules.sims

import lib.takina.core.xml.*
import lib.takina.core.xmpp.stanzas.Stanza

class Reference(val element: Element) : Element by element {

	var begin: Int? by intAttributeProperty()
	var end: Int? by intAttributeProperty()
	var type: String? by stringAttributeProperty()
	var uri: String? by stringAttributeProperty()
	var anchor: String? by stringAttributeProperty()
}

class File(val element: Element) : Element by element {

	var mediaType: String? by stringElementProperty("media-type")
	var fileName: String? by stringElementProperty("name")
	var fileSize: Int? by intElementProperty("size")
	var fileDescription: String? by stringElementProperty("desc")

}

fun Stanza<*>.getReferenceOrNull(): Reference? =
	getChildrenNS("reference", ReferenceModule.XMLNS)?.let { Reference(it) }

fun Reference.getMediaSharingFileOrNull(): File? = this.getChildrenNS("media-sharing", "urn:xmpp:sims:1")
	?.getChildrenNS("file", "urn:xmpp:jingle:apps:file-transfer:5")
	?.let { File(it) }

fun createFileSharingReference(
	uri: String, fileName: String?, mediaType: String, fileSize: Int?, fileDescription: String?,
): Reference {
	return Reference(element("reference") {
		xmlns = "urn:xmpp:reference:0"
		attributes["type"] = "data"
		attributes["uri"] = uri
		"media-sharing" {
			xmlns = "urn:xmpp:sims:1"
			"file" {
				xmlns = "urn:xmpp:jingle:apps:file-transfer:5"
				"media-type" { +mediaType }
				fileName?.let { "name" { +it } }
				fileDescription?.let { "desc" { +it } }
				fileSize?.let { "size" { +"$it" } }
			}
		}
	})
}