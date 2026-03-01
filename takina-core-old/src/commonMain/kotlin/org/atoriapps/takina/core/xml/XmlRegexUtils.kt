package org.atoriapps.takina.core.xml

object XmlRegexUtils {
    private val attributeRegex = Regex("""([A-Za-z_:][A-Za-z0-9_.:-]*)\s*=\s*(['"])(.*?)\2""")
    private val openRegexCache = linkedMapOf<String, Regex>()
    private val closeRegexCache = linkedMapOf<String, Regex>()

    fun parseAttributes(raw: String): Map<String, String> = buildMap {
        attributeRegex.findAll(raw).forEach { match -> put(match.groupValues[1], match.groupValues[3]) }
    }

    fun extractNamespace(attributes: Map<String, String>): String? = attributes["xmlns"] ?: attributes.entries.firstOrNull { it.key.startsWith("xmlns:") }?.value

    fun findElementBounds(xml: String, localName: String, fromIndex: Int = 0): IntRange? {
        val openRegex = openTagRegex(localName)
        val closeRegex = closeTagRegex(localName)
        val open = openRegex.find(xml, fromIndex) ?: return null
        if (open.value.endsWith("/>")) return open.range
        var depth = 1
        var index = open.range.last + 1
        while (depth > 0) {
            val nextOpen = openRegex.find(xml, index)
            val nextClose = closeRegex.find(xml, index) ?: return null
            if (nextOpen != null && nextOpen.range.first < nextClose.range.first) {
                if (!nextOpen.value.endsWith("/>")) depth += 1
                index = nextOpen.range.last + 1
                continue
            }
            depth -= 1
            if (depth == 0) return open.range.first..nextClose.range.last
            index = nextClose.range.last + 1
        }

        return null
    }

    private fun openTagRegex(localName: String): Regex = synchronized(openRegexCache) {
        openRegexCache[localName] ?: Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?${Regex.escape(localName)}\b[^>]*>""").also { openRegexCache[localName] = it }
    }

    private fun closeTagRegex(localName: String): Regex = synchronized(closeRegexCache) {
        closeRegexCache[localName] ?: Regex("""</\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?${Regex.escape(localName)}\s*>""").also { closeRegexCache[localName] = it }
    }
}
