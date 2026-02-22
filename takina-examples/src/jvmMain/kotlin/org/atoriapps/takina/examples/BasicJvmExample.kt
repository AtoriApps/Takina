package org.atoriapps.takina.examples

import org.atoriapps.takina.core.connections.SecurityMode
import org.atoriapps.takina.core.createTakina
import org.atoriapps.takina.core.events.AllConnectedEvent
import org.atoriapps.takina.core.events.ConnectionClosedEvent
import org.atoriapps.takina.core.events.ConnectionFailedEvent
import org.atoriapps.takina.core.events.FrameInboundEvent
import org.atoriapps.takina.core.events.StanzaReceivedEvent
import org.atoriapps.takina.core.events.FrameOutboundEvent
import org.atoriapps.takina.core.xmpp.toBareJid

fun main() {
    val demoUserJid = "alice@example.com".toBareJid()

    val takina = createTakina(registerAllComponents = false) {
        addAccount {
            jid = demoUserJid
            password { "replace-with-real-password" }
            endpoint(
                host = "example.com",
                port = 5223,
                securityMode = SecurityMode.DIRECT_TLS,
            )
            resource = "takina-jvm"
        }

        addAccount {
            jid = "bot@example.com".toBareJid()
            password { "replace-with-real-password" }
            endpoint(
                host = "example.com",
                port = 5223,
                securityMode = SecurityMode.DIRECT_TLS,
            )
            resource = "takina-bot"
        }
    }

    takina.events.on(AllConnectedEvent) {
        println("已尝试连接全部账号：${it.connectedCount}/${it.configuredCount}")
    }
    takina.events.on(ConnectionFailedEvent) {
        println("账号连接失败 ${it.jid}：${it.reason}")
    }
    takina.events.on(FrameInboundEvent) {
        println("入站包（${it.jid}）：${it.xml}")
    }
    takina.events.on(FrameOutboundEvent) {
        println("出站包（${it.jid}）：${it.xml}")
    }
    takina.events.on(ConnectionClosedEvent) {
        println("连接已关闭 ${it.jid}：${it.reason}")
    }

    val request = takina.request.message {
        from = demoUserJid
        to = "bob@example.com".toBareJid()
        body = "Hello from Takina"
    }

    println("待发送消息 XML：${request.toXml()}")
    // takina.connectAll()
    // request.send()
}
