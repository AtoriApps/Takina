
package lib.takina.core.xml

@DslMarker
annotation class TakinaElementDsl

class ElementAttributes(private val element: Element) {

	operator fun set(name: String, value: String?) {
		if (value == null) element.attributes.remove(name)
		else element.attributes[name] = value
	}

	operator fun get(name: String): String? {
		return element.attributes[name]
	}
}

@TakinaElementDsl
open class AttributesHelper(internal val element: Element) {

	infix fun String.to(target: String) {
		element.attributes[this] = target
	}

	operator fun set(name: String, value: String?) {
		if (value == null) element.attributes.remove(name)
		else element.attributes[name] = value
	}

	operator fun get(name: String): String? {
		return element.attributes[name]
	}

}

@TakinaElementDsl
open class ElementNode(internal val element: Element) {

	fun attribute(name: String, value: String) {
		element.attributes[name] = value
	}

	val attributes = ElementAttributes(element)

	fun attributes(init: (AttributesHelper.() -> Unit)) {
		val h = AttributesHelper(element)
		h.init()
	}

	var xmlns: String?
		set(value) {
			attributes["xmlns"] = value
		}
		get() = element.xmlns

	operator fun String.unaryPlus() {
		value = if (value == null) this else value + this
	}

	var value: String?
		set(value) {
			element.value = value
		}
		get() = element.value

	/**
	 * To tkjfjkshdfjk
	 */
	@Deprecated("WIll be removed")
	operator fun String.invoke(value: String): Element {
		val n = element(this)
		n.value = value
		return n
	}

	operator fun String.invoke(
		xmlns: String? = null,
		init: (ElementNode.() -> Unit)? = null,
	): Element {
		return element(this, init).apply {
			xmlns?.let {
				attributes["xmlns"] = it
			}
		}
	}

	fun addChild(e: Element) {
		e.parent = element
		element.children.add(e)
	}

	fun element(name: String, init: (ElementNode.() -> Unit)? = null): Element {
		val e = ElementNode(ElementImpl(name))
		if (init != null) e.init()
		e.element.parent = element
		element.children.add(e.element)
		return e.element
	}

}

fun element(name: String, init: ElementNode.() -> Unit): Element {
	val n = ElementNode(ElementImpl(name))
	n.init()
	return n.element
}

fun response(element: Element, init: ElementNode.() -> Unit): Element {
	val n = ElementNode(ElementImpl(element.name))
	n.element.attributes["id"] = element.attributes["id"]!!
	n.element.attributes["type"] = "result"
	if (element.attributes["to"] != null) n.element.attributes["from"] = element.attributes["to"]!!
	if (element.attributes["from"] != null) n.element.attributes["to"] = element.attributes["from"]!!
	n.init()
	return n.element
}

