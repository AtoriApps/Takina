package org.atoriapps.takina.core

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.atoriapps.takina.core.connections.ConnectionConfig
import org.atoriapps.takina.core.connections.XmppConnectPhase
import org.atoriapps.takina.core.connections.XmppPreBindNegotiationDecision
import org.atoriapps.takina.core.connections.XmppPreBindTransport
import org.atoriapps.takina.core.connections.XmppSession
import org.atoriapps.takina.core.connections.XmppTransport
import org.atoriapps.takina.core.connections.XmppTransportCallbacks
import org.atoriapps.takina.core.connections.XmppTransportFactoryRegistry
import org.atoriapps.takina.core.models.TakinaResult
import org.atoriapps.takina.core.models.toBareJid
import org.atoriapps.takina.features.streammanagement.StreamManagementFeature
import org.atoriapps.takina.features.streammanagement.StreamManagementFeatureProvider
import org.atoriapps.takina.features.streammanagement.StreamManagementResumedEvent
import kotlin.time.Clock

class StreamManagementFeatureTest {
    private val alice = "alice@example.com".toBareJid()

    @Test
    fun `stream management prebind resume can skip bind`() = runTest {
        val persisted = StreamManagementFeature.PersistedSessionState(
            sessionId = "sm-sid-1",
            allowResume = true,
            maxResumeSeconds = 60,
            outboundSentCount = 0,
            lastServerAckCount = 0,
            pendingOutbound = emptyList(),
            boundJid = "${alice}/takina",
            persistedAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
        )
        val store = object : StreamManagementFeature.StreamManagementStateStore {
            override fun load(owner: org.atoriapps.takina.core.models.BareJid): StreamManagementFeature.PersistedSessionState? = persisted
            override fun save(owner: org.atoriapps.takina.core.models.BareJid, state: StreamManagementFeature.PersistedSessionState) = Unit
            override fun clear(owner: org.atoriapps.takina.core.models.BareJid) = Unit
        }

        var bindPhaseCount = 0
        val sentFrames = mutableListOf<String>()
        val oldFactory = XmppTransportFactoryRegistry.factory
        XmppTransportFactoryRegistry.factory = { config, _ ->
            object : XmppTransport {
                override suspend fun connect(
                    password: String,
                    onPhase: suspend (XmppConnectPhase) -> Unit,
                    preBindNegotiation: suspend (String, XmppPreBindTransport) -> XmppPreBindNegotiationDecision,
                ): XmppSession {
                    onPhase(XmppConnectPhase.TCP_CONNECTING)
                    onPhase(XmppConnectPhase.STREAM_OPENING)
                    onPhase(XmppConnectPhase.AUTHENTICATING)
                    onPhase(XmppConnectPhase.STREAM_OPENING)
                    onPhase(XmppConnectPhase.PRE_BIND_NEGOTIATING)

                    var resumeRequested = false
                    val decision = preBindNegotiation(
                        "<features><sm xmlns='urn:xmpp:sm:3'/></features>",
                        object : XmppPreBindTransport {
                            override suspend fun sendRawFrame(xml: String) {
                                sentFrames += xml
                                if (xml.contains("<resume")) resumeRequested = true
                            }

                            override suspend fun readFrame(): String? = if (resumeRequested) {
                                "<resumed xmlns='urn:xmpp:sm:3' previd='sm-sid-1' h='0'/>"
                            } else {
                                null
                            }
                        },
                    )
                    return when (decision) {
                        XmppPreBindNegotiationDecision.ProceedToBind -> {
                            bindPhaseCount += 1
                            onPhase(XmppConnectPhase.BINDING_RESOURCE)
                            XmppSession("${config.owner}/${config.resource}")
                        }

                        is XmppPreBindNegotiationDecision.ResumeSucceeded -> XmppSession(decision.boundJid)
                    }
                }

                override suspend fun sendRaw(xml: String) {
                    sentFrames += xml
                }

                override suspend fun disconnect() = Unit
            }
        }
        try {
            var resumedEventCount = 0
            val takina = createTakina {
                features {
                    installAndConfigure(StreamManagementFeatureProvider) {
                        restorePersistedStateOnStartup = true
                        stateStore = store
                    }
                }
                addAccount {
                    jid = alice
                    password = "secret"
                }
            }
            takina.events.on(StreamManagementResumedEvent) { resumedEventCount += 1 }

            val connect = takina.connect(alice)
            assertTrue(connect is TakinaResult.Ok)
            assertEquals(0, bindPhaseCount)
            assertEquals(1, resumedEventCount)
            assertTrue(sentFrames.any { it.contains("<resume") })
            takina.shutdown()
        } finally {
            XmppTransportFactoryRegistry.factory = oldFactory
        }
    }

    @Test
    fun `stream management ack request frame is claimed and replied`() = runTest {
        class ManualTransport(
            private val config: ConnectionConfig,
            private val callbacks: XmppTransportCallbacks,
            private val sent: MutableList<String>,
        ) : XmppTransport {
            override suspend fun connect(
                password: String,
                onPhase: suspend (XmppConnectPhase) -> Unit,
                preBindNegotiation: suspend (String, XmppPreBindTransport) -> XmppPreBindNegotiationDecision,
            ): XmppSession {
                onPhase(XmppConnectPhase.TCP_CONNECTING)
                onPhase(XmppConnectPhase.STREAM_OPENING)
                onPhase(XmppConnectPhase.AUTHENTICATING)
                onPhase(XmppConnectPhase.STREAM_OPENING)
                onPhase(XmppConnectPhase.PRE_BIND_NEGOTIATING)
                preBindNegotiation(
                    "<features><sm xmlns='urn:xmpp:sm:3'/></features>",
                    object : XmppPreBindTransport {
                        override suspend fun sendRawFrame(xml: String) {
                            sent += xml
                        }

                        override suspend fun readFrame(): String? = null
                    },
                )
                onPhase(XmppConnectPhase.BINDING_RESOURCE)
                return XmppSession("${config.owner}/${config.resource}")
            }

            override suspend fun sendRaw(xml: String) {
                sent += xml
            }

            override suspend fun disconnect() = Unit

            suspend fun pushInbound(xml: String) {
                callbacks.onFrame(xml)
            }
        }

        val sent = mutableListOf<String>()
        lateinit var transport: ManualTransport
        val oldFactory = XmppTransportFactoryRegistry.factory
        XmppTransportFactoryRegistry.factory = { config, callbacks ->
            ManualTransport(config, callbacks, sent).also { transport = it }
        }
        try {
            val takina = createTakina {
                features {
                    install(StreamManagementFeatureProvider)
                }
                addAccount {
                    jid = alice
                    password = "secret"
                }
            }

            assertTrue(takina.connect(alice) is TakinaResult.Ok)
            transport.pushInbound("<enabled xmlns='urn:xmpp:sm:3' id='sid' resume='true' max='60'/>")
            transport.pushInbound("<r xmlns='urn:xmpp:sm:3'/>")

            assertTrue(sent.any { it.contains("<a xmlns='urn:xmpp:sm:3' h='0'/>") })
            takina.shutdown()
        } finally {
            XmppTransportFactoryRegistry.factory = oldFactory
        }
    }
}
