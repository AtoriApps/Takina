
package lib.takina.core.xmpp.modules.chatstates

import lib.takina.core.Context
import lib.takina.core.builder.TakinaConfigDsl
import lib.takina.core.eventbus.Event
import lib.takina.core.eventbus.EventDefinition
import lib.takina.core.modules.Criteria
import lib.takina.core.modules.ModulesManager
import lib.takina.core.modules.XmppModule
import lib.takina.core.modules.XmppModuleProvider
import lib.takina.core.requests.RequestBuilder
import lib.takina.core.xml.Element
import lib.takina.core.xml.element
import lib.takina.core.xmpp.BareJID
import lib.takina.core.xmpp.ErrorCondition
import lib.takina.core.xmpp.JID
import lib.takina.core.xmpp.XMPPException
import lib.takina.core.xmpp.modules.MessageReceivedEvent
import lib.takina.core.xmpp.stanzas.Message
import lib.takina.core.xmpp.stanzas.message

enum class ChatState(val xmppValue: String) {

	/**
	 * User is actively participating in the chat session.
	 */
	Active("active"),

	/**
	 * User has not been actively participating in the chat session.
	 */
	Inactive("inactive"),

	/**
	 * User has effectively ended their participation in the chat session.
	 */
	Gone("gone"),

	/**
	 * User is composing a message.
	 */
	Composing("composing"),

	/**
	 * User had been composing but now has stopped.
	 */
	Paused("paused"),
}

private fun findChatState(element: Element): ChatState? {
	val csi = element.children.find {
		it.xmlns == ChatStateModule.XMLNS
	} ?: return null
	return ChatState.values().find { chatState -> chatState.xmppValue == csi.name } ?: throw XMPPException(
		ErrorCondition.BadRequest, "Unknown chat state ${csi.name}"
	)
}

private fun setChatState(element: Element, state: ChatState?) {
	element.children.find { it.xmlns == ChatStateModule.XMLNS }?.let { element.remove(it) }
	state?.let {
		element.add(element(state.xmppValue) {
			xmlns = ChatStateModule.XMLNS
		})
	}
}

var Message.chatState: ChatState?
	get() = findChatState(this)
	set(value) = setChatState(this, value)

data class ChatStateEvent(val jid: JID, val state: ChatState) : Event(TYPE) {

	companion object : EventDefinition<ChatStateEvent> {

		override val TYPE = "lib.takina.core.xmpp.modules.chatstates.ChatStateEvent"
	}
}

@TakinaConfigDsl
interface ChatStateModuleConfig

/**
 * Module is implementing Chat State Notifications ([XEP-0085](https://xmpp.org/extensions/xep-0085.html)).
 *
 */
class ChatStateModule(override val context: Context) : XmppModule, ChatStateModuleConfig {
//	private val log LoggerFactory.logger("lib.takina.core.xmpp.modules.chatstates.ChatStateModule")

	override val type = TYPE
	override val criteria: Criteria? = null
	override val features: Array<String> = arrayOf(XMLNS)

	companion object : XmppModuleProvider<ChatStateModule, ChatStateModuleConfig> {

		const val XMLNS = "http://jabber.org/protocol/chatstates"
		override val TYPE = XMLNS

		override fun instance(context: Context): ChatStateModule = ChatStateModule(context)

		override fun configure(module: ChatStateModule, cfg: ChatStateModuleConfig.() -> Unit) = module.cfg()

		override fun doAfterRegistration(module: ChatStateModule, moduleManager: ModulesManager) = module.initialize()

	}

	private fun initialize() {
		context.eventBus.register(MessageReceivedEvent) { event ->
			findChatState(event.stanza)?.let { state ->
				if (event.fromJID != null) context.eventBus.fire(ChatStateEvent(event.fromJID, state))
			}
		}
		context.eventBus.register(OwnChatStateChangeEvent) { event ->
			if (event.sendUpdate) {
				sendChatState(event.jid, event.state).send()
			}
		}
	}

	/**
	 * Sends Chat State request.
	 *
	 * @param jid recipient of chat state
	 * @param state Chat State to publish.
	 */
	@Deprecated("Will be removed soon.", replaceWith = ReplaceWith("sendChatState(jid, state).send()"))
	fun publishChatState(jid: BareJID, state: ChatState) = sendChatState(jid, state).send()

	/**
	 * Prepares Chat State request.
	 *
	 * @param jid recipient of chat state
	 * @param state Chat State to publish.
	 */
	fun sendChatState(jid: BareJID, state: ChatState): RequestBuilder<Unit, Message> {
		val msg = message {
			to = jid
			state.xmppValue {
				xmlns = XMLNS
			}
			"no-store" {
				xmlns = "urn:xmpp:hints"
			}
		}
		return context.request.message(msg)
	}

	override fun process(element: Element) = throw XMPPException(ErrorCondition.FeatureNotImplemented)

}