package org.atoriapps.takina.tests

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.atoriapps.takina.core.createTakina
import org.atoriapps.takina.core.connections.ConnectionState
import org.atoriapps.takina.core.connections.SecurityMode
import org.atoriapps.takina.core.events.FinalFrameOutboundEvent
import org.atoriapps.takina.core.events.RawFrameInboundEvent
import org.atoriapps.takina.core.models.BareJid
import org.atoriapps.takina.core.models.toBareJid
import org.atoriapps.takina.core.models.TakinaResult
import org.atoriapps.takina.core.xml.xml

class XmppClientTest {
    @Test
    fun `connect and smoke send against real server`() = runBlocking {
        val env = readEnvOrSkip()

        val takina = createTakina {
            addAccount {
                jid = env.jid
                password = env.password

                connection {
                    host = env.host
                    port = env.port
                    securityMode = env.securityMode
                    trustAllCertificates = env.trustAllCertificates
                    if (env.saslMechanisms.isNotEmpty()) saslMechanisms = env.saslMechanisms
                }
            }
        }

        CoroutineScope(Dispatchers.Default).launch {
            takina.runtime.connectionStates.collect {
                it.forEach{p->
                    println("状态变更：${p.key} -> ${p.value}")
                }
            }
        }
        takina.events.on(RawFrameInboundEvent) { println("入 $owner：$xml") }
        takina.events.on(FinalFrameOutboundEvent) { println("出 $owner：$xml") }

        val connectAllResult = takina.connectAll()
        assertTrue(connectAllResult is TakinaResult.Ok)
        assertEquals(ConnectionState.ESTABLISHED, takina.runtime.connectionStates.value.values.first())

        val target = env.messageTo?.toBareJid() ?: env.jid
        val messageResult = takina.request.message {
            to = target
            body = "你好啊一个一个"
            // ？？？我加密和加密提供者呢？？？
        }.send()
        assertTrue(messageResult is TakinaResult.Ok)

        val presenceResult = takina.request.presence {
            status = env.presenceStr
        }.send()
        assertTrue(presenceResult is TakinaResult.Ok)

        val iqResult = takina.request.iq {
            to = target
            type = "get"
            payload = xml("query") {
                attr("xmlns", "jabber:iq:version")
                selfClosing()
            }
        }.send()
        assertTrue(iqResult is TakinaResult.Ok)

        delay(50_000) // tmd等待

        takina.shutdown()
    }

    private fun readEnvOrSkip(): IntegrationEnv {
        val rawJid = System.getenv("JID")
        val password = System.getenv("PASSWORD")

        assumeTrue(
            "Skipping external integration test. Set JID, PASSWORD.",
            !rawJid.isNullOrBlank() && !password.isNullOrBlank(),
        )

        val jid = rawJid.toBareJid()
        val host = System.getenv("HOST") ?: jid.domain

        val port = System.getenv("PORT")?.toIntOrNull()
        val security = parseSecurityMode(System.getenv("SECURITY_MODE"))
        val trustAll = System.getenv("TRUST_ALL")?.equals("true", ignoreCase = true) == true
        val messageTo = System.getenv("MSG_TO")
        val presenceStr = System.getenv("PRESENCE") ?: "骄傲使用Takina 1.x"
        val sasl = System.getenv("SASL")
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

        return IntegrationEnv(
            host,
            port,
            security,
            jid,
            password,
            trustAll,
            presenceStr,
            messageTo,
            sasl
        )
    }

    private fun parseSecurityMode(raw: String?): SecurityMode = when (raw?.trim()?.uppercase()) {
        "DIRECT_TLS" -> SecurityMode.DIRECT_TLS
        "PLAIN" -> SecurityMode.PLAIN
        else -> SecurityMode.START_TLS
    }

    private data class IntegrationEnv(
        val host: String,
        val port: Int?,
        val securityMode: SecurityMode,
        val jid: BareJid,
        val password: String,
        val trustAllCertificates: Boolean,
        val presenceStr: String,
        val messageTo: String?,
        val saslMechanisms: List<String>,
    )
}
