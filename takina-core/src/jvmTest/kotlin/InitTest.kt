package org.atoriapps.takina.core

import org.atoriapps.takina.core.connections.ConnectionConfig
import org.atoriapps.takina.core.connections.Connector
import org.atoriapps.takina.core.connections.SecurityMode
import org.atoriapps.takina.core.xmpp.toBareJid
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertTrue

class JvmInitTest {
    @Test
    fun connector_plainSocket_shouldSendPayload() {
        val server = ServerSocket(0)
        val received = ByteArrayOutputStream()

        val receiver = thread(start = true) {
            server.accept().use { socket ->
                val input = socket.getInputStream()
                socket.soTimeout = 1_000
                val buffer = ByteArray(1024)
                while (true) {
                    val read = runCatching { input.read(buffer) }.getOrElse { -1 }
                    if (read <= 0) break
                    received.write(buffer, 0, read)
                }
            }
        }

        val connector = Connector()
        val config = ConnectionConfig(
            jid = "alice@example.com".toBareJid(),
            host = "127.0.0.1",
            securityMode = SecurityMode.PLAIN,
            port = server.localPort,
            connectTimeoutMillis = 3_000,
        )
        connector.connect(config)
        connector.send("<stream:stream/>")
        connector.close()

        receiver.join(3_000)
        server.close()

        val payload = received.toString(Charsets.UTF_8.name())
        assertTrue(payload.contains("<stream:stream/>"), "payload should include sent XML")
    }
}
