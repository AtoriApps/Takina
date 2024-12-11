
package lib.takina.core.xml.parser

interface SimpleHandler {

	fun error(errorMessage: String)

	fun startElement(name: String, attrNames: Array<String?>?, attrValues: Array<String?>?)

	fun elementCData(cdata: String)

	fun endElement(name: String): Boolean

	fun otherXML(other: String)

	fun saveParserState(state: Any?)

	fun restoreParserState(): Any?

}