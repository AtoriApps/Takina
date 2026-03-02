package org.atoriapps.takina.core

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import org.atoriapps.takina.core.connections.ConnectionConfig
import org.atoriapps.takina.core.connections.ConnectionState
import org.atoriapps.takina.core.connections.XmppTransport
import org.atoriapps.takina.core.connections.XmppTransportCallbacks
import org.atoriapps.takina.core.connections.XmppTransportFactoryRegistry
import org.atoriapps.takina.core.controlling.ApplyMode
import org.atoriapps.takina.core.features.TakinaFeature
import org.atoriapps.takina.core.features.TakinaFeatureProvider
import org.atoriapps.takina.core.models.Scope
import org.atoriapps.takina.core.models.ScopeKind
import org.atoriapps.takina.core.models.toBareJid

class AccountScopedDslTest {
    private val alice = "alice@example.com".toBareJid()

    private class AccountSwitchableFeature : TakinaFeature {
        override val supportedScopes: Set<ScopeKind> = setOf(
            ScopeKind.GLOBAL,
            ScopeKind.ACCOUNT,
            ScopeKind.CONVERSATION,
            ScopeKind.MESSAGE,
        )
        override val applyMode: ApplyMode = ApplyMode.IMMEDIATE
    }

    private object AccountSwitchableFeatureProvider : TakinaFeatureProvider<AccountSwitchableFeature> {
        override val id: String = "account-switchable"
        override val featureType = AccountSwitchableFeature::class
        override fun create(): AccountSwitchableFeature = AccountSwitchableFeature()
    }

    private class RecordingTransport(
        private val config: ConnectionConfig,
        private val callbacks: XmppTransportCallbacks,
        private val configSink: MutableList<ConnectionConfig>,
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
            configSink += config
        }

        override suspend fun sendRaw(xml: String) = Unit

        override suspend fun disconnect() {
            connected = false
            callbacks.onStateChanged(ConnectionState.CLOSED)
        }
    }

    @Test
    fun `addAccount dsl supports account scoped capability and config at bootstrap`() = runTest {
        val capturedConfig = mutableListOf<ConnectionConfig>()
        val oldFactory = XmppTransportFactoryRegistry.factory
        XmppTransportFactoryRegistry.factory = { config, callbacks ->
            RecordingTransport(config, callbacks, capturedConfig)
        }
        try {
            val takina = createTakina {
                features { install(AccountSwitchableFeatureProvider) }
                addAccount {
                    jid = alice
                    password = "secret"
                    capabilities { disable(AccountSwitchableFeatureProvider) }
                    configs { set("connection.host", "bootstrap.example.com") }
                }
            }

            val accountExplain = takina.runtime.explainWhyEnabled("feature:account-switchable", Scope.Account(alice))
            assertFalse(accountExplain.enabled)

            takina.connect(alice)
            assertEquals("bootstrap.example.com", capturedConfig.single().host)
            takina.shutdown()
        } finally {
            XmppTransportFactoryRegistry.factory = oldFactory
        }
    }

    @Test
    fun `addAccount dsl account scoped entries are not order sensitive to jid declaration`() = runTest {
        val capturedConfig = mutableListOf<ConnectionConfig>()
        val oldFactory = XmppTransportFactoryRegistry.factory
        XmppTransportFactoryRegistry.factory = { config, callbacks ->
            RecordingTransport(config, callbacks, capturedConfig)
        }
        try {
            val takina = createTakina {
                features { install(AccountSwitchableFeatureProvider) }
                addAccount {
                    configs { set("connection.host", "late-jid.example.com") }
                    capabilities { disable(AccountSwitchableFeatureProvider) }
                    pipelines {
                        outbound {
                            about("late-jid-node") { enabled = false }
                        }
                    }
                    jid = alice
                    password = "secret"
                }
            }

            assertFalse(takina.runtime.explainWhyEnabled("feature:account-switchable", Scope.Account(alice)).enabled)
            assertFalse(takina.runtime.explainWhyEnabled("node:late-jid-node", Scope.Account(alice)).enabled)

            takina.connect(alice)
            assertEquals("late-jid.example.com", capturedConfig.single().host)
            takina.shutdown()
        } finally {
            XmppTransportFactoryRegistry.factory = oldFactory
        }
    }

    @Test
    fun `addAccount api supports account scoped capability and config at runtime`() = runTest {
        val capturedConfig = mutableListOf<ConnectionConfig>()
        val oldFactory = XmppTransportFactoryRegistry.factory
        XmppTransportFactoryRegistry.factory = { config, callbacks ->
            RecordingTransport(config, callbacks, capturedConfig)
        }
        try {
            val takina = createTakina {
                features { install(AccountSwitchableFeatureProvider) }
            }

            takina.addAccount {
                jid = alice
                password = "secret"
                capabilities { disable(AccountSwitchableFeatureProvider) }
                configs { set("connection.host", "runtime.example.com") }
            }

            val accountExplain = takina.runtime.explainWhyEnabled("feature:account-switchable", Scope.Account(alice))
            assertFalse(accountExplain.enabled)

            takina.connect(alice)
            assertEquals("runtime.example.com", capturedConfig.single().host)
            takina.shutdown()
        } finally {
            XmppTransportFactoryRegistry.factory = oldFactory
        }
    }

    @Test
    fun `addAccount dsl supports account scoped pipelines with account default scope`() = runTest {
        val takina = createTakina {
            addAccount {
                jid = alice
                password = "secret"
                pipelines {
                    outbound {
                        about("account-only-node") {
                            enabled = false
                        }
                    }
                }
            }
        }

        val accountNode = takina.runtime.explainWhyEnabled("node:account-only-node", Scope.Account(alice))
        assertFalse(accountNode.enabled)
    }

    @Test
    fun `account scoped capability and config reject global scope`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            createTakina {
                addAccount {
                    jid = alice
                    password = "secret"
                    capabilities {
                        disable(AccountSwitchableFeatureProvider, scope = Scope.Global)
                    }
                }
            }
        }

        assertFailsWith<IllegalArgumentException> {
            createTakina {
                addAccount {
                    jid = alice
                    password = "secret"
                    pipelines {
                        outbound {
                            about("bad-global-scope-node", scope = Scope.Global) {
                                enabled = false
                            }
                        }
                    }
                }
            }
        }

        val takina = createTakina {
            addAccount {
                jid = alice
                password = "secret"
            }
        }
        try {
            val account = takina.account(alice)

            assertFailsWith<IllegalArgumentException> {
                account.capabilities {
                    disable(AccountSwitchableFeatureProvider, scope = Scope.Global)
                }
            }
            assertFailsWith<IllegalArgumentException> {
                account.configs {
                    set("reconnect.enabled", false, scope = Scope.Global)
                }
            }
        } finally {
            takina.shutdown()
        }
    }
}
