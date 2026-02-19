package org.atoriapps.takina.examples

import org.atoriapps.takina.core.connections.SecurityMode
import org.atoriapps.takina.core.components.DiscoveryComponent
import org.atoriapps.takina.core.components.discovery
import org.atoriapps.takina.core.createTakina
import org.atoriapps.takina.core.events.ConnectionClosedEvent
import org.atoriapps.takina.core.events.ConnectionFailedEvent
import org.atoriapps.takina.core.events.StanzaReceivedEvent
import org.atoriapps.takina.core.xmpp.createBareJid
import org.atoriapps.takina.core.xmpp.toBareJid
import kotlinx.coroutines.runBlocking

/**
 * Usage (environment variables):
 * - TAKINA_JID=alice@example.com
 * - TAKINA_PASSWORD=secret
 * - TAKINA_HOST=example.com
 * - TAKINA_PORT=5222
 * - TAKINA_SECURITY=START_TLS | DIRECT_TLS | PLAIN
 * - TAKINA_RESOURCE=takina-smoke (optional)
 */
fun main() {
    val jid = (System.getenv("TAKINA_JID") ?: error("TAKINA_JID is required")).toBareJid()
    val password = System.getenv("TAKINA_PASSWORD") ?: error("TAKINA_PASSWORD is required")
    val host = System.getenv("TAKINA_HOST") ?: jid.domain
    val port = System.getenv("TAKINA_PORT")?.toIntOrNull()
    val security = runCatching {
        SecurityMode.valueOf(System.getenv("TAKINA_SECURITY") ?: "START_TLS")
    }.getOrDefault(SecurityMode.START_TLS)
    val resource = System.getenv("TAKINA_RESOURCE") ?: "takina-smoke"

    val takina = createTakina(registerAllComponents = false) {
        registerComponent(DiscoveryComponent)
        addAccount {
            this.jid = jid
            this.password { password }
            endpoint(host = host, port = port, securityMode = security)
            this.resource = resource
        }
    }

    takina.events.on(ConnectionFailedEvent) {
        println("连接失败：${it.jid} -> ${it.reason}")
    }
    takina.events.on(ConnectionClosedEvent) {
        println("连接关闭：${it.jid} -> ${it.reason}")
    }
    takina.events.on(StanzaReceivedEvent) {
        println("入站 ${it.stanzaType}：${it.xml}")
    }

    takina.connect(jid)

    takina.request.presence {
        from = jid
        status = "Takina smoke test online"
    }.send()

    runBlocking {
        val result = takina.discovery().discoInfoAwait(
            from = jid,
            to = createBareJid(domain = jid.domain),
            timeoutMillis = 8_000,
        ).awaitResult()
        println("disco 结果：type=${result.type} id=${result.id}")
    }

    println("已连接并发送初始 presence/disco，请等待入站 stanza...")
    Thread.sleep(5_000)
    takina.disconnect(jid)
}
