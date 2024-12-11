
package lib.takina.core.xmpp.modules.vcard

import lib.takina.core.Context
import lib.takina.core.builder.TakinaConfigDsl
import lib.takina.core.eventbus.Event
import lib.takina.core.eventbus.EventDefinition
import lib.takina.core.modules.Criteria
import lib.takina.core.modules.XmppModule
import lib.takina.core.modules.XmppModuleProvider
import lib.takina.core.requests.RequestBuilder
import lib.takina.core.xml.Element
import lib.takina.core.xmpp.*
import lib.takina.core.xmpp.modules.pubsub.PubSubItemEvent
import lib.takina.core.xmpp.stanzas.IQ
import lib.takina.core.xmpp.stanzas.IQType
import lib.takina.core.xmpp.stanzas.iq

data class VCardUpdatedEvent(val jid: BareJID, val vcard: VCard?) : Event(TYPE) {

	companion object : EventDefinition<VCardUpdatedEvent> {

		override val TYPE = "lib.takina.core.xmpp.modules.vcard.VCardUpdatedEvent"
	}
}

@TakinaConfigDsl
interface VCardModuleConfig

class VCardModule(override val context: Context) : XmppModule, VCardModuleConfig {

	companion object : XmppModuleProvider<VCardModule, VCardModuleConfig> {

		const val XMLNS = "urn:ietf:params:xml:ns:vcard-4.0"
		const val NODE = "urn:xmpp:vcard4"
		override val TYPE = XMLNS

		override fun instance(context: Context): VCardModule = VCardModule(context)

		override fun configure(module: VCardModule, cfg: VCardModuleConfig.() -> Unit) {
			module.cfg()
			module.initialize()
		}

	}

	override val criteria: Criteria? = null
	override val features: Array<String> = arrayOf(XMLNS, "$NODE+notify")
	override val type = TYPE

	/**
	 * If `true`, vCard will be retrieved after receiving information about vCard update.
	 */
	var autoRetrieve: Boolean = false

	private fun initialize() {
		context.eventBus.register(PubSubItemEvent, this@VCardModule::processEvent)
	}

	override fun process(element: Element) = throw XMPPException(ErrorCondition.FeatureNotImplemented)

	/**
	 * Prepares request to retrieve vcard.
	 * @param jid address of entity what VCard have to be retrieved.
	 * @return builder of request returning [VCard] as result.
	 */
	fun retrieveVCard(jid: BareJID): RequestBuilder<VCard, IQ> {
		val iq = iq {
			type = IQType.Get
			to = jid
			"vcard" {
				xmlns = XMLNS
			}
		}
		return context.request.iq(iq).map(this@VCardModule::parseResponse)
	}

	/**
	 * Prepares request to publish own VCard.
	 *
	 * @param vcard VCard to be published.
	 */
	fun publish(vcard: VCard): RequestBuilder<Unit, IQ> {
		val ownJid = context.boundJID?.bareJID
		val iq = iq {
			type = IQType.Set
			ownJid?.let {
				to = it.toString().toJID()
			}
			addChild(vcard.element)
		}
		return context.request.iq(iq).map {}
	}

	private fun processEvent(event: PubSubItemEvent) {
		if (event !is PubSubItemEvent.Published || event.nodeName != NODE) return
		val jid = event.pubSubJID ?: return

		if (autoRetrieve) {
			retrieveVCard(jid.bareJID).response {
				if (it.isSuccess) {
					context.eventBus.fire(VCardUpdatedEvent(jid.bareJID, it.getOrNull()))
				}
			}.send()
		} else {
			context.eventBus.fire(VCardUpdatedEvent(jid.bareJID, null))
		}
	}

	private fun parseResponse(iq: Element): VCard {
		val vCard = iq.getChildrenNS("vcard", XMLNS) ?: throw XMPPException(ErrorCondition.BadRequest)
		return VCard(vCard)
	}

}