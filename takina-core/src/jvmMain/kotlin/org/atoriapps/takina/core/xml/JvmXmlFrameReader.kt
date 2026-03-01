package org.atoriapps.takina.core.xml

import java.io.InputStream
import java.io.InputStreamReader
import java.io.PushbackReader

internal class JvmXmlFrameReader(inputStream: InputStream) {
    private val reader = PushbackReader(InputStreamReader(inputStream, Charsets.UTF_8), 2)

    fun nextFrame(): String? {
        while (true) {
            val token = readNextTagToken() ?: return null
            val info = parseTagToken(token)
            if (info.isIgnorable || info.isClosingTag) continue

            if (info.tagName == "stream:stream" && !info.selfClosing) return token
            if (info.selfClosing) return token

            val out = StringBuilder(token)
            var depth = 1
            while (depth > 0) {
                val text = readUntilTagStart() ?: return out.toString()
                out.append(text)

                val nested = readTagToken() ?: return out.toString()
                out.append(nested)
                val nestedInfo = parseTagToken(nested)
                if (nestedInfo.isIgnorable) continue
                if (nestedInfo.isClosingTag) depth -= 1 else if (!nestedInfo.selfClosing) depth += 1
            }
            return out.toString()
        }
    }

    private fun readNextTagToken(): String? {
        while (true) {
            readUntilTagStart() ?: return null
            return readTagToken()
        }
    }

    private fun readUntilTagStart(): String? {
        val out = StringBuilder()
        while (true) {
            val ch = reader.read()
            if (ch == -1) return if (out.isEmpty()) null else out.toString()
            val c = ch.toChar()
            if (c == '<') {
                reader.unread(ch)
                return out.toString()
            }
            out.append(c)
        }
    }

    private fun readTagToken(): String? {
        val first = reader.read()
        if (first == -1) return null
        if (first.toChar() != '<') return readTagToken()
        val out = StringBuilder("<")

        val c1 = reader.read()
        if (c1 == -1) return out.toString()
        out.append(c1.toChar())

        val c2 = reader.read()
        if (c2 == -1) return out.toString()
        out.append(c2.toChar())

        val c3 = reader.read()
        if (c3 != -1) out.append(c3.toChar())

        val prefix = out.toString()
        if (prefix.startsWith("<!--")) {
            readUntilSuffix(out, "-->")
            return out.toString()
        }
        if (prefix.startsWith("<![CDATA")) {
            readUntilSuffix(out, "]]>")
            return out.toString()
        }
        if (prefix.startsWith("<?")) {
            readUntilSuffix(out, "?>")
            return out.toString()
        }

        var inSingle = false
        var inDouble = false
        while (true) {
            val c = reader.read()
            if (c == -1) break
            val ch = c.toChar()
            out.append(ch)
            if (ch == '\'' && !inDouble) inSingle = !inSingle
            if (ch == '"' && !inSingle) inDouble = !inDouble
            if (ch == '>' && !inSingle && !inDouble) break
        }
        return out.toString()
    }

    private fun readUntilSuffix(out: StringBuilder, suffix: String) {
        while (true) {
            val c = reader.read()
            if (c == -1) break
            out.append(c.toChar())
            if (out.endsWith(suffix)) break
        }
    }

    private fun StringBuilder.endsWith(suffix: String): Boolean {
        if (length < suffix.length) return false
        return substring(length - suffix.length) == suffix
    }

    private data class TagInfo(
        val tagName: String?,
        val isClosingTag: Boolean,
        val selfClosing: Boolean,
        val isIgnorable: Boolean,
    )

    private fun parseTagToken(token: String): TagInfo {
        val v = token.trim()
        if (v.startsWith("<?") || v.startsWith("<!--") || v.startsWith("<![CDATA")) {
            return TagInfo(null, isClosingTag = false, selfClosing = true, isIgnorable = true)
        }
        if (!v.startsWith("<")) return TagInfo(null, false, true, true)
        val closing = v.startsWith("</")
        val selfClosing = v.endsWith("/>")
        val start = if (closing) 2 else 1
        val name = v.substring(start).takeWhile { !it.isWhitespace() && it != '>' && it != '/' }
        return TagInfo(name.ifBlank { null }, closing, selfClosing, name.isBlank())
    }
}
