
package lib.takina.core.xmpp.modules.presence

import lib.takina.core.Context
import lib.takina.core.builder.TakinaConfigDsl
import lib.takina.core.eventbus.Event
import lib.takina.core.eventbus.EventDefinition
import lib.takina.core.logger.LoggerFactory
import lib.takina.core.modules.Criterion
import lib.takina.core.modules.XmppModule
import lib.takina.core.modules.XmppModuleProvider
import lib.takina.core.requests.RequestBuilder
import lib.takina.core.xml.Element
import lib.takina.core.xmpp.BareJID
import lib.takina.core.xmpp.JID
import lib.takina.core.xmpp.getFromAttr
import lib.takina.core.xmpp.stanzas.*

/**
 * Event released when **any** presence stanza will be received.
 */
data class PresenceReceivedEvent(
	/** JID of sender. */
	val jid: JID,
	/** Type of received presence stanza. */
	val stanzaType: PresenceType?,
	/** Received stanza. */
	val stanza: Presence,
) : Event(TYPE) {

	companion object : EventDefinition<PresenceReceivedEvent> {

		override val TYPE = "lib.takina.core.xmpp.modules.presence.PresenceReceivedEvent"
	}
}

/**
 * Event released when received stanza is related to change presence status by contact.
 */
data class ContactChangeStatusEvent(
	/** JID of sender. */
	val jid: BareJID,
	/** Human-readable status set by contact. */
	val status: String?,
	/** "best" presence stanza, based on presence priority. */
	val presence: Presence,
	/** Just received presence stanza. */
	val lastReceivedPresence: Presence,
) : Event(TYPE) {

	companion object : EventDefinition<ContactChangeStatusEvent> {

		override val TYPE = "lib.takina.core.xmpp.modules.presence.ContactChangeStatusEvent"
	}
}

@TakinaConfigDsl
/**
 * Configuration of [PresenceModule].
 */
interface PresenceModuleConfig {

	/**
	 * Specify a store to keep received presence stanza.
	 *
	 */
	var store: PresenceStore

}

/**
 * Module for handling received presence information.
 */
class PresenceModule(override val context: Context) : XmppModule, PresenceModuleConfig {

	private val log = LoggerFactory.logger("lib.takina.core.xmpp.modules.presence.PresenceModule")

	override val type = TYPE
	override val criteria = Criterion.name(Presence.NAME)
	override val features: Array<String>? = null

	override var store: PresenceStore = InMemoryPresenceStore()

	companion object : XmppModuleProvider<PresenceModule, PresenceModuleConfig> {

		override val TYPE = "PresenceModule"
		override fun instance(context: Context): PresenceModule = PresenceModule(context)

		override fun configure(module: PresenceModule, cfg: PresenceModuleConfig.() -> Unit) = module.cfg()
	}

	override fun process(element: Element) {
		val presence: Presence = wrap(element)
		val fromJID = presence.getFromAttr()
		log.finer { "Presence received $presence" }
		if (fromJID == null) {
			return
		}

		if (presence.type == PresenceType.Unavailable) {
			store.removePresence(fromJID)
		} else if (presence.type == null || presence.type == PresenceType.Error) {
			store.setPresence(presence)
		}
		context.eventBus.fire(PresenceReceivedEvent(fromJID, presence.type, presence))

		if (presence.type == null || presence.type == PresenceType.Unavailable || presence.type == PresenceType.Error) {
			val bestPresence = getBestPresenceOf(fromJID.bareJID) ?: presence
			context.eventBus.fire(
				ContactChangeStatusEvent(
					fromJID.bareJID, bestPresence.status, bestPresence, presence
				)
			)
		}
	}

	/**
	 * Sends initial presence.
	 */
	fun sendInitialPresence() {
		val presence = presence { }
		context.writer.writeDirectly(presence)
	}

	/**
	 * Prepare request for send user defined presence.
	 * @param jid presence receiver.If `null`, the presence will be sent to all participants (default).
	 * @param type presence type.
	 * @param show availability state.
	 * @param status human-readable status description.
	 */
	fun sendPresence(
		jid: JID? = null, type: PresenceType? = null, show: Show? = null, status: String? = null,
	): RequestBuilder<Unit, Presence> {
		return context.request.presence {
			if (jid != null) this.to = jid
			this.type = type
			if (show != null) this.show = show
			if (status != null) {
				this.status = status
			}
		}
	}

	/**
	 * Prepares request for presence subscription manipulation. Check XMPP documentation for details.
	 * @param jid JabberID of entity
	 * @param presenceType one of [PresenceType.Subscribe], [PresenceType.Subscribed], [PresenceType.Unsubscribe], [PresenceType.Unsubscribed]
	 */
	fun sendSubscriptionSet(jid: JID, presenceType: PresenceType): RequestBuilder<Unit, Presence> {
		require(
			presenceType in listOf(
				PresenceType.Subscribe, PresenceType.Subscribed, PresenceType.Unsubscribe, PresenceType.Unsubscribed
			)
		) { "presenceType must one of: Subscribe, Subscribed, Unsubscribe, Unsubscribed" }
		return context.request.presence {
			to = jid
			type = presenceType
		}
	}

	/**
	 * Return presence with the highest priority of given JabberID.
	 * @param jid JabberID
	 */
	fun getBestPresenceOf(jid: BareJID): Presence? {
		data class Envelope(val presence: Presence) {

			val comp: String by lazy { "${(500 - presence.priority)}:${100 + presence.typeAndShow().ordinal}" }
		}

		return store.getPresences(jid).filter { presence -> presence.type == null }
			.map { presence -> Envelope(presence) }.minByOrNull { envelope -> envelope.comp }?.presence
	}

	/**
	 * Return latest presence of given JabberID.
	 * @param jid JabberID
	 */
	fun getPresenceOf(jid: JID): Presence? {
		return store.getPresence(jid)
	}

	/**
	 * Returns all known resources of given JabberID.
	 * @param jid JabberID
	 */
	fun getResources(jid: BareJID): List<JID> {
		return store.getPresences(jid).mapNotNull { p -> p.from }
	}

}