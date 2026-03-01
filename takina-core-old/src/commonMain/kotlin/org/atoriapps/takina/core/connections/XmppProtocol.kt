package org.atoriapps.takina.core.connections

import org.atoriapps.takina.core.exceptions.TakinaConnectionException
import org.atoriapps.takina.core.xml.XmlParser

internal object XmppProtocol {
    fun localName(qualifiedName: String): String = qualifiedName.substringAfter(':')

    fun rootName(xml: String): String {
        if (xml.trimStart().startsWith("</")) {
            return xml.substringAfter("</").substringBefore('>').substringAfter(':')
        }
        return localName(XmlParser.parseRoot(xml).rootName)
    }

    fun rootAttributes(xml: String): Map<String, String> = XmlParser.parseRoot(xml).attributes

    fun containsStartTls(featuresXml: String): Boolean =
        Regex("""<\s*starttls(\s|>)""").containsMatchIn(featuresXml)

    fun containsMechanism(featuresXml: String, mechanism: String): Boolean =
        Regex("""<\s*mechanism\s*>\s*${Regex.escape(mechanism)}\s*<\s*/\s*mechanism\s*>""")
            .containsMatchIn(featuresXml)

    fun containsStreamManagement(featuresXml: String): Boolean =
        Regex("""<\s*sm\b[^>]*xmlns\s*=\s*['"]urn:xmpp:sm:3['"][^>]*/?>""")
            .containsMatchIn(featuresXml)

    fun isSaslSuccess(xml: String): Boolean = rootName(xml) == "success"

    fun isStartTlsProceed(xml: String): Boolean = rootName(xml) == "proceed"

    fun isFailure(xml: String): Boolean = rootName(xml) == "failure"

    fun expectRoot(xml: String, expectedLocalName: String, errorPrefix: String) {
        val actual = rootName(xml)
        if (actual != expectedLocalName) {
            throw TakinaConnectionException("$errorPrefix: expected <$expectedLocalName>, got <$actual>")
        }
    }
}
