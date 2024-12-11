
package lib.takina.core.xml

import kotlin.jvm.JvmStatic

class ElementBuilder private constructor(private val element: Element) {

	var currentElement: Element = element
		private set

	val onTop: Boolean
		get() = currentElement.parent == null

	fun child(name: String): ElementBuilder {
		val element = ElementImpl(name)
		element.parent = currentElement
		currentElement.children.add(element)
		currentElement = element
		return this
	}

	fun build(): Element = element

	fun attribute(key: String, value: String): ElementBuilder {
		currentElement.attributes[key] = value
		return this
	}

	fun attributes(attributes: Map<String, String>): ElementBuilder {
		currentElement.attributes.putAll(attributes)
		return this
	}

	fun value(value: String): ElementBuilder {
		currentElement.value = value
		return this
	}

	fun xmlns(xmlns: String): ElementBuilder {
		currentElement.attributes["xmlns"] = xmlns
		return this
	}

	fun up(): ElementBuilder {
		currentElement = currentElement.parent!!
		return this
	}

	companion object {

		@JvmStatic
		fun create(name: String): ElementBuilder {
			return ElementBuilder(ElementImpl(name))
		}

		@JvmStatic
		fun create(name: String, xmlns: String): ElementBuilder {
			val element = ElementImpl(name)
			element.attributes["xmlns"] = xmlns
			return ElementBuilder(element)
		}
	}

}