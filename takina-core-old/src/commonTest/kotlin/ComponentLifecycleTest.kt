package org.atoriapps.takina.core

import org.atoriapps.takina.core.components.TakinaComponentProvider
import org.atoriapps.takina.core.components.TakinaConnectionLifecycleComponent
import org.atoriapps.takina.core.xmpp.toBareJid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ComponentLifecycleTest {
    @Test
    fun lifecycleCallbacks_shouldInvokeInstallAndShutdown() {
        val traces = mutableListOf<String>()
        val takina = createTakina(registerAllComponents = false) {
            registerComponent(HookComponentProvider(traces))
            addAccount {
                jid = "alice@example.com".toBareJid()
                password { "password" }
            }
        }
        assertEquals(listOf("install"), traces)
        takina.shutdown()
        assertEquals(listOf("install", "shutdown"), traces)
    }

    @Test
    fun shutdown_shouldBlockFollowupOperations() {
        val takina = createTakina(registerAllComponents = false) {
            addAccount {
                jid = "alice@example.com".toBareJid()
                password { "password" }
            }
        }
        takina.shutdown()
        assertFailsWith<IllegalStateException> {
            takina.connect("alice@example.com".toBareJid())
        }
        assertFailsWith<IllegalStateException> {
            takina.request.message {
                from = "alice@example.com".toBareJid()
                to = "bob@example.com".toBareJid()
                body = "hello"
            }.send()
        }
    }

    private class HookComponent(
        private val traces: MutableList<String>,
    ) : TakinaConnectionLifecycleComponent {
        override fun onInstall(context: TakinaContext) {
            traces += "install"
        }

        override fun onShutdown(context: TakinaContext) {
            traces += "shutdown"
        }
    }

    private class HookComponentProvider(
        private val traces: MutableList<String>,
    ) : TakinaComponentProvider<HookComponent> {
        override fun getInstance(context: TakinaContext): HookComponent = HookComponent(traces)

        override fun getComponentType() = HookComponent::class
    }
}
