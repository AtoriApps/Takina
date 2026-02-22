package org.atoriapps.takina.examples

import kotlinx.coroutines.runBlocking
import org.atoriapps.takina.core.components.*
import org.atoriapps.takina.core.connections.SecurityMode
import org.atoriapps.takina.core.createTakina
import org.atoriapps.takina.core.events.ConnectionClosedEvent
import org.atoriapps.takina.core.events.ConnectionFailedEvent
import org.atoriapps.takina.core.events.FrameInboundEvent
import org.atoriapps.takina.core.events.FrameOutboundEvent
import org.atoriapps.takina.core.xmpp.createBareJid
import org.atoriapps.takina.core.xmpp.toBareJid

/**
 * Usage (environment variables):
 * - TAKINA_JID=alice@example.com (必填)
 * - TAKINA_PASSWORD=secret (必填)
 * - TAKINA_HOST=example.com
 * - TAKINA_PORT=5222
 * - TAKINA_SECURITY=START_TLS | DIRECT_TLS | PLAIN
 * - TAKINA_RESOURCE=takina-smoke (optional)
 * - TAKINA_SMOKE_DURATION_SECONDS=50 (optional)
 * - TAKINA_SMOKE_SEND_MSG=1 (optional, 测试发消息给人)
 * - TAKINA_SMOKE_SEND_MSG_TO=bob@example.com (配合 SEND_MSG，指定接受人)
 * - TAKINA_SMOKE_SEND_MSG_CONTENT=自定义消息内容 (optional)
 * - TAKINA_SMOKE_ROSTER=1 (optional, 测试 roster get)
 * - TAKINA_SMOKE_MAM=1 (optional, 测试 mam query)
 * - TAKINA_SMOKE_PUSH=1 (optional, 测试 push enable/disable)
 * - TAKINA_SMOKE_PUSH_SERVICE=push.example.com (配合 TAKINA_SMOKE_PUSH)
 * - TAKINA_SMOKE_PUSH_NODE=device-node (配合 TAKINA_SMOKE_PUSH，可留空)
 * - TAKINA_SMOKE_UPLOAD=1 (optional, 测试 slot 申请)
 * - TAKINA_SMOKE_UPLOAD_SERVICE=upload.example.com (配合 TAKINA_SMOKE_UPLOAD)
 * - TAKINA_SMOKE_UPLOAD_FILE=smoke.bin
 * - TAKINA_SMOKE_UPLOAD_SIZE=1024
 * - TAKINA_SMOKE_ALT_LINKS_XML=<XRD .../> (optional，测试 xep-0156 解析)
 * - TAKINA_SMOKE_ALT_LINKS_JSON={\"links\":[...]} (optional，测试 xep-0156 json 解析)
 */
fun main() {
    val jid = (System.getenv("TAKINA_JID") ?: error("必须提供 TAKINA_JID")).toBareJid()
    val password = System.getenv("TAKINA_PASSWORD") ?: error("必须提供 TAKINA_PASSWORD")
    val host = System.getenv("TAKINA_HOST") ?: jid.domain
    val port = System.getenv("TAKINA_PORT")?.toIntOrNull()
    val security = runCatching { SecurityMode.valueOf(System.getenv("TAKINA_SECURITY") ?: "START_TLS") }.getOrDefault(SecurityMode.START_TLS)
    val resource = System.getenv("TAKINA_RESOURCE") ?: "takina-smoke"
    val smokeDurationSeconds = System.getenv("TAKINA_SMOKE_DURATION_SECONDS")?.toIntOrNull() ?: 50

    val smokeSendMsg = System.getenv("TAKINA_SMOKE_SEND_MSG").toBooleanLike()
    val smokeRoster = System.getenv("TAKINA_SMOKE_ROSTER").toBooleanLike()
    val smokeMam = System.getenv("TAKINA_SMOKE_MAM").toBooleanLike()
    val smokePush = System.getenv("TAKINA_SMOKE_PUSH").toBooleanLike()
    val smokeUpload = System.getenv("TAKINA_SMOKE_UPLOAD").toBooleanLike()

    val takina = createTakina {
        onConfigureComponent(StreamManagementComponent) {
            autoReconnectMaxAttempts = 8
            autoReconnectDelayMillis = 2000
        }

        addAccount {
            this.jid = jid
            this.password = password

            endpoint(host = host, port = port, securityMode = security)
            this.resource = resource
        }
    }

    takina.events.on(ConnectionFailedEvent) { println("连接失败：${it.jid} -> ${it.reason}") }
    takina.events.on(ConnectionClosedEvent) { println("连接关闭：${it.jid} -> ${it.reason}") }
    takina.events.on(FrameInboundEvent) { println("入站：${it.xml}") }
    takina.events.on(FrameOutboundEvent) { println("出站：${it.xml}") }

    takina.connectAll()

    takina.request.presence {
        status = "骄傲地宣告：本账号正在进行Takina客户端冒烟测试，爱来自中国"
    }.send()

    if (smokeSendMsg) {
        val toJid = System.getenv("TAKINA_SMOKE_SEND_MSG_TO") ?: error("启用发消息冒烟时必须提供接收方Jid")
        takina.request.message {
            to = toJid.toBareJid()
            body = System.getenv("TAKINA_SMOKE_SEND_MSG_CONTENT") ?: "本消息由Takina客户端发送"
        }.send()
    }

    runBlocking {
        val result = takina.discovery.discoInfoAwait(
            from = jid,
            to = createBareJid(domain = jid.domain),
            timeoutMillis = 8000,
        ).awaitResult()
        println("disco 结果：type=${result.type} id=${result.id}")

        if (smokeRoster) runCatching {
            val rosterResult = takina.roster.rosterGet(from = jid, timeoutMillis = 8_000).awaitResult()
            println("roster 测试通过：type=${rosterResult.type} id=${rosterResult.id}")
        }.onFailure { println("roster 测试失败：${it.message}") }

        if (smokeMam) runCatching {
            val mamResult = takina.mam.queryArchiveAwait(from = jid, with = jid, pageMax = 1, timeoutMillis = 8_000).awaitResult()
            println("mam 测试通过：type=${mamResult.type} id=${mamResult.id}")
        }.onFailure { println("mam 测试失败：${it.message}") }

        if (smokePush) runCatching {
            val service = (System.getenv("TAKINA_SMOKE_PUSH_SERVICE") ?: error("启用 push 冒烟时必须提供 TAKINA_SMOKE_PUSH_SERVICE")).toBareJid()
            val node = System.getenv("TAKINA_SMOKE_PUSH_NODE")
            val enable = takina.csiPush.enablePushAwait(pushServiceJid = service, node = node, from = jid, timeoutMillis = 8_000).awaitResult()
            println("push enable 响应：type=${enable.type} id=${enable.id}")
            val disable = takina.csiPush.disablePushAwait(pushServiceJid = service, node = node, from = jid, timeoutMillis = 8_000).awaitResult()
            println("push disable 响应：type=${disable.type} id=${disable.id}")
        }.onFailure { println("push 测试失败：${it.message}") }

        if (smokeUpload) runCatching {
            val uploadService = (System.getenv("TAKINA_SMOKE_UPLOAD_SERVICE") ?: error("启用 upload 冒烟时必须提供 TAKINA_SMOKE_UPLOAD_SERVICE")).toBareJid()
            val filename = System.getenv("TAKINA_SMOKE_UPLOAD_FILE") ?: "smoke.bin"
            val size = System.getenv("TAKINA_SMOKE_UPLOAD_SIZE")?.toLongOrNull() ?: 1024L
            val slotResult = takina.httpUpload.requestSlotAwait(
                filename = filename,
                size = size,
                contentType = "application/octet-stream",
                from = jid,
                to = uploadService,
                timeoutMillis = 8000,
            ).awaitResult()
            val slot = takina.httpUpload.parseSlotResult(slotResult.xml)
            println("upload slot 测试：put=${slot?.putUrl ?: "<none>"} get=${slot?.getUrl ?: "<none>"}")
        }.onFailure { println("upload 测试失败：${it.message}") }
    }

    System.getenv("TAKINA_SMOKE_ALT_LINKS_XML")?.let { raw ->
        val endpoints = takina.connectionDiscovery.parseHostMetaLinks(raw)
        println("xep-0156 xml 解析到备用连接数=${endpoints.size}")
    }

    System.getenv("TAKINA_SMOKE_ALT_LINKS_JSON")?.let { raw ->
        val endpoints = takina.connectionDiscovery.parseHostMetaJsonLinks(raw)
        println("xep-0156 json 解析到备用连接数=${endpoints.size}")
    }

    println("已连接并执行基础冒烟，接下来是您的自由测试时间（$smokeDurationSeconds 秒）")

    Thread.sleep(smokeDurationSeconds * 1000L)

    takina.disconnect(jid)
}

private fun String?.toBooleanLike(): Boolean = this != null && (equals("1") || equals("true", ignoreCase = true))
