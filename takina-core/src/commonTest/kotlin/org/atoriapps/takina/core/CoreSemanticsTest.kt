package org.atoriapps.takina.core

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.atoriapps.takina.core.connections.AccountState
import org.atoriapps.takina.core.connections.ConnectionConfigPaths
import org.atoriapps.takina.core.connections.ConnectionConfig
import org.atoriapps.takina.core.connections.ConnectionState
import org.atoriapps.takina.core.connections.ReconnectConfigPaths
import org.atoriapps.takina.core.connections.XmppTransport
import org.atoriapps.takina.core.connections.XmppTransportCallbacks
import org.atoriapps.takina.core.connections.XmppTransportFactoryRegistry
import org.atoriapps.takina.core.controlling.ApplyMode
import org.atoriapps.takina.core.events.ConfigRejectedEvent
import org.atoriapps.takina.core.events.FinalFrameOutboundEvent
import org.atoriapps.takina.core.events.RawFrameInboundEvent
import org.atoriapps.takina.core.features.TakinaFeature
import org.atoriapps.takina.core.features.TakinaFeatureProvider
import org.atoriapps.takina.core.models.Scope
import org.atoriapps.takina.core.models.ScopeKind
import org.atoriapps.takina.core.models.toBareJid
import org.atoriapps.takina.core.pipeline.NodeResult
import org.atoriapps.takina.core.pipeline.OutboundFrame
import org.atoriapps.takina.core.pipeline.OutboundNode
import org.atoriapps.takina.core.request.PresenceShow

class CoreSemanticsTest {
    private val alice = "alice@example.com".toBareJid()
    private val bob = "bob@example.com".toBareJid()

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
        private val callbacks: XmppTransportCallbacks,
        private val sentSink: MutableList<String>,
    ) : XmppTransport {
        override var boundJid: String? = null
        private var connected = false
        override val isConnected: Boolean get() = connected

        override suspend fun connect(password: String) {
            callbacks.onStateChanged(ConnectionState.TCP_CONNECTING)
            callbacks.onStateChanged(ConnectionState.STREAM_OPENING)
            callbacks.onStateChanged(ConnectionState.AUTHENTICATING)
            callbacks.onStateChanged(ConnectionState.BINDING_RESOURCE)
            boundJid = "${config.owner}/${config.resource}"
            connected = true
            callbacks.onStateChanged(ConnectionState.ESTABLISHED)
        }

        override suspend fun sendRaw(xml: String) {
            sentSink += xml
        }

        override suspend fun disconnect() {
            connected = false
            boundJid = null
            callbacks.onStateChanged(ConnectionState.CLOSED)
        }
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
        val takina = createTakina {
            addAccount {
                jid = alice
                password = "secret"
            }
        }

        takina.events.on(ConfigRejectedEvent::class) { rejected += 1 }

        takina.configs {
            set(ReconnectConfigPaths.ENABLED, "false")
        }

        assertEquals(1, rejected)
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
        XmppTransportFactoryRegistry.factory = { config, callbacks ->
            captured += config
            RecordingTransport(config, callbacks, sent)
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
        XmppTransportFactoryRegistry.factory = { config, callbacks ->
            RecordingTransport(config, callbacks, sent)
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
            takina.request.iq { to = bob; type = "get"; payload = "<ping/>" }.send()

            assertEquals(3, sent.size)
            assertTrue(sent.all { it.startsWith("<!--marker-->") })
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
                override var boundJid: String? = null
                private var connected = false
                override val isConnected: Boolean get() = connected

                override suspend fun connect(password: String) {
                    callbacks.onStateChanged(ConnectionState.TCP_CONNECTING)
                    callbacks.onStateChanged(ConnectionState.STREAM_OPENING)
                    callbacks.onStateChanged(ConnectionState.AUTHENTICATING)
                    callbacks.onStateChanged(ConnectionState.BINDING_RESOURCE)
                    boundJid = "${config.owner}/${config.resource}"
                    connected = true
                    callbacks.onStateChanged(ConnectionState.ESTABLISHED)
                    callbacks.onFrame("<message from='bob@example.com'><body>hi</body></message>")
                }

                override suspend fun sendRaw(xml: String) {
                    sent += xml
                }

                override suspend fun disconnect() {
                    connected = false
                    boundJid = null
                    callbacks.onStateChanged(ConnectionState.CLOSED)
                }
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
}
