package org.atoriapps.takina.core.connections

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import javax.security.auth.callback.CallbackHandler
import javax.security.auth.callback.NameCallback
import javax.security.auth.callback.PasswordCallback
import javax.security.sasl.AuthorizeCallback
import javax.security.sasl.RealmCallback
import javax.security.sasl.RealmChoiceCallback
import javax.security.sasl.Sasl
import javax.security.sasl.SaslClient
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.cancellation.CancellationException
import org.atoriapps.takina.core.xml.XmlElement
import org.atoriapps.takina.core.xml.JvmXmlFrameReader
import org.atoriapps.takina.core.xml.XmlParser
import org.atoriapps.takina.core.xml.XmlWriter
import org.atoriapps.takina.core.xml.xml

internal class JvmXmppTransport(
    private val config: ConnectionConfig,
    private val callbacks: XmppTransportCallbacks,
) : XmppTransport {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()

    private var socket: Socket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private var reader: JvmXmlFrameReader? = null
    private var readLoopStarted = false
    private var closedByClient = false

    override var boundJid: String? = null
        private set

    override val isConnected: Boolean
        get() = socket?.isConnected == true && socket?.isClosed == false && boundJid != null

    override suspend fun connect(password: String) = withContext(Dispatchers.IO) {
        if (isConnected) return@withContext
        closedByClient = false

        callbacks.onStateChanged(ConnectionState.TCP_CONNECTING)
        connectSocket()

        if (config.securityMode == SecurityMode.DIRECT_TLS) {
            callbacks.onStateChanged(ConnectionState.TLS_HANDSHAKING)
            upgradeToTls()
        }

        callbacks.onStateChanged(ConnectionState.STREAM_OPENING)
        var features = openStreamAndReadFeatures()

        if (config.securityMode == SecurityMode.START_TLS) {
            if (features.firstDescendant("starttls") == null) throw IllegalStateException("Server does not advertise STARTTLS")

            callbacks.onStateChanged(ConnectionState.TLS_HANDSHAKING)
            requestStartTls()
            upgradeToTls()

            callbacks.onStateChanged(ConnectionState.STREAM_OPENING)
            features = openStreamAndReadFeatures()
        }

        callbacks.onStateChanged(ConnectionState.AUTHENTICATING)
        authenticate(features, password)

        callbacks.onStateChanged(ConnectionState.STREAM_OPENING)
        features = openStreamAndReadFeatures()
        if (features.firstDescendant("bind") == null) throw IllegalStateException("Server does not advertise resource binding")

        callbacks.onStateChanged(ConnectionState.BINDING_RESOURCE)
        performResourceBind()
        callbacks.onStateChanged(ConnectionState.ESTABLISHED)

        startReadLoop()
    }

    override suspend fun sendRaw(xml: String) {
        val bytes = xml.toByteArray(Charsets.UTF_8)

        writeLock.withLock {
            val out = output ?: error("Transport not connected")
            withContext(Dispatchers.IO) {
                out.write(bytes)
                out.flush()
            }
        }
    }

    override suspend fun disconnect() {
        closedByClient = true
        runCatching { sendRaw("</stream:stream>") }
        closeInternal("client-disconnect")
    }

    private suspend fun connectSocket() = withContext(Dispatchers.IO) {
        val base = Socket()
        base.soTimeout = config.connectTimeoutMillis
        base.connect(InetSocketAddress(config.host, config.port), config.connectTimeoutMillis)
        socket = base
        input = base.getInputStream()
        output = base.getOutputStream()
        reader = JvmXmlFrameReader(input!!)
    }

    private suspend fun openStreamAndReadFeatures(): XmlElement {
        sendRaw(streamOpen())
        val openFrame = readRequiredFrame("stream open")
        val openTag = XmlParser.parseStartTagOrNull(openFrame)
        if (openTag?.localName != "stream") throw IllegalStateException("Expected stream:stream, got: $openFrame")

        val featuresFrame = readRequiredFrame("stream features")
        val features = XmlParser.parseElementOrNull(featuresFrame)
        if (features?.localName != "features") throw IllegalStateException("Expected stream:features, got: $featuresFrame")
        return features
    }

    private suspend fun requestStartTls() {
        sendRaw(XmlWriter.render(xml("starttls") {
            attr("xmlns", "urn:ietf:params:xml:ns:xmpp-tls")
            selfClosing()
        }))
        val proceedFrame = readRequiredFrame("starttls proceed")
        val proceed = XmlParser.parseElementOrNull(proceedFrame)
        if (proceed?.localName != "proceed") throw IllegalStateException("STARTTLS failed: $proceedFrame")
    }

    private suspend fun authenticate(features: XmlElement, password: String) {
        val serverMechanisms = features.descendants("mechanism").map { it.textContent().trim().uppercase() }.toSet()
        if (serverMechanisms.isEmpty()) throw IllegalStateException("Server does not advertise SASL mechanisms")

        val preferred = config.saslMechanisms.map { it.uppercase() }
        val common = preferred.filter { it in serverMechanisms }
        if (common.isEmpty()) {
            throw IllegalStateException("No common SASL mechanism. client=$preferred server=$serverMechanisms")
        }
        val selected = common.firstOrNull { mechanism -> isLocallySupportedSaslMechanism(mechanism, password) }
            ?: throw IllegalStateException(
                "No locally supported SASL mechanism. common=$common (JVM provider may miss SCRAM/DIGEST; try enabling PLAIN or adding provider)",
            )

        if (selected == "PLAIN") {
            authenticatePlain(password)
            return
        }
        val scram = ScramMechanism.fromNameOrNull(selected)
        if (scram != null) {
            authenticateViaBuiltinScram(scram, password)
            return
        }
        authenticateViaSaslClient(selected, password)
    }

    private fun isLocallySupportedSaslMechanism(mechanism: String, password: String): Boolean {
        if (mechanism == "PLAIN") return true
        ScramMechanism.fromNameOrNull(mechanism)?.let { return it.isRuntimeSupported() }
        return runCatching {
            val client = createSaslClient(mechanism, password)
            val supported = client != null
            runCatching { client?.dispose() }
            supported
        }.getOrDefault(false)
    }

    private suspend fun authenticateViaBuiltinScram(mechanism: ScramMechanism, password: String) {
        val first = JvmScram.buildClientFirst(config.owner.local)
        val firstPayload = Base64.getEncoder().encodeToString(first.fullMessage.toByteArray(Charsets.UTF_8))
        sendRaw(XmlWriter.render(xml("auth") {
            attr("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl")
            attr("mechanism", mechanism.mechanismName)
            text(firstPayload)
        }))

        val challengeFrame = readRequiredFrame("scram challenge")
        val challengeNode = XmlParser.parseElementOrNull(challengeFrame) ?: throw IllegalStateException("Unexpected non-xml SCRAM challenge: $challengeFrame")
        if (challengeNode.localName == "failure") throw IllegalStateException("SASL auth failed via ${mechanism.mechanismName}: $challengeFrame")
        if (challengeNode.localName != "challenge") throw IllegalStateException("Unexpected SCRAM frame via ${mechanism.mechanismName}: $challengeFrame")
        val serverFirstB64 = challengeNode.textContent().trim()
        val serverFirst = String(Base64.getDecoder().decode(serverFirstB64), Charsets.UTF_8)

        val final = JvmScram.buildClientFinal(
            mechanism = mechanism,
            password = password,
            clientFirstBare = first.messageBare,
            serverFirstMessage = serverFirst,
        )
        val finalPayload = Base64.getEncoder().encodeToString(final.fullMessage.toByteArray(Charsets.UTF_8))
        sendRaw(XmlWriter.render(xml("response") {
            attr("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl")
            text(finalPayload)
        }))

        val successFrame = readRequiredFrame("scram success")
        val successNode = XmlParser.parseElementOrNull(successFrame) ?: throw IllegalStateException("Unexpected non-xml SCRAM success frame: $successFrame")
        when (successNode.localName) {
            "success" -> {
                val successPayload = successNode.textContent().trim()
                if (successPayload.isNotEmpty()) {
                    val decoded = String(Base64.getDecoder().decode(successPayload), Charsets.UTF_8)
                    val verifier = JvmScram.extractServerVerifier(decoded)
                    if (verifier != null && !MessageDigest.isEqual(verifier.toByteArray(), final.expectedServerSignatureBase64.toByteArray()))
                        throw IllegalStateException("SCRAM server signature verification failed")
                }
            }

            "failure" -> throw IllegalStateException("SASL auth failed via ${mechanism.mechanismName}: $successFrame")

            else -> throw IllegalStateException("Unexpected SCRAM final frame via ${mechanism.mechanismName}: $successFrame")
        }
    }

    private suspend fun authenticatePlain(password: String) {
        val payload = "\u0000${config.owner.local}\u0000$password"
        val encoded = Base64.getEncoder().encodeToString(payload.toByteArray(Charsets.UTF_8))
        sendRaw(XmlWriter.render(xml("auth") {
            attr("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl")
            attr("mechanism", "PLAIN")
            text(encoded)
        }))
        val responseFrame = readRequiredFrame("sasl result")
        val response = XmlParser.parseElementOrNull(responseFrame)
        when (response?.localName) {
            "success" -> Unit
            "failure" -> throw IllegalStateException("SASL auth failed: $responseFrame")
            else -> throw IllegalStateException("Unexpected SASL response: $responseFrame")
        }
    }

    private suspend fun authenticateViaSaslClient(mechanism: String, password: String) {
        val client = createSaslClient(mechanism, password) ?: throw IllegalStateException("SASL client not available for mechanism: $mechanism")

        val initial = if (client.hasInitialResponse()) client.evaluateChallenge(ByteArray(0)) else null
        if (initial == null) sendRaw(XmlWriter.render(xml("auth") {
            attr("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl")
            attr("mechanism", mechanism)
            selfClosing()
        })) else sendRaw(XmlWriter.render(xml("auth") {
            attr("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl")
            attr("mechanism", mechanism)
            text(Base64.getEncoder().encodeToString(initial))
        }))

        while (true) {
            val frame = readRequiredFrame("sasl challenge or success")
            val node = XmlParser.parseElementOrNull(frame) ?: throw IllegalStateException("Unexpected non-xml SASL frame via $mechanism: $frame")
            when (node.localName) {
                "challenge" -> {
                    val challengeRaw = node.textContent()
                    val challenge = if (challengeRaw.isBlank()) ByteArray(0) else Base64.getDecoder().decode(challengeRaw.trim())
                    val response = client.evaluateChallenge(challenge)
                    if (response.isEmpty()) sendRaw(XmlWriter.render(xml("response") {
                        attr("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl")
                        selfClosing()
                    })) else sendRaw(XmlWriter.render(xml("response") {
                        attr("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl")
                        text(Base64.getEncoder().encodeToString(response))
                    }))
                }

                "success" -> {
                    val finalDataRaw = node.textContent()
                    if (finalDataRaw.isNotBlank() && !client.isComplete) {
                        val finalData = Base64.getDecoder().decode(finalDataRaw.trim())
                        client.evaluateChallenge(finalData)
                    }
                    return
                }

                "failure" -> throw IllegalStateException("SASL auth failed via $mechanism: $frame")

                else -> throw IllegalStateException("Unexpected SASL frame via $mechanism: $frame")
            }
        }
    }

    private suspend fun performResourceBind() {
        val id = "bind-1"
        val bind = XmlWriter.render(xml("iq") {
            attr("id", id)
            attr("type", "set")
            element("bind") {
                attr("xmlns", "urn:ietf:params:xml:ns:xmpp-bind")
                element("resource") { text(config.resource) }
            }
        })
        sendRaw(bind)

        val resultFrame = readRequiredFrame("bind result")
        val result = XmlParser.parseElementOrNull(resultFrame)
        if (result?.localName != "iq" || result.attribute("type") != "result") throw IllegalStateException("Bind failed: $resultFrame")
        val jid = result.firstDescendant("jid")?.textContent()
        boundJid = jid ?: "${config.owner}/${config.resource}"
    }

    private suspend fun readRequiredFrame(label: String): String = withContext(Dispatchers.IO) {
        val value = reader?.nextFrame() ?: throw IllegalStateException("EOF while waiting for $label")
        value
    }

    private suspend fun upgradeToTls() = withContext(Dispatchers.IO) {
        val current = socket ?: error("socket is null")
        val sslContext = if (config.trustAllCertificates) insecureSslContext() else SSLContext.getDefault()
        val sslSocket = sslContext.socketFactory.createSocket(current, config.host, config.port, true) as SSLSocket
        sslSocket.useClientMode = true
        sslSocket.startHandshake()

        socket = sslSocket
        input = sslSocket.inputStream
        output = sslSocket.outputStream
        reader = JvmXmlFrameReader(input!!)
    }

    private fun startReadLoop() {
        if (readLoopStarted) return
        readLoopStarted = true
        scope.launch {
            try {
                while (isActive) {
                    val frame = withContext(Dispatchers.IO) { reader?.nextFrame() } ?: break
                    callbacks.onFrame(frame)
                }
                closeInternal(if (closedByClient) "client-disconnect" else "eof")
            } catch (_: CancellationException) {
                closeInternal("cancelled")
            } catch (t: Throwable) {
                callbacks.onFrameParseFailed("", t.message ?: "Unknown parser error")
                closeInternal("read-error:${t.message}")
            }
        }
    }

    private suspend fun closeInternal(reason: String?) {
        runCatching { socket?.close() }
        socket = null
        input = null
        output = null
        reader = null
        boundJid = null
        readLoopStarted = false
        callbacks.onStateChanged(ConnectionState.CLOSED)
        if (!closedByClient) callbacks.onClosed(reason)
    }

    private fun streamOpen(): String = XmlWriter.startTag(
        name = "stream:stream",
        attributes = mapOf(
            "to" to config.owner.domain,
            "xmlns" to "jabber:client",
            "xmlns:stream" to "http://etherx.jabber.org/streams",
            "version" to "1.0",
        ),
    )

    private fun createSaslClient(mechanism: String, password: String): SaslClient? {
        val callbackHandler = CallbackHandler { callbacks ->
            for (callback in callbacks) {
                when (callback) {
                    is NameCallback -> callback.name = config.owner.local
                    is PasswordCallback -> callback.password = password.toCharArray()
                    is RealmCallback -> callback.text = config.owner.domain
                    is RealmChoiceCallback -> if (callback.choices.isNotEmpty()) callback.setSelectedIndex(0)
                    is AuthorizeCallback -> callback.isAuthorized = true
                    else -> Unit
                }
            }
        }
        val props = linkedMapOf<String, String>().apply {
            put(Sasl.QOP, "auth")
            put("com.sun.security.sasl.digest.realm", config.owner.domain)
        }
        return Sasl.createSaslClient(
            arrayOf(mechanism),
            null,
            "xmpp",
            config.owner.domain,
            props,
            callbackHandler,
        )
    }

    private fun insecureSslContext(): SSLContext {
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) = Unit
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
        }
        return SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustAll), java.security.SecureRandom()) }
    }
}

internal actual fun createPlatformXmppTransport(
    config: ConnectionConfig,
    callbacks: XmppTransportCallbacks,
): XmppTransport = JvmXmppTransport(config, callbacks)
