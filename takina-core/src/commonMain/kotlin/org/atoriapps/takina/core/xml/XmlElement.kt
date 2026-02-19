package org.atoriapps.takina.core.xml

data class XmlElement(
    val name: String,
    val namespace: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val children: List<XmlElement> = emptyList(),
    val text: String? = null,
) {
    init {
        require(name.isNotBlank()) { "xml element name cannot be blank" }
    }

    fun toXmlString(): String {
        val attrs = buildString {
            if (namespace != null) append(" xmlns='").append(XmlEscaper.escape(namespace)).append("'")
            attributes.forEach { (key, value) ->
                append(" ")
                append(key)
                append("='")
                append(XmlEscaper.escape(value))
                append("'")
            }
        }

        if (children.isEmpty() && text == null) {
            return "<$name$attrs/>"
        }

        val childXml = children.joinToString(separator = "") { it.toXmlString() }
        val escapedText = text?.let(XmlEscaper::escape).orEmpty()
        return "<$name$attrs>$escapedText$childXml</$name>"
    }
}

object XmlEscaper {
    fun escape(value: String): String = buildString(value.length + 8) {
        value.forEach { ch ->
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(ch)
            }
        }
    }
}
