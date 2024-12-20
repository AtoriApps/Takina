
package lib.takina.core.xmpp.modules.receipts

import lib.takina.core.Context
import lib.takina.core.builder.TakinaConfigDsl
import lib.takina.core.eventbus.Event
import lib.takina.core.eventbus.EventDefinition
import lib.takina.core.modules.*
import lib.takina.core.requests.RequestBuilder
import lib.takina.core.xml.Element
import lib.takina.core.xml.element
import lib.takina.core.xmpp.ErrorCondition
import lib.takina.core.xmpp.JID
import lib.takina.core.xmpp.XMPPException
import lib.takina.core.xmpp.stanzas.Message
import lib.takina.core.xmpp.stanzas.MessageNode
import lib.takina.core.xmpp.stanzas.MessageType
import lib.takina.core.xmpp.stanzas.message
import lib.takina.core.xmpp.toJID

/**
 * Event fired when a delivery receipt was received.
 * @param jid JabberID of sender
 * @param msgId identifier of the message to which the received delivery receipt refers.
 */
data class MessageDeliveryReceiptEvent(val jid: JID, val msgId: String) : Event(TYPE) {

    companion object : EventDefinition<MessageDeliveryReceiptEvent> {

        override val TYPE = "lib.takina.core.xmpp.modules.receipts.MessageDeliveryReceiptEvent"
    }
}

@TakinaConfigDsl
interface DeliveryReceiptsModuleConfig {

    enum class Mode {
        /**
         * Add request to all outgoing messages.
         */
        All,

        /**
         * Do not add request.
         */
        Off
    }

    /**
     * If ``true`` then library will automatically (and immediately) send delivery receipt when message will
     * be received by client.
     */
    var autoSendReceived: Boolean

    /**
     * Define, how module should add delivery receipt request to outgoing messages.
     */
    var mode: Mode

}

/**
 * Module is implementing Message Delivery Receipts ([XEP-0184](https://xmpp.org/extensions/xep-0184.html)).
 *
 */
class DeliveryReceiptsModule(override val context: Context) : XmppModule, StanzaInterceptor,
    DeliveryReceiptsModuleConfig {

    /**
     * Module is implementing Message Delivery Receipts ([XEP-0184](https://xmpp.org/extensions/xep-0184.html)).
     *
     */
    companion object : XmppModuleProvider<DeliveryReceiptsModule, DeliveryReceiptsModuleConfig> {

        const val XMLNS = "urn:xmpp:receipts"
        override val TYPE = XMLNS

        override fun instance(context: Context): DeliveryReceiptsModule = DeliveryReceiptsModule(context)

        override fun configure(module: DeliveryReceiptsModule, cfg: DeliveryReceiptsModuleConfig.() -> Unit) =
            module.cfg()

        override fun doAfterRegistration(module: DeliveryReceiptsModule, moduleManager: ModulesManager) =
            moduleManager.registerInterceptors(arrayOf(module))

    }

    override val criteria: Criteria? = null
    override val features: Array<String> = arrayOf(XMLNS)
    override val type = TYPE

    override var autoSendReceived = false
    override var mode: DeliveryReceiptsModuleConfig.Mode = DeliveryReceiptsModuleConfig.Mode.All

    override fun process(element: Element) = throw XMPPException(ErrorCondition.FeatureNotImplemented)

    override fun afterReceive(element: Element): Element {
        if (element.name != Message.NAME) return element
        if (element.attributes["type"] == MessageType.Error.value) return element
        val from = element.attributes["from"]?.toJID() ?: return element

        element.getReceiptReceivedID()?.let { id -> context.eventBus.fire(MessageDeliveryReceiptEvent(from, id)) }

        if (autoSendReceived) element.getChildrenNS("request", XMLNS)?.let {
            element.attributes["id"]?.let { id ->
                val resp = message {
                    element.attributes["from"]?.let {
                        attribute("to", it)
                    }
                    element.attributes["type"]?.let {
                        attribute("type", it)
                    }
                    "received" {
                        xmlns = XMLNS
                        attribute("id", id)
                    }
                }
                context.writer.writeDirectly(resp)
            }
        }
        return element
    }

    fun received(jid: JID, originId: String): RequestBuilder<Unit, Message> = context.request.message(message {
        to = jid
        "received" {
            xmlns = XMLNS
            attribute("id", originId)
        }
    }, true)

    override fun beforeSend(element: Element): Element {
        if (element.name != Message.NAME) return element
        if (element.attributes["type"] == MessageType.Groupchat.value) return element
        if (element.attributes["id"] == null) return element
        if (element.getChildrenNS("request", XMLNS) != null) return element
        if (element.getChildrenNS("received", XMLNS) != null) return element
        if (element.getFirstChild("body") == null) return element

        if (mode == DeliveryReceiptsModuleConfig.Mode.All) element.add(element("request") {
            xmlns = XMLNS
        })
        return element
    }
}

/**
 * Add delivery receipt request to stanza.
 */
fun MessageNode.deliveryReceiptRequest() = this.element.add(lib.takina.core.xml.element("request") {
    xmlns = DeliveryReceiptsModule.XMLNS
})

/**
 * Checks if received stanza contains request for delivery receipt.
 */
fun Element?.isDeliveryReceiptRequested(): Boolean =
    this != null && this.getChildrenNS("request", DeliveryReceiptsModule.XMLNS) != null

/**
 * Returns the identifier of the message to which the delivery receipt refers, or `null` if element doesn't contain a delivery receipt.
 */
fun Element.getReceiptReceivedID(): String? {
    return this.getChildrenNS("received", DeliveryReceiptsModule.XMLNS)?.let { it.attributes["id"] }
}