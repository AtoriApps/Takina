
package lib.takina.core.xmpp.modules

import lib.takina.core.Context
import lib.takina.core.builder.TakinaConfigDsl
import lib.takina.core.eventbus.Event
import lib.takina.core.eventbus.EventDefinition
import lib.takina.core.logger.LoggerFactory
import lib.takina.core.modules.Criteria
import lib.takina.core.modules.Criterion
import lib.takina.core.modules.XmppModule
import lib.takina.core.modules.XmppModuleProvider
import lib.takina.core.xml.Element
import lib.takina.core.xmpp.JID
import lib.takina.core.xmpp.modules.mix.MIXModule
import lib.takina.core.xmpp.modules.mix.isMixMessage
import lib.takina.core.xmpp.modules.pubsub.PubSubModule
import lib.takina.core.xmpp.modules.pubsub.isPubSubMessage
import lib.takina.core.xmpp.stanzas.Message
import lib.takina.core.xmpp.stanzas.wrap

/**
 * Event raised when Message stanza is received.
 * @param fromJID JabberID of stanza server.
 * @param stanza received [Message] stanza
 */
data class MessageReceivedEvent(val fromJID: JID?, val stanza: Message) : Event(TYPE) {

	companion object : EventDefinition<MessageReceivedEvent> {

		override val TYPE = "lib.takina.core.xmpp.modules.MessageReceivedEvent"
	}
}

@TakinaConfigDsl
interface MessageModuleConfig

/**
 * Incoming Message stanzas handler. The module is integrated part of XMPP Core protocol.
 */
class MessageModule(override val context: Context) : XmppModule, MessageModuleConfig {

	private val log = LoggerFactory.logger("lib.takina.core.xmpp.modules.MessageModule")

	override val type = TYPE
	override val criteria: Criteria = Criterion.element(this@MessageModule::isMessage)
	override val features: Array<String>? = null
	//	override val criteria = Criterion.name(Message.NAME)

	/**
	 * Incoming Message stanzas handler. The module is integrated part of XMPP Core protocol.
	 */
	companion object : XmppModuleProvider<MessageModule, MessageModuleConfig> {

		override val TYPE = "lib.takina.core.xmpp.modules.MessageModule"
		override fun instance(context: Context): MessageModule = MessageModule(context)

		override fun configure(module: MessageModule, cfg: MessageModuleConfig.() -> Unit) = module.cfg()
	}

	private fun isMessage(message: Element): Boolean = when {
		context.modules.isRegistered(MIXModule.TYPE) && message.isMixMessage() -> false
		context.modules.isRegistered(PubSubModule.TYPE) && message.isPubSubMessage() -> false
		else -> message.name == Message.NAME
	}

	override fun process(element: Element) {
		val msg: Message = wrap(element)
		context.eventBus.fire(MessageReceivedEvent(msg.from, msg))
	}

}

