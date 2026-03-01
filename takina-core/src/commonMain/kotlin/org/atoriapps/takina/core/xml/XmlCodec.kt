package org.atoriapps.takina.core.xml

object XmlWriter {
    fun render(node: XmlNode): String = when (node) {
        is XmlText -> escapeText(node.text)
        is XmlElement -> renderElement(node)
    }

    fun startTag(name: String, attributes: Map<String, String> = emptyMap()): String {
        val attrs = if (attributes.isEmpty()) "" else attributes.entries.joinToString(
            separator = " ",
            prefix = " ",
        ) { (key, value) -> "$key='${escapeAttribute(value)}'" }
        return "<$name$attrs>"
    }

    fun escapeText(value: String): String = buildString(value.length) {
        for (c in value) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                else -> append(c)
            }
        }
    }

    fun escapeAttribute(value: String): String = buildString(value.length) {
        for (c in value) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(c)
            }
        }
    }

    private fun renderElement(element: XmlElement): String {
        val attrs = if (element.attributes.isEmpty()) "" else element.attributes.entries.joinToString(
            separator = " ",
            prefix = " ",
        ) { (key, value) -> "$key='${escapeAttribute(value)}'" }
        if (element.selfClosing || element.children.isEmpty()) return "<${element.name}$attrs/>"
        val children = element.children.joinToString(separator = "") { render(it) }
        return "<${element.name}$attrs>$children</${element.name}>"
    }
}

object XmlParser {
    fun parseElementOrNull(xml: String): XmlElement? = runCatching {
        val cursor = Cursor(xml.trim())
        cursor.skipMisc()
        val element = parseElement(cursor)
        cursor.skipMisc()
        element
    }.getOrNull()

    fun parseStartTagOrNull(xml: String): XmlElement? = runCatching {
        val cursor = Cursor(xml.trim())
        cursor.skipMisc()
        parseStartTag(cursor)
    }.getOrNull()

    private fun parseElement(cursor: Cursor): XmlElement {
        val open = parseOpenTag(cursor)
        if (open.selfClosing) return XmlElement(name = open.name, attributes = open.attributes, selfClosing = true)

        val children = mutableListOf<XmlNode>()
        while (true) {
            cursor.skipWhitespace()
            if (cursor.peekStartsWith("</")) {
                cursor.consume("</")
                val closing = cursor.readName()
                if (closing != open.name) error("Mismatched closing tag: expected ${open.name}, got $closing")
                cursor.skipWhitespace()
                cursor.consume(">")
                return XmlElement(name = open.name, attributes = open.attributes, children = children)
            }
            if (cursor.peekStartsWith("<![CDATA[")) {
                children += XmlText(cursor.readCData())
                continue
            }
            if (cursor.peekStartsWith("<!--")) {
                cursor.skipComment()
                continue
            }
            if (cursor.peekStartsWith("<?")) {
                cursor.skipProcessingInstruction()
                continue
            }
            if (cursor.peek() == '<') children += parseElement(cursor) else {
                val text = cursor.readTextUntil('<')
                if (text.isNotBlank()) children += XmlText(unescape(text))
            }
        }
    }

    private fun parseStartTag(cursor: Cursor): XmlElement {
        val open = parseOpenTag(cursor)
        return XmlElement(
            name = open.name,
            attributes = open.attributes,
            selfClosing = open.selfClosing,
        )
    }

    private fun parseOpenTag(cursor: Cursor): OpenTag {
        cursor.skipWhitespace()
        cursor.consume("<")
        if (cursor.peek() == '/') error("Closing tag is not an open tag")
        val name = cursor.readName()
        val attrs = linkedMapOf<String, String>()

        while (true) {
            cursor.skipWhitespace()
            when {
                cursor.peekStartsWith("/>") -> {
                    cursor.consume("/>")
                    return OpenTag(name = name, attributes = attrs, selfClosing = true)
                }

                cursor.peek() == '>' -> {
                    cursor.consume(">")
                    return OpenTag(name = name, attributes = attrs, selfClosing = false)
                }

                else -> {
                    val attrName = cursor.readName()
                    cursor.skipWhitespace()
                    cursor.consume("=")
                    cursor.skipWhitespace()
                    val attrValue = unescape(cursor.readQuotedValue())
                    attrs[attrName] = attrValue
                }
            }
        }
    }

    private data class OpenTag(
        val name: String,
        val attributes: Map<String, String>,
        val selfClosing: Boolean,
    )

    private fun Cursor.skipMisc() {
        while (true) {
            skipWhitespace()
            when {
                peekStartsWith("<!--") -> skipComment()
                peekStartsWith("<?") -> skipProcessingInstruction()
                else -> return
            }
        }
    }

    private class Cursor(
        private val source: String,
    ) {
        var index: Int = 0

        fun peek(): Char? = if (index < source.length) source[index] else null

        fun peekStartsWith(value: String): Boolean = source.startsWith(value, index)

        fun skipWhitespace() {
            while (peek()?.isWhitespace() == true) index += 1
        }

        fun consume(expected: String) {
            if (!source.startsWith(expected, index)) error("Expected '$expected' at $index")
            index += expected.length
        }

        fun readName(): String {
            val start = index
            while (true) {
                val c = peek() ?: break
                if (c.isWhitespace() || c == '=' || c == '/' || c == '>') break
                index += 1
            }
            if (index == start) error("Expected name at $index")
            return source.substring(start, index)
        }

        fun readQuotedValue(): String {
            val quote = peek()
            if (quote != '\'' && quote != '"') error("Expected quote at $index")
            index += 1
            val start = index
            while (peek() != quote) {
                if (peek() == null) error("Unexpected EOF in quoted value")
                index += 1
            }
            val value = source.substring(start, index)
            index += 1
            return value
        }

        fun readTextUntil(stop: Char): String {
            val start = index
            while (peek() != null && peek() != stop) index += 1
            return source.substring(start, index)
        }

        fun readCData(): String {
            consume("<![CDATA[")
            val end = source.indexOf("]]>", index)
            if (end < 0) error("Unclosed CDATA")
            val out = source.substring(index, end)
            index = end + 3
            return out
        }

        fun skipComment() {
            consume("<!--")
            val end = source.indexOf("-->", index)
            if (end < 0) error("Unclosed comment")
            index = end + 3
        }

        fun skipProcessingInstruction() {
            consume("<?")
            val end = source.indexOf("?>", index)
            if (end < 0) error("Unclosed processing instruction")
            index = end + 2
        }
    }

    private fun unescape(value: String): String = value.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
}
