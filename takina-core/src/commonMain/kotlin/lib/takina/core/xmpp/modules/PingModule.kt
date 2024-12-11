
package lib.takina.core.xmpp.modules

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import lib.takina.core.Context
import lib.takina.core.builder.TakinaConfigDsl
import lib.takina.core.modules.AbstractXmppIQModule
import lib.takina.core.modules.Criterion
import lib.takina.core.modules.XmppModuleProvider
import lib.takina.core.requests.RequestBuilder
import lib.takina.core.xml.response
import lib.takina.core.xmpp.ErrorCondition
import lib.takina.core.xmpp.JID
import lib.takina.core.xmpp.XMPPException
import lib.takina.core.xmpp.stanzas.IQ
import lib.takina.core.xmpp.stanzas.IQType
import lib.takina.core.xmpp.stanzas.iq
import kotlin.time.Duration

/**
 * Configuration of [PingModule].
 */
@TakinaConfigDsl
interface PingModuleConfig

/**
 * Module is implementing XMPP Ping ([XEP-0199](https://xmpp.org/extensions/xep-0199.html)).
 *
 */
class PingModule(context: Context) : PingModuleConfig, AbstractXmppIQModule(
	context, TYPE, arrayOf(XMLNS), Criterion.chain(
		Criterion.name(IQ.NAME), Criterion.xmlns(XMLNS)
	)
) {

	companion object : XmppModuleProvider<PingModule, PingModuleConfig> {

		const val XMLNS = "urn:xmpp:ping"
		override val TYPE = XMLNS
		override fun configure(module: PingModule, cfg: PingModuleConfig.() -> Unit) = module.cfg()

		override fun instance(context: Context): PingModule = PingModule(context)

	}

	/**
	 * Prepares a ping request using XMPP Ping (XEP-0199).
	 *
	 * @param jid The JID (Jabber ID) to ping. If null, a ping is sent to the server.
	 * @return A RequestBuilder that can be used to handle the ping response.
	 */
	fun ping(jid: JID? = null): RequestBuilder<Pong, IQ> {
		val stanza = iq {
			type = IQType.Get
			if (jid != null) to = jid
			"ping" {
				xmlns = XMLNS
			}
		}
		var time0: Instant = Clock.System.now()
		return context.request.iq(stanza).onSend { time0 = Clock.System.now() }.map { Pong(Clock.System.now() - time0) }
	}

	override fun processGet(element: IQ) {
		context.writer.writeDirectly(response(element) { })
	}

	override fun processSet(element: IQ) {
		throw XMPPException(ErrorCondition.NotAcceptable)
	}

	/**
	 * Ping response.
	 */
	data class Pong(
		/** Measured response time. */
		val time: Duration,
	)

}