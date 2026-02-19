package org.atoriapps.takina.core.connections

import java.io.BufferedWriter
import java.io.EOFException
import java.io.OutputStreamWriter
import java.io.PushbackReader
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

actual class Connector actual constructor() : AbstractConnector() {
    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    private var reader: PushbackReader? = null

    private val pumpRunning = AtomicBoolean(false)
    private var pumpThread: Thread? = null

    override val isConnected: Boolean
        get() = socket?.isConnected == true && socket?.isClosed == false

    override fun connect(config: ConnectionConfig) {
        close()

        val rawSocket = Socket()
        rawSocket.soTimeout = config.connectTimeoutMillis
        rawSocket.connect(InetSocketAddress(config.host, config.port), config.connectTimeoutMillis)

        val activeSocket = when (config.securityMode) {
            SecurityMode.DIRECT_TLS -> wrapTls(rawSocket, config.host, config.port)
            SecurityMode.START_TLS, SecurityMode.PLAIN -> rawSocket
        }
        installIo(activeSocket)
    }

    override fun send(xml: String) {
        val activeWriter = writer ?: error("connector is not connected")
        activeWriter.write(xml)
        activeWriter.flush()
    }

    override fun readFrame(timeoutMillis: Int): String {
        val activeSocket = socket ?: error("connector is not connected")
        val activeReader = reader ?: error("connector is not connected")
        val previous = activeSocket.soTimeout
        if (timeoutMillis >= 0) {
            activeSocket.soTimeout = timeoutMillis
        }
        return try {
            readNextFrame(activeReader)
        } finally {
            runCatching { activeSocket.soTimeout = previous }
        }
    }

    override fun upgradeToTls(config: ConnectionConfig) {
        val activeSocket = socket ?: error("connector is not connected")
        if (activeSocket is SSLSocket) return
        installIo(wrapTls(activeSocket, config.host, config.port))
    }

    override fun startFramePump(
        onFrame: (String) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        stopFramePump()
        pumpRunning.set(true)
        pumpThread = Thread(
            {
                try {
                    while (pumpRunning.get() && isConnected) {
                        val frame = readFrame(0)
                        onFrame(frame)
                    }
                } catch (t: Throwable) {
                    if (pumpRunning.get()) onError(t)
                }
            },
            "takina-frame-pump-${hashCode()}",
        ).apply {
            isDaemon = true
            start()
        }
    }

    override fun stopFramePump() {
        pumpRunning.set(false)
        pumpThread?.interrupt()
        pumpThread = null
    }

    override fun close() {
        stopFramePump()
        runCatching { writer?.flush() }
        runCatching { writer?.close() }
        runCatching { reader?.close() }
        runCatching { socket?.close() }
        writer = null
        reader = null
        socket = null
    }

    private fun installIo(nextSocket: Socket) {
        socket = nextSocket
        writer = BufferedWriter(OutputStreamWriter(nextSocket.getOutputStream(), StandardCharsets.UTF_8))
        reader = PushbackReader(nextSocket.getInputStream().reader(StandardCharsets.UTF_8), 8)
    }

    private fun wrapTls(baseSocket: Socket, host: String, port: Int): SSLSocket {
        val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
        return (factory.createSocket(baseSocket, host, port, true) as SSLSocket).apply {
            useClientMode = true
            startHandshake()
        }
    }

    private fun readNextFrame(reader: PushbackReader): String {
        while (true) {
            val token = readNextToken(reader)
            when {
                token.startsWith("<?") -> continue
                token.startsWith("<!--") -> continue
                token.startsWith("</") -> return token
            }

            val name = parseTagName(token) ?: continue
            if (name == "stream:stream") return token
            if (isSelfClosing(token)) return token
            return token + readElementTail(reader)
        }
    }

    private fun readElementTail(reader: PushbackReader): String {
        val builder = StringBuilder()
        var depth = 1
        while (depth > 0) {
            val segment = readNextTokenWithLeadingText(reader)
            builder.append(segment)

            val tokenStart = segment.lastIndexOf('<')
            if (tokenStart == -1) continue
            val token = segment.substring(tokenStart)
            when {
                token.startsWith("<?") -> Unit
                token.startsWith("<!--") -> Unit
                token.startsWith("<![CDATA[") -> Unit
                token.startsWith("<!") -> Unit
                token.startsWith("</") -> depth -= 1
                isSelfClosing(token) -> Unit
                else -> depth += 1
            }
        }
        return builder.toString()
    }

    private fun readNextToken(reader: PushbackReader): String {
        val segment = readNextTokenWithLeadingText(reader)
        val tokenStart = segment.lastIndexOf('<')
        if (tokenStart == -1) throw EOFException("unexpected stream content without XML token")
        return segment.substring(tokenStart)
    }

    private fun readNextTokenWithLeadingText(reader: PushbackReader): String {
        val builder = StringBuilder()
        while (true) {
            val next = reader.read()
            if (next == -1) throw EOFException("connection closed while waiting for frame")
            val ch = next.toChar()
            builder.append(ch)
            if (ch == '<') {
                builder.append(readTokenTail(reader))
                return builder.toString()
            }
        }
    }

    private fun readTokenTail(reader: PushbackReader): String {
        val builder = StringBuilder()
        val first = reader.read()
        if (first == -1) throw EOFException("unexpected end of stream after '<'")
        val c1 = first.toChar()
        builder.append(c1)

        return when (c1) {
            '?' -> {
                readUntilSequence(reader, builder, "?>")
                builder.toString()
            }

            '!' -> {
                val second = reader.read()
                if (second == -1) throw EOFException("unexpected end of stream in declaration")
                val c2 = second.toChar()
                builder.append(c2)
                when (c2) {
                    '-' -> {
                        val third = reader.read()
                        if (third == -1) throw EOFException("unexpected end of stream in comment")
                        val c3 = third.toChar()
                        builder.append(c3)
                        if (c3 == '-') {
                            readUntilSequence(reader, builder, "-->")
                        } else {
                            readNormalTagTail(reader, builder)
                        }
                    }

                    '[' -> {
                        readUntilSequence(reader, builder, "]]>")
                    }

                    else -> {
                        readNormalTagTail(reader, builder)
                    }
                }
                builder.toString()
            }

            else -> {
                readNormalTagTail(reader, builder)
                builder.toString()
            }
        }
    }

    private fun readNormalTagTail(reader: PushbackReader, builder: StringBuilder) {
        var quote: Char? = null
        while (true) {
            val next = reader.read()
            if (next == -1) throw EOFException("unexpected end of stream in tag")
            val ch = next.toChar()
            builder.append(ch)

            if (quote != null) {
                if (ch == quote) quote = null
                continue
            }

            if (ch == '"' || ch == '\'') {
                quote = ch
                continue
            }

            if (ch == '>') return
        }
    }

    private fun readUntilSequence(
        reader: PushbackReader,
        builder: StringBuilder,
        suffix: String,
    ) {
        while (true) {
            val next = reader.read()
            if (next == -1) throw EOFException("unexpected end of stream while waiting for '$suffix'")
            builder.append(next.toChar())
            if (builder.endsWith(suffix)) return
        }
    }

    private fun parseTagName(token: String): String? {
        val trimmed = token.trim()
        val body = when {
            trimmed.startsWith("</") -> trimmed.removePrefix("</")
            trimmed.startsWith("<") -> trimmed.removePrefix("<")
            else -> return null
        }
        return body
            .trimStart()
            .takeWhile { !it.isWhitespace() && it != '/' && it != '>' }
            .ifBlank { null }
    }

    private fun isSelfClosing(token: String): Boolean = token.trimEnd().endsWith("/>")

    private fun StringBuilder.endsWith(suffix: String): Boolean {
        if (length < suffix.length) return false
        for (i in suffix.indices) {
            if (this[length - suffix.length + i] != suffix[i]) return false
        }
        return true
    }
}
