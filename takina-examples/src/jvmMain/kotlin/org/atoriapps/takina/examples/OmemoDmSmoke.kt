package org.atoriapps.takina.examples

import kotlinx.coroutines.runBlocking
import org.atoriapps.takina.core.components.OmemoComponent
import org.atoriapps.takina.core.components.omemo
import org.atoriapps.takina.core.createTakina
import org.atoriapps.takina.core.events.FrameInboundEvent
import org.atoriapps.takina.core.events.FrameOutboundEvent
import org.atoriapps.takina.core.events.StanzaReceivedEvent
import org.atoriapps.takina.core.requests.omemoProvider
import org.atoriapps.takina.core.xmpp.toBareJid

/**
 * 环境变量：
 * - TAKINA_A_JID / TAKINA_A_PASSWORD
 * - TAKINA_B_JID / TAKINA_B_PASSWORD
 * - TAKINA_OMEMO_MSG_A2B (可选，默认 "hello from A")
 * - TAKINA_OMEMO_MSG_B2A (可选，默认 "hello from B")
 */
fun main() = runBlocking {
    val aJid = (System.getenv("TAKINA_A_JID") ?: error("缺少 TAKINA_A_JID")).toBareJid()
    val aPassword = System.getenv("TAKINA_A_PASSWORD") ?: error("缺少 TAKINA_A_PASSWORD")
    val bJid = (System.getenv("TAKINA_B_JID") ?: error("缺少 TAKINA_B_JID")).toBareJid()
    val bPassword = System.getenv("TAKINA_B_PASSWORD") ?: error("缺少 TAKINA_B_PASSWORD")
    val msgA2B = System.getenv("TAKINA_OMEMO_MSG_A2B") ?: "hello from A"
    val msgB2A = System.getenv("TAKINA_OMEMO_MSG_B2A") ?: "hello from B"

    val takina = createTakina {
        addAccount {
            jid = aJid
            password = aPassword
            resource = "takina-omemo-a"
        }

        addAccount {
            jid = bJid
            password = bPassword
            resource = "takina-omemo-b"
        }
    }

    takina.events.enableEventLog = false

    takina.events.on(FrameInboundEvent) { println("${it.jid} 入站：${it.xml}") }
    takina.events.on(FrameOutboundEvent) { println("${it.jid} 出站：${it.xml}") }
    takina.events.on(StanzaReceivedEvent) { event ->
        if (event.stanzaType != "message") return@on
        val target = event.jid
        val decrypted = takina.omemo.decryptMessage(self = target, messageXml = event.xml) ?: return@on
        println("OMEMO解密成功：receiver=$target senderDevice=${decrypted.senderDeviceId} text=${decrypted.plaintext}")
    }

    takina.connectAll()

    takina.request.presence {
        from = aJid
        status = "骄傲地宣告：本账号正在扮演A方，进行Takina客户端OmemoDm冒烟测试，爱来自中国"
    }.send()
    takina.request.presence {
        from = bJid
        status = "骄傲地宣告：本账号正在扮演A方，进行Takina客户端OmemoDm冒烟测试，爱来自中国"
    }.send()

    Thread.sleep(500L) // 等待上线完成

    println("作事前准备")
    takina.omemo.bootstrapLocalDevice(aJid)
    takina.omemo.bootstrapLocalDevice(bJid)
    takina.omemo.publishOwnMaterial(aJid)
    takina.omemo.publishOwnMaterial(bJid)

    val syncAB = takina.omemo.syncContactMaterial(from = aJid, contact = bJid)
    val syncBA = takina.omemo.syncContactMaterial(from = bJid, contact = aJid)
    println("同步完成：A->B devices=${syncAB.devices.size} bundles=${syncAB.fetchedBundles}；B->A devices=${syncBA.devices.size} bundles=${syncBA.fetchedBundles}")

    println("发加密消息")
    takina.request.message {
        from = aJid
        to = bJid
        body = msgA2B
        encryption {
            provider = omemoProvider()
            fallbackBody = "这这不能2B"
        }
    }.send()
    takina.request.message {
        from = bJid
        to = aJid
        body = msgB2A
        encryption {
            provider = omemoProvider()
            fallbackBody = "这这不能2A"
        }
    }.send()

    Thread.sleep(120_000L) // 久等一下
    takina.disconnectAll()
}
