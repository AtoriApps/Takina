package org.atoriapps.takina.core.xml

object XmlParser {
    private val openingTagRegex = Regex("""<([A-Za-z_:][A-Za-z0-9_.:-]*)\b""")
    private val attributeRegex = Regex("""([A-Za-z_:][A-Za-z0-9_.:-]*)\s*=\s*(['"])(.*?)\2""")

    data class LightweightParseResult(
        val rootName: String,
        val attributes: Map<String, String>,
    )

    /**
     * Lightweight parser for first element only.
     * This intentionally does not parse nested children; it is for protocol routing checks.
     */
    fun parseRoot(xml: String): LightweightParseResult {
        val trimmed = xml.trim()
        val rootMatch = openingTagRegex.find(trimmed)
            ?: throw IllegalArgumentException("invalid xml: missing opening tag")
        val rootName = rootMatch.groupValues[1]

        val rawTag = trimmed.substring(rootMatch.range.first, trimmed.indexOf('>', rootMatch.range.first) + 1)
        val attrs = buildMap {
            attributeRegex.findAll(rawTag).forEach { match ->
                put(match.groupValues[1], match.groupValues[3])
            }
        }
        return LightweightParseResult(rootName, attrs)
    }
}
