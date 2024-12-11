
package lib.takina.core.xmpp.stanzas

import lib.takina.core.exceptions.TakinaException
import lib.takina.core.xml.*
import lib.takina.core.xmpp.JID
import lib.takina.core.xmpp.nextUID
import lib.takina.core.xmpp.toJID

@TakinaElementDsl
abstract class StanzaNode<STANZA_TYPE>(element: Element) : ElementNode(element) {

	private fun getJID(attName: String): JID? {
		val att = element.attributes[attName]
		return if (att == null) null else att.toJID()
	}

	protected open fun setAtt(attName: String, value: String?) {
		if (value == null) {
			element.attributes.remove(attName)
		} else {
			element.attributes[attName] = value
		}
	}

	fun id(value: String? = null) {
		if (value != null) {
			element.attributes["id"] = value
		} else if (!element.attributes.containsKey("id")) {
			element.attributes["id"] = nextUID()
		}
	}

	var to: JID?
		get() = getJID("to")
		set(value) = setAtt("to", value?.toString())

	var from: JID?
		get() = getJID("from")
		set(value) = setAtt("from", value?.toString())

	abstract var type: STANZA_TYPE

}

@TakinaElementDsl
class PresenceNode(private val presence: Presence) : StanzaNode<PresenceType?>(presence) {

	override var type: PresenceType?
		set(value) = setAtt("type", value?.value)
		get() = PresenceType.values().firstOrNull { te -> te.value == value }

	private fun intSetShow(show: Show?) {
		presence.show = show
	}

	private fun intSetPriority(value: Int) {
		presence.priority = value
	}

	private fun intSetStatus(value: String?) {
		presence.status = value
	}

	var show: Show?
		set(value) = intSetShow(value)
		get() = presence.show

	var priority: Int
		set(value) = intSetPriority(value)
		get() = presence.priority

	var status: String?
		set(value) = intSetStatus(value)
		get() = presence.status

}

@TakinaElementDsl
class IQNode(element: IQ) : StanzaNode<IQType>(element) {

	override var type: IQType
		set(value) = setAtt("type", value.value)
		get() = IQType.values().first { te -> te.value == value }

	fun query(xmlns: String, init: (ElementNode.() -> Unit)): Element {
		val e = element("query", init)
		e.attributes["xmlns"] = xmlns
		return e
//		val element = ElementImpl("query")
//		element.attributes["xmlns"] = xmlns
//		val e = ElementNode(element)
//		if (init != null) e.init()
//		e.element.parent = element
//		element.children.add(e.element)
//		return e.element
	}

}

@TakinaElementDsl
class MessageNode(element: Message) : StanzaNode<MessageType?>(element) {

	override var type: MessageType?
		set(value) = setAtt("type", value?.value)
		get() = MessageType.values().firstOrNull { te -> te.value == value }

	var body: String?
		set(value) = element.setChildContent("body", value)
		get() = element.getChildContent("body")
}

@Suppress("UNCHECKED_CAST")
fun <ST : Stanza<*>> wrap(element: Element): ST {
	return if (element is Stanza<*>) element as ST
	else when (element.name) {
		Presence.NAME -> Presence(element) as ST
		IQ.NAME -> IQ(element) as ST
		Message.NAME -> Message(element) as ST
		else -> throw TakinaException("Unknown stanza type '${element.name}'.")
	}
}

fun <ST : Stanza<*>> Element.asStanza(): ST = wrap(this)

fun presence(init: PresenceNode.() -> Unit): Presence {
	val n = PresenceNode(Presence(ElementImpl(Presence.NAME)))
	n.init()
	n.id()
	return n.element as Presence
}

fun message(init: MessageNode.() -> Unit): Message {
	val n = MessageNode(Message(ElementImpl(Message.NAME)))
	n.init()
	n.id()
	return n.element as Message
}

fun iq(init: IQNode.() -> Unit): IQ {
	val n = IQNode(IQ(ElementImpl(IQ.NAME)))
	n.init()
	n.id()
	return n.element as IQ
}