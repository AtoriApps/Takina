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
import org.atoriapps.takina.core.error.ErrorDomain
import org.atoriapps.takina.core.error.TakinaErrors
import org.atoriapps.takina.core.error.TakinaFailureException
import org.atoriapps.takina.core.utils.CodecUtils
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
    private var boundJid: String? = null

    private fun fail(
        domain: ErrorDomain,
        number: Int,
        message: String,
        retryable: Boolean,
        cause: Throwable? = null,
    ): Nothing {
        throw TakinaFailureException(
            TakinaErrors.of(
                domain = domain,
                number = number,
                message = message,
                retryable = retryable,
                cause = cause,
            )
        )
    }

    override suspend fun connect(
        password: String,
        onPhase: suspend (XmppConnectPhase) -> Unit,
        preBindNegotiation: suspend (featuresXml: String, transport: XmppPreBindTransport) -> XmppPreBindNegotiationDecision,
    ): XmppSession = withContext(Dispatchers.IO) {
        val existingBoundJid = boundJid

        if (socket?.isConnected == true && socket?.isClosed == false && !existingBoundJid.isNullOrBlank()) return@withContext XmppSession(existingBoundJid)

        closedByClient = false

        try {
            onPhase(XmppConnectPhase.TCP_CONNECTING)
            connectSocket()

            if (config.securityMode == SecurityMode.DIRECT_TLS) {
                onPhase(XmppConnectPhase.TLS_HANDSHAKING)
                upgradeToTls()
            }

            onPhase(XmppConnectPhase.STREAM_OPENING)
            var features = openStreamAndReadFeatures()

            if (config.securityMode == SecurityMode.START_TLS) {
                if (features.firstDescendant("starttls") == null) fail(ErrorDomain.TLS, 201, "Server does not advertise STARTTLS", retryable = false)

                onPhase(XmppConnectPhase.TLS_HANDSHAKING)
                requestStartTls()
                upgradeToTls()

                onPhase(XmppConnectPhase.STREAM_OPENING)
                features = openStreamAndReadFeatures()
            }

            onPhase(XmppConnectPhase.AUTHENTICATING)
            authenticate(features, password)

            onPhase(XmppConnectPhase.STREAM_OPENING)
            features = openStreamAndReadFeatures()

            onPhase(XmppConnectPhase.PRE_BIND_NEGOTIATING)
            val preBindTransport = object : XmppPreBindTransport {
                override suspend fun sendRawFrame(xml: String) = sendRaw(xml)

                override suspend fun readFrame(): String? = withContext(Dispatchers.IO) { reader?.nextFrame() }
            }
            when (val decision = preBindNegotiation(XmlWriter.render(features), preBindTransport)) {
                XmppPreBindNegotiationDecision.ProceedToBind -> Unit
                is XmppPreBindNegotiationDecision.ResumeSucceeded -> {
                    val session = XmppSession(boundJid = decision.boundJid)
                    boundJid = session.boundJid
                    startReadLoop()
                    return@withContext session
                }
            }

            if (features.firstDescendant("bind") == null) fail(ErrorDomain.BIND, 201, "Server does not advertise resource binding", retryable = false)

            onPhase(XmppConnectPhase.BINDING_RESOURCE)
            val session = XmppSession(boundJid = performResourceBind())
            boundJid = session.boundJid

            startReadLoop()
            session
        } catch (t: Throwable) {
            runCatching {
                closeInternal(
                    reason = "connect-failed:${t.message}",
                    notifyDisconnected = false,
                    authHardFailure = t.isAuthHardFailure(),
                )
            }
            if (t is CancellationException || t is TakinaFailureException) throw t
            fail(
                domain = ErrorDomain.TRANSPORT,
                number = 100,
                message = t.message ?: "Connection setup failed",
                retryable = true,
                cause = t,
            )
        }
    }

    override suspend fun sendRaw(xml: String) {
        val bytes = xml.toByteArray(Charsets.UTF_8)

        writeLock.withLock {
            val out = output ?: fail(
                domain = ErrorDomain.TRANSPORT,
                number = 111,
                message = "Transport not connected",
                retryable = true,
            )
            runCatching {
                withContext(Dispatchers.IO) {
                    out.write(bytes)
                    out.flush()
                }
            }.getOrElse { t ->
                if (t is CancellationException || t is TakinaFailureException) throw t
                fail(
                    domain = ErrorDomain.TRANSPORT,
                    number = 114,
                    message = t.message ?: "Failed to write outbound XML frame",
                    retryable = true,
                    cause = t,
                )
            }
        }
    }

    override suspend fun disconnect() {
        closedByClient = true
        runCatching { sendRaw("</stream:stream>") }
        closeInternal("client-disconnect")
    }

    private suspend fun connectSocket() = withContext(Dispatchers.IO) {
        runCatching {
            val base = Socket()
            // connectTimeoutMillis should only affect dial timeout, not steady-state read timeout.
            base.soTimeout = 0
            base.connect(InetSocketAddress(config.host, config.port), config.connectTimeoutMillis)
            socket = base
            input = base.getInputStream()
            output = base.getOutputStream()
            reader = JvmXmlFrameReader(input!!)
        }.getOrElse { t ->
            if (t is CancellationException || t is TakinaFailureException) throw t
            fail(
                domain = ErrorDomain.TRANSPORT,
                number = 120,
                message = t.message ?: "TCP connect failed",
                retryable = true,
                cause = t,
            )
        }
    }

    private suspend fun openStreamAndReadFeatures(): XmlElement {
        sendRaw(streamOpen())
        val openFrame = readRequiredFrame("stream open")
        val openTag = XmlParser.parseStartTagOrNull(openFrame)
        if (openTag?.localName != "stream") {
            fail(ErrorDomain.STREAM, 201, "Expected stream:stream, got: $openFrame", retryable = true)
        }

        val featuresFrame = readRequiredFrame("stream features")
        val features = XmlParser.parseElementOrNull(featuresFrame)
        if (features?.localName != "features") {
            fail(ErrorDomain.STREAM, 202, "Expected stream:features, got: $featuresFrame", retryable = true)
        }
        return features
    }

    private suspend fun requestStartTls() {
        sendRaw(XmlWriter.render(xml("starttls") {
            attr("xmlns", "urn:ietf:params:xml:ns:xmpp-tls")
            selfClosing()
        }))
        val proceedFrame = readRequiredFrame("starttls proceed")
        val proceed = XmlParser.parseElementOrNull(proceedFrame)
        if (proceed?.localName != "proceed") {
            fail(ErrorDomain.TLS, 202, "STARTTLS failed: $proceedFrame", retryable = true)
        }
    }

    private suspend fun authenticate(features: XmlElement, password: String) {
        val serverMechanisms = features.descendants("mechanism").map { it.textContent().trim().uppercase() }.toSet()
        if (serverMechanisms.isEmpty()) {
            fail(ErrorDomain.AUTH, 201, "Server does not advertise SASL mechanisms", retryable = false)
        }

        val preferred = config.saslMechanisms.map { it.uppercase() }
        val common = preferred.filter { it in serverMechanisms }
        if (common.isEmpty()) {
            fail(ErrorDomain.AUTH, 202, "No common SASL mechanism. client=$preferred server=$serverMechanisms", retryable = false)
        }
        val selected = common.firstOrNull { mechanism -> isLocallySupportedSaslMechanism(mechanism, password) }
            ?: fail(
                ErrorDomain.AUTH,
                203,
                "No locally supported SASL mechanism. common=$common (JVM provider may miss SCRAM/DIGEST; try enabling PLAIN or adding provider)",
                retryable = false,
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
        val first = Scram.buildClientFirst(config.owner.local)
        val firstPayload = Scram.encodeUtf8Base64(first.fullMessage)
        sendRaw(XmlWriter.render(xml("auth") {
            attr("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl")
            attr("mechanism", mechanism.mechanismName)
            text(firstPayload)
        }))

        val challengeFrame = readRequiredFrame("scram challenge")
        val challengeNode = XmlParser.parseElementOrNull(challengeFrame)
            ?: fail(ErrorDomain.AUTH, 204, "Unexpected non-xml SCRAM challenge: $challengeFrame", retryable = true)
        if (challengeNode.localName == "failure") {
            fail(ErrorDomain.AUTH, 205, "SASL auth failed via ${mechanism.mechanismName}: $challengeFrame", retryable = false)
        }
        if (challengeNode.localName != "challenge") {
            fail(ErrorDomain.AUTH, 206, "Unexpected SCRAM frame via ${mechanism.mechanismName}: $challengeFrame", retryable = true)
        }
        val serverFirstB64 = challengeNode.textContent().trim()
        val serverFirst = Scram.decodeUtf8Base64(serverFirstB64)

        val final = Scram.buildClientFinal(
            mechanism = mechanism,
            password = password,
            clientFirstBare = first.messageBare,
            serverFirstMessage = serverFirst,
        )
        val finalPayload = Scram.encodeUtf8Base64(final.fullMessage)
        sendRaw(XmlWriter.render(xml("response") {
            attr("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl")
            text(finalPayload)
        }))

        val successFrame = readRequiredFrame("scram success")
        val successNode = XmlParser.parseElementOrNull(successFrame)
            ?: fail(ErrorDomain.AUTH, 207, "Unexpected non-xml SCRAM success frame: $successFrame", retryable = true)
        when (successNode.localName) {
            "success" -> {
                val successPayload = successNode.textContent().trim()
                if (successPayload.isNotEmpty()) {
                    val decoded = Scram.decodeUtf8Base64(successPayload)
                    val verifier = Scram.extractServerVerifier(decoded)

                    if (verifier != null && !Scram.constantTimeEqualsUtf8(verifier, final.expectedServerSignatureBase64))
                        fail(ErrorDomain.AUTH, 208, "SCRAM server signature verification failed", retryable = false)
                }
            }

            "failure" -> fail(ErrorDomain.AUTH, 209, "SASL auth failed via ${mechanism.mechanismName}: $successFrame", retryable = false)

            else -> fail(ErrorDomain.AUTH, 210, "Unexpected SCRAM final frame via ${mechanism.mechanismName}: $successFrame", retryable = true)
        }
    }

    private suspend fun authenticatePlain(password: String) {
        val payload = "\u0000${config.owner.local}\u0000$password"
        val encoded = CodecUtils.base64Encode(payload.toByteArray(Charsets.UTF_8))
        sendRaw(XmlWriter.render(xml("auth") {
            attr("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl")
            attr("mechanism", "PLAIN")
            text(encoded)
        }))
        val responseFrame = readRequiredFrame("sasl result")
        val response = XmlParser.parseElementOrNull(responseFrame)
        when (response?.localName) {
            "success" -> Unit
            "failure" -> fail(ErrorDomain.AUTH, 211, "SASL auth failed: $responseFrame", retryable = false)
            else -> fail(ErrorDomain.AUTH, 212, "Unexpected SASL response: $responseFrame", retryable = true)
        }
    }

    private suspend fun authenticateViaSaslClient(mechanism: String, password: String) {
        val client = createSaslClient(mechanism, password)
            ?: fail(ErrorDomain.AUTH, 213, "SASL client not available for mechanism: $mechanism", retryable = false)

        val initial = if (client.hasInitialResponse()) client.evaluateChallenge(ByteArray(0)) else null
        if (initial == null) sendRaw(XmlWriter.render(xml("auth") {
            attr("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl")
            attr("mechanism", mechanism)
            selfClosing()
        })) else sendRaw(XmlWriter.render(xml("auth") {
            attr("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl")
            attr("mechanism", mechanism)
            text(CodecUtils.base64Encode(initial))
        }))

        while (true) {
            val frame = readRequiredFrame("sasl challenge or success")
            val node = XmlParser.parseElementOrNull(frame) ?: fail(ErrorDomain.AUTH, 214, "Unexpected non-xml SASL frame via $mechanism: $frame", retryable = true)

            when (node.localName) {
                "challenge" -> {
                    val challengeRaw = node.textContent()
                    val challenge = if (challengeRaw.isBlank()) ByteArray(0) else CodecUtils.base64Decode(challengeRaw.trim())
                    val response = client.evaluateChallenge(challenge)
                    if (response.isEmpty()) sendRaw(XmlWriter.render(xml("response") {
                        attr("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl")
                        selfClosing()
                    })) else sendRaw(XmlWriter.render(xml("response") {
                        attr("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl")
                        text(CodecUtils.base64Encode(response))
                    }))
                }

                "success" -> {
                    val finalDataRaw = node.textContent()
                    if (finalDataRaw.isNotBlank() && !client.isComplete) {
                        val finalData = CodecUtils.base64Decode(finalDataRaw.trim())
                        client.evaluateChallenge(finalData)
                    }
                    return
                }

                "failure" -> fail(ErrorDomain.AUTH, 215, "SASL auth failed via $mechanism: $frame", retryable = false)

                else -> fail(ErrorDomain.AUTH, 216, "Unexpected SASL frame via $mechanism: $frame", retryable = true)
            }
        }
    }

    private suspend fun performResourceBind(): String {
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
        if (result?.localName != "iq" || result.attribute("type") != "result" || result.attribute("id") != id) fail(ErrorDomain.BIND, 202, "Bind failed: $resultFrame", retryable = true)

        val jid = result.firstDescendant("jid")?.textContent()?.trim()
        if (jid.isNullOrBlank()) fail(ErrorDomain.BIND, 203, "Bind result missing jid: $resultFrame", retryable = true)
        return jid
    }

    private suspend fun readRequiredFrame(label: String): String = withContext(Dispatchers.IO) {
        val value = reader?.nextFrame() ?: fail(
            domain = ErrorDomain.TRANSPORT,
            number = 112,
            message = "EOF while waiting for $label",
            retryable = true,
        )
        value
    }

    private suspend fun upgradeToTls() = withContext(Dispatchers.IO) {
        val current = socket ?: fail(
            domain = ErrorDomain.TRANSPORT,
            number = 113,
            message = "socket is null",
            retryable = false,
        )
        runCatching {
            val sslContext = if (config.trustAllCertificates) insecureSslContext() else SSLContext.getDefault()
            val sslSocket = sslContext.socketFactory.createSocket(current, config.host, config.port, true) as SSLSocket
            sslSocket.useClientMode = true
            sslSocket.startHandshake()

            socket = sslSocket
            input = sslSocket.inputStream
            output = sslSocket.outputStream
            reader = JvmXmlFrameReader(input!!)
        }.getOrElse { t ->
            if (t is CancellationException || t is TakinaFailureException) throw t
            fail(
                domain = ErrorDomain.TLS,
                number = 203,
                message = t.message ?: "TLS upgrade failed",
                retryable = true,
                cause = t,
            )
        }
    }

    private fun startReadLoop() {
        if (readLoopStarted) return
        readLoopStarted = true

        scope.launch {
            var lastFrame = ""
            try {
                while (isActive) {
                    val frame = withContext(Dispatchers.IO) { reader?.nextFrame() } ?: break
                    lastFrame = frame
                    val streamError = parseXmppStreamError(frame)
                    if (streamError != null) {
                        closeInternal(
                            reason = "stream-error:${streamError.condition.wireName}",
                            authHardFailure = streamError.condition.authHardFailure,
                        )
                        return@launch
                    }
                    callbacks.onFrame(frame)
                }
                closeInternal(if (closedByClient) "client-disconnect" else "eof")
            } catch (_: CancellationException) {
                closeInternal("cancelled")
            } catch (t: TakinaFailureException) {
                closeInternal(
                    reason = "read-failure:${t.error.code}",
                    authHardFailure = t.isAuthHardFailure(),
                )
            } catch (t: Throwable) {
                callbacks.onFrameParseFailed(lastFrame, t.message ?: "Unknown parser error")
                closeInternal("read-error:${t.message}")
            }
        }
    }

    private suspend fun closeInternal(
        reason: String?,
        notifyDisconnected: Boolean = true,
        authHardFailure: Boolean = false,
    ) {
        runCatching { socket?.close() }
        socket = null
        input = null
        output = null
        reader = null
        boundJid = null
        readLoopStarted = false
        if (!closedByClient && notifyDisconnected) runCatching { callbacks.onClosed(reason, authHardFailure) }
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

    private fun Throwable.isAuthHardFailure(): Boolean = this is TakinaFailureException && error.domain == ErrorDomain.AUTH && !error.retryable
}

internal actual fun createPlatformXmppTransport(
    config: ConnectionConfig,
    callbacks: XmppTransportCallbacks,
): XmppTransport = JvmXmppTransport(config, callbacks)
