package org.atoriapps.takina.core

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.atoriapps.takina.core.connections.AccountState
import org.atoriapps.takina.core.connections.ConnectionConfig
import org.atoriapps.takina.core.connections.ConnectionState
import org.atoriapps.takina.core.connections.XmppConnectPhase
import org.atoriapps.takina.core.connections.XmppSession
import org.atoriapps.takina.core.connections.XmppTransport
import org.atoriapps.takina.core.connections.XmppTransportCallbacks
import org.atoriapps.takina.core.connections.XmppTransportFactoryRegistry
import org.atoriapps.takina.core.controlling.ApplyMode
import org.atoriapps.takina.core.controlling.ConfigRejectCode
import org.atoriapps.takina.core.controlling.ConnectionConfigPaths
import org.atoriapps.takina.core.controlling.ReconnectConfigPaths
import org.atoriapps.takina.core.events.AllConnectEvent
import org.atoriapps.takina.core.events.ConfigRejectedEvent
import org.atoriapps.takina.core.events.FinalFrameOutboundEvent
import org.atoriapps.takina.core.events.PipelineNodeFailedEvent
import org.atoriapps.takina.core.events.ReconnectScheduledEvent
import org.atoriapps.takina.core.events.RawFrameInboundEvent
import org.atoriapps.takina.core.error.ErrorDomain
import org.atoriapps.takina.core.error.TakinaErrors
import org.atoriapps.takina.core.error.TakinaFailureException
import org.atoriapps.takina.core.features.TakinaFeature
import org.atoriapps.takina.core.features.TakinaFeatureProvider
import org.atoriapps.takina.core.models.BareJid
import org.atoriapps.takina.core.models.Scope
import org.atoriapps.takina.core.models.ScopeKind
import org.atoriapps.takina.core.models.TakinaResult
import org.atoriapps.takina.core.models.toBareJid
import org.atoriapps.takina.core.pipeline.NodeResult
import org.atoriapps.takina.core.pipeline.OutboundFrame
import org.atoriapps.takina.core.pipeline.OutboundNode
import org.atoriapps.takina.core.request.PresenceShow
import org.atoriapps.takina.core.xml.xml

class CoreSemanticsTest {
    private val alice = "alice@example.com".toBareJid()
    private val bob = "bob@example.com".toBareJid()
    private val carol = "carol@example.com".toBareJid()

    private class DummyFeature : TakinaFeature {
        override val supportedScopes: Set<ScopeKind> = setOf(ScopeKind.GLOBAL, ScopeKind.MESSAGE)
        override val applyMode: ApplyMode = ApplyMode.NEXT_CONNECTION
    }

    private object DummyFeatureProvider : TakinaFeatureProvider<DummyFeature> {
        override val id: String = "dummy"
        override val featureType = DummyFeature::class
        override fun create(): DummyFeature = DummyFeature()
    }

    private class RecordingTransport(
        private val config: ConnectionConfig,
        private val sentSink: MutableList<String>,
    ) : XmppTransport {
        override suspend fun connect(password: String, onPhase: suspend (XmppConnectPhase) -> Unit): XmppSession {
            onPhase(XmppConnectPhase.TCP_CONNECTING)
            onPhase(XmppConnectPhase.STREAM_OPENING)
            onPhase(XmppConnectPhase.AUTHENTICATING)
            onPhase(XmppConnectPhase.BINDING_RESOURCE)
            return XmppSession("${config.owner}/${config.resource}")
        }

        override suspend fun sendRaw(xml: String) {
            sentSink += xml
        }

        override suspend fun disconnect() = Unit
    }

    private class MarkerOutboundFeature : TakinaFeature {
        override val supportedScopes: Set<ScopeKind> = setOf(ScopeKind.GLOBAL, ScopeKind.ACCOUNT, ScopeKind.CONVERSATION, ScopeKind.MESSAGE)
        override val applyMode: ApplyMode = ApplyMode.NEXT_ITEM
        override fun outboundNodes(): List<OutboundNode> = listOf(object : OutboundNode {
            override val key: String = "marker-outbound"
            override suspend fun execute(frame: OutboundFrame): NodeResult = NodeResult.Continue("<!--marker-->${frame.raw}")
        })
    }

    private object MarkerOutboundFeatureProvider : TakinaFeatureProvider<MarkerOutboundFeature> {
        override val id: String = "marker-outbound"
        override val featureType = MarkerOutboundFeature::class
        override fun create(): MarkerOutboundFeature = MarkerOutboundFeature()
    }

    private class ThrowingOutboundFeature : TakinaFeature {
        override val supportedScopes: Set<ScopeKind> = setOf(ScopeKind.GLOBAL, ScopeKind.ACCOUNT, ScopeKind.CONVERSATION, ScopeKind.MESSAGE)
        override val applyMode: ApplyMode = ApplyMode.NEXT_ITEM
        override fun outboundNodes(): List<OutboundNode> = listOf(object : OutboundNode {
            override val key: String = "throwing-outbound"
            override suspend fun execute(frame: OutboundFrame): NodeResult = error("node boom")
        })
    }

    private object ThrowingOutboundFeatureProvider : TakinaFeatureProvider<ThrowingOutboundFeature> {
        override val id: String = "throwing-outbound"
        override val featureType = ThrowingOutboundFeature::class
        override fun create(): ThrowingOutboundFeature = ThrowingOutboundFeature()
    }

    private class ManualCloseTransport(
        private val config: ConnectionConfig,
        private val callbacks: XmppTransportCallbacks,
    ) : XmppTransport {
        override suspend fun connect(password: String, onPhase: suspend (XmppConnectPhase) -> Unit): XmppSession {
            onPhase(XmppConnectPhase.TCP_CONNECTING)
            onPhase(XmppConnectPhase.STREAM_OPENING)
            onPhase(XmppConnectPhase.AUTHENTICATING)
            onPhase(XmppConnectPhase.BINDING_RESOURCE)
            return XmppSession("${config.owner}/${config.resource}")
        }

        override suspend fun sendRaw(xml: String) = Unit

        override suspend fun disconnect() = Unit

        suspend fun closeUnexpectedly(authHardFailure: Boolean) {
            callbacks.onClosed("forced-close", authHardFailure)
        }
    }

    @Test
    fun `core startup initializes runtime states`() = runTest {
        val takina = createTakina {
            addAccount {
                jid = alice
                password = "secret"
            }
        }
        assertEquals(AccountState.REGISTERED, takina.runtime.accountStates.value.getValue(alice))
        assertEquals(ConnectionState.IDLE, takina.runtime.connectionStates.value.getValue(alice))
    }

    @Test
    fun `config apply emits rejected event for invalid value`() = runTest {
        var rejected = 0
        var rejectCode: ConfigRejectCode? = null
        val takina = createTakina {
            addAccount {
                jid = alice
                password = "secret"
            }
        }

        takina.events.on(ConfigRejectedEvent::class) {
            rejected += 1
            rejectCode = this.rejectCode
        }

        takina.configs {
            set(ReconnectConfigPaths.ENABLED, "false")
        }

        assertEquals(1, rejected)
        assertEquals(ConfigRejectCode.TYPE_MISMATCH, rejectCode)
    }

    @Test
    fun `feature explain and scope override are available`() = runTest {
        val takina = createTakina {
            addAccount {
                jid = alice
                password = "secret"
            }
            features {
                install(DummyFeatureProvider)
            }
        }

        takina.capabilities {
            disable(DummyFeatureProvider, scope = Scope.Global)
            enable(DummyFeatureProvider, scope = Scope.Message(alice, bob, "m1"))
        }

        val global = takina.runtime.explainWhyEnabled("feature:dummy", Scope.Global)
        val message = takina.runtime.explainWhyEnabled("feature:dummy", Scope.Message(alice, bob, "m1"))
        assertTrue(!global.enabled)
        assertTrue(message.enabled)
    }

    @Test
    fun `connection host is defined by account and cannot be overridden by configs`() = runTest {
        val captured = mutableListOf<ConnectionConfig>()
        val sent = mutableListOf<String>()
        val oldFactory = XmppTransportFactoryRegistry.factory
        XmppTransportFactoryRegistry.factory = { config, _ ->
            captured += config
            RecordingTransport(config, sent)
        }
        try {
            val takina = createTakina {
                addAccount {
                    jid = alice
                    password = "secret"
                    connection {
                        host = "first.example.com"
                    }
                }
            }

            takina.connect(alice)
            takina.disconnect(alice)

            assertFailsWith<IllegalArgumentException> {
                takina.configs {
                    set(ConnectionConfigPaths.HOST, "second.example.com", scope = Scope.Account(alice))
                }
            }
            takina.connect(alice)

            assertEquals("first.example.com", captured.first().host)
            assertEquals("first.example.com", captured.last().host)
            takina.shutdown()
        } finally {
            XmppTransportFactoryRegistry.factory = oldFactory
        }
    }

    @Test
    fun `message presence and iq all pass through business outbound pipeline`() = runTest {
        val sent = mutableListOf<String>()
        val oldFactory = XmppTransportFactoryRegistry.factory
        XmppTransportFactoryRegistry.factory = { config, _ ->
            RecordingTransport(config, sent)
        }
        try {
            val takina = createTakina {
                addAccount {
                    jid = alice
                    password = "secret"
                }
                features {
                    install(MarkerOutboundFeatureProvider)
                }
            }

            takina.connect(alice)
            takina.request.message { to = bob; body = "m" }.send()
            takina.request.presence { to = bob; show = PresenceShow.Chat }.send()
            takina.request.iq { to = bob; type = "get"; payload = xml("ping") { selfClosing() } }.send()

            assertEquals(3, sent.size)
            assertTrue(sent.all { it.startsWith("<!--marker-->") })
            takina.shutdown()
        } finally {
            XmppTransportFactoryRegistry.factory = oldFactory
        }
    }

    @Test
    fun `send returns Err when account is not connected`() = runTest {
        val takina = createTakina {
            addAccount {
                jid = alice
                password = "secret"
            }
        }

        val result = takina.request.message {
            to = bob
            body = "offline"
        }.send()

        assertTrue(result is TakinaResult.Err)
        assertEquals("TAKINA-TRANSPORT-106", result.error.code)
    }

    @Test
    fun `auth hard connect failure marks account failed and closes connection state`() = runTest {
        val oldFactory = XmppTransportFactoryRegistry.factory
        XmppTransportFactoryRegistry.factory = { _, callbacks ->
            object : XmppTransport {
                override suspend fun connect(password: String, onPhase: suspend (XmppConnectPhase) -> Unit): XmppSession {
                    onPhase(XmppConnectPhase.TCP_CONNECTING)
                    throw TakinaFailureException(
                        TakinaErrors.of(
                            domain = ErrorDomain.AUTH,
                            number = 250,
                            message = "bad credentials",
                            retryable = false,
                        )
                    )
                }

                override suspend fun sendRaw(xml: String) = Unit
                override suspend fun disconnect() = Unit
            }
        }
        try {
            val takina = createTakina {
                addAccount {
                    jid = alice
                    password = "secret"
                }
            }

            val result = takina.connect(alice)
            assertTrue(result is TakinaResult.Err)
            assertEquals(AccountState.FAILED, takina.runtime.accountStates.value.getValue(alice))
            assertEquals(ConnectionState.CLOSED, takina.runtime.connectionStates.value.getValue(alice))
            takina.shutdown()
        } finally {
            XmppTransportFactoryRegistry.factory = oldFactory
        }
    }

    @Test
    fun `auth hard unexpected close does not schedule reconnect and ends in failed state`() = runTest {
        val oldFactory = XmppTransportFactoryRegistry.factory
        lateinit var transport: ManualCloseTransport
        XmppTransportFactoryRegistry.factory = { config, callbacks ->
            ManualCloseTransport(config, callbacks).also { transport = it }
        }
        try {
            var reconnectScheduled = 0
            val takina = createTakina {
                addAccount {
                    jid = alice
                    password = "secret"
                }
            }
            takina.events.on(ReconnectScheduledEvent::class) { reconnectScheduled += 1 }

            val connectResult = takina.connect(alice)
            assertTrue(connectResult is TakinaResult.Ok)
            transport.closeUnexpectedly(authHardFailure = true)

            assertEquals(0, reconnectScheduled)
            assertEquals(AccountState.FAILED, takina.runtime.accountStates.value.getValue(alice))
            takina.shutdown()
        } finally {
            XmppTransportFactoryRegistry.factory = oldFactory
        }
    }

    @Test
    fun `stale transport close callback does not invalidate newer active session`() = runTest {
        val oldFactory = XmppTransportFactoryRegistry.factory
        lateinit var first: ManualCloseTransport
        lateinit var second: ManualCloseTransport
        var created = 0
        XmppTransportFactoryRegistry.factory = { config, callbacks ->
            created += 1
            when (created) {
                1 -> ManualCloseTransport(config, callbacks).also { first = it }
                else -> ManualCloseTransport(config, callbacks).also { second = it }
            }
        }
        try {
            val takina = createTakina {
                addAccount { jid = alice; password = "secret" }
                configs {
                    reconnect {
                        enabled = true
                        delayMillis = 0L
                        factor = 1.0
                        jitter = 0.0
                        maxAttempts = 1
                    }
                }
            }

            assertTrue(takina.connect(alice) is TakinaResult.Ok)
            first.closeUnexpectedly(authHardFailure = false)
            assertEquals(ConnectionState.ESTABLISHED, takina.runtime.connectionStates.value.getValue(alice))

            // Old lifecycle callback must be ignored after reconnect promoted the new transport.
            first.closeUnexpectedly(authHardFailure = false)
            assertEquals(ConnectionState.ESTABLISHED, takina.runtime.connectionStates.value.getValue(alice))

            val send = takina.request.message { to = bob; body = "still-connected" }.send()
            assertTrue(send is TakinaResult.Ok)
            takina.shutdown()
        } finally {
            XmppTransportFactoryRegistry.factory = oldFactory
        }
    }

    @Test
    fun `send returns Err when owner is ambiguous`() = runTest {
        val takina = createTakina {
            addAccount { jid = alice; password = "secret" }
            addAccount { jid = carol; password = "secret" }
        }

        val result = takina.request.message {
            to = bob
            body = "ambiguous"
        }.send()

        assertTrue(result is TakinaResult.Err)
        assertEquals("TAKINA-CONFIG-301", result.error.code)
    }

    @Test
    fun `pipeline node exception emits failure event and send continues`() = runTest {
        val sent = mutableListOf<String>()
        val oldFactory = XmppTransportFactoryRegistry.factory
        XmppTransportFactoryRegistry.factory = { config, _ -> RecordingTransport(config, sent) }
        try {
            var pipelineFailed = 0
            val takina = createTakina {
                addAccount { jid = alice; password = "secret" }
                features { install(ThrowingOutboundFeatureProvider) }
            }
            takina.events.on(PipelineNodeFailedEvent::class) {
                pipelineFailed += 1
                assertEquals("throwing-outbound", nodeKey)
            }

            takina.connect(alice)
            val result = takina.request.message { to = bob; body = "hello" }.send()

            assertTrue(result is TakinaResult.Ok)
            assertEquals(1, pipelineFailed)
            takina.shutdown()
        } finally {
            XmppTransportFactoryRegistry.factory = oldFactory
        }
    }

    @Test
    fun `connectAll returns completion summary and emits aggregated results when some accounts fail`() = runTest {
        val oldFactory = XmppTransportFactoryRegistry.factory
        XmppTransportFactoryRegistry.factory = { config, callbacks ->
            object : XmppTransport {
                override suspend fun connect(password: String, onPhase: suspend (XmppConnectPhase) -> Unit): XmppSession {
                    if (config.owner == carol) error("forced connect failure")
                    onPhase(XmppConnectPhase.TCP_CONNECTING)
                    onPhase(XmppConnectPhase.STREAM_OPENING)
                    onPhase(XmppConnectPhase.AUTHENTICATING)
                    onPhase(XmppConnectPhase.BINDING_RESOURCE)
                    return XmppSession("${config.owner}/${config.resource}")
                }

                override suspend fun sendRaw(xml: String) = Unit

                override suspend fun disconnect() = Unit
            }
        }
        try {
            var allConnectResults: Map<BareJid, TakinaResult<Unit>>? = null
            var allConnectFailed: Int? = null
            var allConnectSucceed: Int? = null
            val takina = createTakina {
                addAccount { jid = alice; password = "secret" }
                addAccount { jid = carol; password = "secret" }
            }
            takina.events.on(AllConnectEvent::class) {
                allConnectResults = results
                allConnectSucceed = outcome.succeed
                allConnectFailed = outcome.failed
            }

            val result = takina.connectAll()
            assertTrue(result is TakinaResult.Ok)
            assertEquals(1, result.value.succeed)
            assertEquals(1, result.value.failed)
            val emitted = requireNotNull(allConnectResults)
            assertEquals(2, emitted.size)
            assertTrue(emitted.getValue(alice) is TakinaResult.Ok)
            assertTrue(emitted.getValue(carol) is TakinaResult.Err)
            assertEquals(1, allConnectSucceed)
            assertEquals(1, allConnectFailed)

            takina.shutdown()
        } finally {
            XmppTransportFactoryRegistry.factory = oldFactory
        }
    }

    @Test
    fun `raw inbound and final outbound frame events are emitted and support companion listener syntax`() = runTest {
        val sent = mutableListOf<String>()
        val oldFactory = XmppTransportFactoryRegistry.factory
        XmppTransportFactoryRegistry.factory = { config, callbacks ->
            object : XmppTransport {
                override suspend fun connect(password: String, onPhase: suspend (XmppConnectPhase) -> Unit): XmppSession {
                    onPhase(XmppConnectPhase.TCP_CONNECTING)
                    onPhase(XmppConnectPhase.STREAM_OPENING)
                    onPhase(XmppConnectPhase.AUTHENTICATING)
                    onPhase(XmppConnectPhase.BINDING_RESOURCE)
                    callbacks.onFrame("<message from='bob@example.com'><body>hi</body></message>")
                    return XmppSession("${config.owner}/${config.resource}")
                }

                override suspend fun sendRaw(xml: String) {
                    sent += xml
                }

                override suspend fun disconnect() = Unit
            }
        }
        try {
            var inbound = 0
            var outbound = 0

            val takina = createTakina {
                addAccount {
                    jid = alice
                    password = "secret"
                }
            }
            takina.events.on(RawFrameInboundEvent) { inbound += 1 }
            takina.events.on(FinalFrameOutboundEvent) { outbound += 1 }

            takina.connect(alice)
            takina.request.message { to = bob; body = "hello" }.send()

            assertEquals(1, inbound)
            assertEquals(1, outbound)
            takina.shutdown()
        } finally {
            XmppTransportFactoryRegistry.factory = oldFactory
        }
    }

    @Test
    fun `async inbound frame from active transport still emits raw inbound event`() = runTest {
        val oldFactory = XmppTransportFactoryRegistry.factory
        XmppTransportFactoryRegistry.factory = { config, callbacks ->
            object : XmppTransport {
                override suspend fun connect(password: String, onPhase: suspend (XmppConnectPhase) -> Unit): XmppSession {
                    onPhase(XmppConnectPhase.TCP_CONNECTING)
                    onPhase(XmppConnectPhase.STREAM_OPENING)
                    onPhase(XmppConnectPhase.AUTHENTICATING)
                    onPhase(XmppConnectPhase.BINDING_RESOURCE)
                    CoroutineScope(Dispatchers.Default).launch {
                        delay(20)
                        callbacks.onFrame("<message from='bob@example.com'><body>async</body></message>")
                    }
                    return XmppSession("${config.owner}/${config.resource}")
                }

                override suspend fun sendRaw(xml: String) = Unit
                override suspend fun disconnect() = Unit
            }
        }
        try {
            var inbound = 0
            val inboundSignal = CompletableDeferred<Unit>()
            val takina = createTakina {
                addAccount { jid = alice; password = "secret" }
            }
            takina.events.on(RawFrameInboundEvent) {
                inbound += 1
                inboundSignal.complete(Unit)
            }

            assertTrue(takina.connect(alice) is TakinaResult.Ok)
            withContext(Dispatchers.Default) {
                withTimeout(1000) { inboundSignal.await() }
            }

            assertEquals(1, inbound)
            takina.shutdown()
        } finally {
            XmppTransportFactoryRegistry.factory = oldFactory
        }
    }
}
