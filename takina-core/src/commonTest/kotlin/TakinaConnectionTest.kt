package org.atoriapps.takina.core

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.atoriapps.takina.core.connections.AbstractConnector
import org.atoriapps.takina.core.connections.ConnectionLifecycleStage
import org.atoriapps.takina.core.connections.ConnectionConfig
import org.atoriapps.takina.core.connections.IqResult
import org.atoriapps.takina.core.connections.SecurityMode
import org.atoriapps.takina.core.connections.TakinaConnection
import org.atoriapps.takina.core.exceptions.TakinaConnectionException
import org.atoriapps.takina.core.xmpp.toBareJid
import org.atoriapps.takina.core.xmpp.stanzas.IqStanza
import org.atoriapps.takina.core.xmpp.stanzas.IqType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TakinaConnectionTest {
    @Test
    fun startTlsHandshake_shouldPerformTlsAuthAndBind() {
        val stages = mutableListOf<ConnectionLifecycleStage>()
        val fake = FakeConnector(
            frames = listOf(
                "<stream:stream xmlns:stream='http://etherx.jabber.org/streams'/>",
                "<stream:features><starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/><mechanisms xmlns='urn:ietf:params:xml:ns:xmpp-sasl'><mechanism>PLAIN</mechanism></mechanisms></stream:features>",
                "<proceed xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>",
                "<stream:stream xmlns:stream='http://etherx.jabber.org/streams'/>",
                "<stream:features><mechanisms xmlns='urn:ietf:params:xml:ns:xmpp-sasl'><mechanism>PLAIN</mechanism></mechanisms></stream:features>",
                "<success xmlns='urn:ietf:params:xml:ns:xmpp-sasl'/>",
                "<stream:stream xmlns:stream='http://etherx.jabber.org/streams'/>",
                "<stream:features><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/></stream:features>",
                "__BIND_RESULT__",
            ),
        )
        val config = ConnectionConfig(
            jid = "alice@example.com".toBareJid(),
            host = "example.com",
            securityMode = SecurityMode.START_TLS,
            port = 5222,
            resource = "takina",
        )

        val connection = TakinaConnection(
            config = config,
            passwordProvider = { "password" },
            connectorFactory = { fake },
            onLifecycleStageChanged = { _, _, newStage -> stages += newStage },
        )
        connection.connect()

        assertEquals(TakinaConnection.ConnectionState.CONNECTED, connection.state)
        assertEquals(ConnectionLifecycleStage.ONLINE, connection.lifecycleStage)
        assertTrue(fake.upgradedToTls, "connector should be upgraded to TLS after STARTTLS")
        assertTrue(fake.sent.any { it.contains("<starttls") }, "should send STARTTLS request")
        assertTrue(fake.sent.any { it.contains("<auth") }, "should send SASL auth")
        assertTrue(fake.sent.any { it.contains("<bind") }, "should send bind iq")
        assertTrue(stages.contains(ConnectionLifecycleStage.TLS_NEGOTIATED), "should have tls stage transition")
        assertTrue(stages.contains(ConnectionLifecycleStage.AUTHENTICATED), "should have auth stage transition")
        assertEquals(stages.lastOrNull(), ConnectionLifecycleStage.ONLINE, "last stage should be ONLINE")
    }

    @Test
    fun plainHandshake_withoutPlainMechanism_shouldFail() {
        val fake = FakeConnector(
            frames = listOf(
                "<stream:stream xmlns:stream='http://etherx.jabber.org/streams'/>",
                "<stream:features><mechanisms xmlns='urn:ietf:params:xml:ns:xmpp-sasl'><mechanism>SCRAM-SHA-1</mechanism></mechanisms></stream:features>",
            ),
        )
        val config = ConnectionConfig(
            jid = "alice@example.com".toBareJid(),
            host = "example.com",
            securityMode = SecurityMode.PLAIN,
            port = 5222,
            resource = "takina",
        )

        val connection = TakinaConnection(
            config = config,
            passwordProvider = { "password" },
            connectorFactory = { fake },
        )

        assertFailsWith<TakinaConnectionException> {
            connection.connect()
        }
    }

    @Test
    fun connection_shouldExposeSmSupportFromFeatures() {
        val fake = FakeConnector(
            frames = listOf(
                "<stream:stream xmlns:stream='http://etherx.jabber.org/streams'/>",
                "<stream:features><sm xmlns='urn:xmpp:sm:3'/><mechanisms xmlns='urn:ietf:params:xml:ns:xmpp-sasl'><mechanism>PLAIN</mechanism></mechanisms></stream:features>",
                "<success xmlns='urn:ietf:params:xml:ns:xmpp-sasl'/>",
                "<stream:stream xmlns:stream='http://etherx.jabber.org/streams'/>",
                "<stream:features><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/><sm xmlns='urn:xmpp:sm:3'/></stream:features>",
                "__BIND_RESULT__",
            ),
        )
        val config = ConnectionConfig(
            jid = "alice@example.com".toBareJid(),
            host = "example.com",
            securityMode = SecurityMode.PLAIN,
            port = 5222,
            resource = "takina",
        )
        val connection = TakinaConnection(
            config = config,
            passwordProvider = { "password" },
            connectorFactory = { fake },
        )

        connection.connect()
        assertTrue(connection.supportsStreamManagement)
    }

    @Test
    fun connectedConnection_shouldDeliverInboundStanzaFromPump() {
        val fake = FakeConnector(
            frames = listOf(
                "<stream:stream xmlns:stream='http://etherx.jabber.org/streams'/>",
                "<stream:features><mechanisms xmlns='urn:ietf:params:xml:ns:xmpp-sasl'><mechanism>PLAIN</mechanism></mechanisms></stream:features>",
                "<success xmlns='urn:ietf:params:xml:ns:xmpp-sasl'/>",
                "<stream:stream xmlns:stream='http://etherx.jabber.org/streams'/>",
                "<stream:features><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/></stream:features>",
                "__BIND_RESULT__",
            ),
        )
        val config = ConnectionConfig(
            jid = "alice@example.com".toBareJid(),
            host = "example.com",
            securityMode = SecurityMode.PLAIN,
            port = 5222,
            resource = "takina",
        )

        var inboundXml: String? = null
        val connection = TakinaConnection(
            config = config,
            passwordProvider = { "password" },
            connectorFactory = { fake },
            onInboundStanza = { _, xml -> inboundXml = xml },
        )
        connection.connect()
        fake.emitFromPump("<message from='bob@example.com' to='alice@example.com'><body>hello</body></message>")

        assertEquals(inboundXml?.contains("<message"), true)
    }

    @Test
    fun connectedConnection_shouldDeliverNonStanzaFrameToInboundFrameCallback() {
        val fake = FakeConnector(
            frames = listOf(
                "<stream:stream xmlns:stream='http://etherx.jabber.org/streams'/>",
                "<stream:features><mechanisms xmlns='urn:ietf:params:xml:ns:xmpp-sasl'><mechanism>PLAIN</mechanism></mechanisms></stream:features>",
                "<success xmlns='urn:ietf:params:xml:ns:xmpp-sasl'/>",
                "<stream:stream xmlns:stream='http://etherx.jabber.org/streams'/>",
                "<stream:features><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/></stream:features>",
                "__BIND_RESULT__",
            ),
        )
        val config = ConnectionConfig(
            jid = "alice@example.com".toBareJid(),
            host = "example.com",
            securityMode = SecurityMode.PLAIN,
            port = 5222,
            resource = "takina",
        )

        var inboundFrameXml: String? = null
        val connection = TakinaConnection(
            config = config,
            passwordProvider = { "password" },
            connectorFactory = { fake },
            onInboundFrame = { _, xml -> inboundFrameXml = xml },
        )
        connection.connect()
        fake.emitFromPump("<r xmlns='urn:xmpp:sm:3'/>")

        assertEquals("<r xmlns='urn:xmpp:sm:3'/>", inboundFrameXml)
    }

    @Test
    fun iqAwait_shouldReturnResultFrame() {
        runBlocking {
            val fake = FakeConnector(
                frames = listOf(
                    "<stream:stream xmlns:stream='http://etherx.jabber.org/streams'/>",
                    "<stream:features><mechanisms xmlns='urn:ietf:params:xml:ns:xmpp-sasl'><mechanism>PLAIN</mechanism></mechanisms></stream:features>",
                    "<success xmlns='urn:ietf:params:xml:ns:xmpp-sasl'/>",
                    "<stream:stream xmlns:stream='http://etherx.jabber.org/streams'/>",
                    "<stream:features><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/></stream:features>",
                    "__BIND_RESULT__",
                ),
            )
            val config = ConnectionConfig(
                jid = "alice@example.com".toBareJid(),
                host = "example.com",
                securityMode = SecurityMode.PLAIN,
                port = 5222,
                resource = "takina",
            )
            val connection = TakinaConnection(
                config = config,
                passwordProvider = { "password" },
                connectorFactory = { fake },
            )
            connection.connect()

            val query = IqStanza(
                id = "iq-test-1",
                from = "alice@example.com".toBareJid(),
                to = "example.com".toBareJid(),
                type = IqType.GET,
            )

            val waiting = async {
                connection.sendIqAndAwaitResult(query, timeoutMillis = 3_000)
            }
            delay(50)
            fake.emitFromPump("<iq id='iq-test-1' type='result'><query xmlns='jabber:iq:version'/></iq>")

            val result: IqResult = waiting.await()
            assertEquals("iq-test-1", result.id)
            assertEquals(IqType.RESULT, result.type)
            assertTrue(result.xml.contains("jabber:iq:version"))
        }
    }

    @Test
    fun iqAwait_shouldTimeout() {
        runBlocking {
            val fake = FakeConnector(
                frames = listOf(
                    "<stream:stream xmlns:stream='http://etherx.jabber.org/streams'/>",
                    "<stream:features><mechanisms xmlns='urn:ietf:params:xml:ns:xmpp-sasl'><mechanism>PLAIN</mechanism></mechanisms></stream:features>",
                    "<success xmlns='urn:ietf:params:xml:ns:xmpp-sasl'/>",
                    "<stream:stream xmlns:stream='http://etherx.jabber.org/streams'/>",
                    "<stream:features><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/></stream:features>",
                    "__BIND_RESULT__",
                ),
            )
            val config = ConnectionConfig(
                jid = "alice@example.com".toBareJid(),
                host = "example.com",
                securityMode = SecurityMode.PLAIN,
                port = 5222,
                resource = "takina",
            )
            val connection = TakinaConnection(
                config = config,
                passwordProvider = { "password" },
                connectorFactory = { fake },
            )
            connection.connect()

            val query = IqStanza(
                id = "iq-timeout",
                from = "alice@example.com".toBareJid(),
                to = "example.com".toBareJid(),
                type = IqType.GET,
            )

            assertFailsWith<TakinaConnectionException> {
                connection.sendIqAndAwaitResult(query, timeoutMillis = 100)
            }
        }
    }
}

private class FakeConnector(
    frames: List<String>,
) : AbstractConnector() {
    private val queue = ArrayDeque(frames)
    val sent = mutableListOf<String>()
    var upgradedToTls: Boolean = false
        private set

    private var pumpOnFrame: ((String) -> Unit)? = null
    private var pumpOnError: ((Throwable) -> Unit)? = null

    override val isConnected: Boolean
        get() = true

    override fun connect(config: ConnectionConfig) = Unit

    override fun send(xml: String) {
        sent += xml
    }

    override fun readFrame(timeoutMillis: Int): String =
        when (val frame = queue.removeFirstOrNull()) {
            null -> throw TakinaConnectionException("fake connector ran out of scripted frames")

            "__BIND_RESULT__" -> {
                val bindRequest = sent.lastOrNull { it.contains("<iq") && it.contains("<bind") }
                    ?: throw TakinaConnectionException("bind response requested before bind iq was sent")
                val bindId = bindRequest.substringAfter("id='").substringBefore("'")
                "<iq id='$bindId' type='result'><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'><jid>alice@example.com/takina</jid></bind></iq>"
            }

            else -> frame
        }

    override fun upgradeToTls(config: ConnectionConfig) {
        upgradedToTls = true
    }

    override fun startFramePump(onFrame: (String) -> Unit, onError: (Throwable) -> Unit) {
        pumpOnFrame = onFrame
        pumpOnError = onError
    }

    override fun stopFramePump() {
        pumpOnFrame = null
        pumpOnError = null
    }

    override fun close() = Unit

    fun emitFromPump(frame: String) {
        pumpOnFrame?.invoke(frame) ?: error("pump is not started")
    }

    fun failPump(error: Throwable) {
        pumpOnError?.invoke(error) ?: error("pump is not started")
    }
}
