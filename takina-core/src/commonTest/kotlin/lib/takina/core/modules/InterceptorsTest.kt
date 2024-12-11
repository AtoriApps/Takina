package lib.takina.core.modules

import lib.takina.DummyTakina
import lib.takina.core.Context
import lib.takina.core.builder.TakinaConfigDsl
import lib.takina.core.builder.createConfiguration
import lib.takina.core.requests.RequestBuilder
import lib.takina.core.xml.Element
import lib.takina.core.xml.response
import lib.takina.core.xmpp.*
import lib.takina.core.xmpp.modules.PingModule
import lib.takina.core.xmpp.stanzas.IQ
import lib.takina.core.xmpp.stanzas.IQType
import lib.takina.core.xmpp.stanzas.iq
import lib.takina.requestResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

@TakinaConfigDsl
interface InterceptorTestModuleConfig

class InterceptorTestModule(context: Context) : InterceptorTestModuleConfig, StanzaInterceptor, AbstractXmppIQModule(
    context, PingModule.TYPE, arrayOf(PingModule.XMLNS), Criterion.chain(
        Criterion.name(IQ.NAME), Criterion.xmlns(PingModule.XMLNS)
    )
) {

    companion object : XmppModuleProvider<InterceptorTestModule, InterceptorTestModuleConfig> {

        const val XMLNS = "urn:xmpp:ping"
        override val TYPE = XMLNS
        override fun configure(module: InterceptorTestModule, cfg: InterceptorTestModuleConfig.() -> Unit) =
            module.cfg()

        override fun instance(context: Context): InterceptorTestModule = InterceptorTestModule(context)

        override fun doAfterRegistration(module: InterceptorTestModule, moduleManager: ModulesManager) =
            moduleManager.registerInterceptors(arrayOf(module))

    }

    val interceptedReceived = mutableListOf<Element>()
    val interceptedSent = mutableListOf<Element>()

    fun ping(jid: JID? = null): RequestBuilder<Unit, IQ> {
        val stanza = iq {
            type = IQType.Get
            if (jid != null) to = jid
            "ping" {
                xmlns = XMLNS
            }
        }
        return context.request.iq(stanza).map { }
    }

    override fun processGet(element: IQ) {
        context.writer.writeDirectly(response(element) { })
    }

    override fun processSet(element: IQ) {
        throw XMPPException(ErrorCondition.NotAcceptable)
    }

    override fun afterReceive(element: Element): Element {
        interceptedReceived += element
        return element
    }

    override fun beforeSend(element: Element): Element {
        interceptedSent += element
        return element
    }

}

class InterceptorsTest {

    val takina = DummyTakina(createConfiguration(false) {
        auth {
            userJID = "user@example.com".toBareJID()
            password { "pencil" }
        }
        install(InterceptorTestModule)
    }).apply {
        connect()
    }

    @Test
    fun test_send_interceptor() {
        takina.requestResponse<Unit> {
            request {
                it.getModule(InterceptorTestModule).ping("entity@faraway.com".toJID())
            }
            expectedRequest {
                iq {
                    type = IQType.Get
                    to = "entity@faraway.com".toJID()
                    "ping" {
                        xmlns = "urn:xmpp:ping"
                    }
                }
            }
            response {
                iq {
                    from = "entity@faraway.com".toJID()
                    to = "user@example.scom/1234".toJID()
                    type = IQType.Result
                }
            }
            validate {
                assertNotNull(it).let {
                    it.onFailure { fail(cause = it) }
                    it.onSuccess { }
                }
            }
        }

        val module = takina.getModule(InterceptorTestModule)

        println(module.interceptedSent)

        assertEquals(1, module.interceptedSent.size)
        assertEquals(1, module.interceptedReceived.size)

        assertEquals("entity@faraway.com", module.interceptedSent[0].attributes["to"])

        assertEquals("user@example.scom/1234", module.interceptedReceived[0].attributes["to"])
        assertEquals("entity@faraway.com", module.interceptedReceived[0].attributes["from"])
    }

}