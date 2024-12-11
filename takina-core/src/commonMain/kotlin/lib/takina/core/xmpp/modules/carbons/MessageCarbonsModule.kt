
package lib.takina.core.xmpp.modules.carbons

import lib.takina.core.AbstractTakina
import lib.takina.core.Context
import lib.takina.core.builder.ConfigurationException
import lib.takina.core.builder.TakinaConfigDsl
import lib.takina.core.eventbus.Event
import lib.takina.core.eventbus.EventDefinition
import lib.takina.core.modules.Criterion
import lib.takina.core.modules.ModulesManager
import lib.takina.core.modules.XmppModule
import lib.takina.core.modules.XmppModuleProvider
import lib.takina.core.requests.RequestBuilder
import lib.takina.core.xml.Element
import lib.takina.core.xml.element
import lib.takina.core.xmpp.ErrorCondition
import lib.takina.core.xmpp.JID
import lib.takina.core.xmpp.XMPPException
import lib.takina.core.xmpp.getFromAttr
import lib.takina.core.xmpp.modules.MessageModule
import lib.takina.core.xmpp.modules.auth.InlineFeatures
import lib.takina.core.xmpp.modules.auth.InlineProtocol
import lib.takina.core.xmpp.modules.auth.InlineProtocolStage
import lib.takina.core.xmpp.modules.auth.InlineResponse
import lib.takina.core.xmpp.stanzas.IQ
import lib.takina.core.xmpp.stanzas.IQType
import lib.takina.core.xmpp.stanzas.Message
import lib.takina.core.xmpp.stanzas.asStanza

sealed class CarbonEvent(@Suppress("unused") val fromJID: JID?, val stanza: Message) : Event(TYPE) {

	companion object : EventDefinition<CarbonEvent> {

		override val TYPE = "lib.takina.core.xmpp.modules.carbons.CarbonEvent"
	}

	class Sent(fromJID: JID?, stanza: Message) : CarbonEvent(fromJID, stanza)
	class Received(fromJID: JID?, stanza: Message) : CarbonEvent(fromJID, stanza)
}

@TakinaConfigDsl
interface MessageCarbonsModuleConfig

class MessageCarbonsModule(override val context: Context, private val forwardHandler: (Message) -> Unit) : XmppModule,
	InlineProtocol, MessageCarbonsModuleConfig {

	override val type = TYPE
	override val criteria = Criterion.chain(Criterion.name(Message.NAME), Criterion.xmlns(XMLNS))
	override val features: Array<String> = arrayOf(XMLNS)

	private var messageModule: MessageModule? = null

	companion object : XmppModuleProvider<MessageCarbonsModule, MessageCarbonsModuleConfig> {

		const val XMLNS = "urn:xmpp:carbons:2"
		override val TYPE = XMLNS
		private const val FORWARD_XMLNS = "urn:xmpp:forward:0"

		override fun instance(context: Context): MessageCarbonsModule {
			if (!(context is AbstractTakina)) throw ConfigurationException("Cannot create instance of MessageCarbonModule. Unsupported type of context.")
			return MessageCarbonsModule(context, context::processReceivedXmlElement)
		}

		override fun configure(module: MessageCarbonsModule, cfg: MessageCarbonsModuleConfig.() -> Unit) = module.cfg()

		override fun doAfterRegistration(module: MessageCarbonsModule, moduleManager: ModulesManager) =
			module.initialize()

	}

	private fun initialize() {
		this.messageModule = context.modules.getModuleOrNull(MessageModule.TYPE)
	}

	override fun process(element: Element) {
		val ownJid = context.boundJID?.bareJID
		val from = element.getFromAttr()
		if (from != null && from.bareJID != ownJid) throw XMPPException(ErrorCondition.NotAcceptable)
		element.getChildrenNS(XMLNS).firstOrNull()?.let {
			when (it.name) {
				"sent" -> processSent(it)
				"received" -> processReceived(it)
				else -> throw XMPPException(ErrorCondition.BadRequest)
			}
		}
	}

	@Suppress("unused")
	fun enable(): RequestBuilder<Unit, IQ> = context.request.iq {
		type = IQType.Set
		"enable" {
			xmlns = XMLNS
		}
	}.map { }

	@Suppress("unused")
	fun disable(): RequestBuilder<Unit, IQ> = context.request.iq {
		type = IQType.Set
		"disable" {
			xmlns = XMLNS
		}
	}.map { }

	private fun processSent(carbon: Element) {
		val msg = carbon.getChildrenNS("forwarded", FORWARD_XMLNS)?.getChildren(Message.NAME)?.firstOrNull()
			?.asStanza<Message>() ?: return
		
		forwardHandler.invoke(msg)
		//messageModule?.process(msg)
		context.eventBus.fire(CarbonEvent.Sent(msg.from, msg))
	}

	private fun processReceived(carbon: Element) {
		val msg = carbon.getChildrenNS("forwarded", FORWARD_XMLNS)?.getChildren(Message.NAME)?.firstOrNull()
			?.asStanza<Message>() ?: return

		forwardHandler.invoke(msg)
		context.eventBus.fire(CarbonEvent.Received(msg.from, msg))
	}

	override fun featureFor(features: InlineFeatures, stage: InlineProtocolStage): Element? {
		return if (stage == InlineProtocolStage.AfterBind && features.supports(XMLNS)) {
			element("enable") { xmlns = XMLNS }
		} else null
	}

	override fun process(response: InlineResponse) {
	}

}
