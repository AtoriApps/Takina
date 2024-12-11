
package lib.takina.core.xml

import lib.takina.core.xmpp.JID
import lib.takina.core.xmpp.toJID
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

abstract class AttributeProperty<V>(private val attributeName: String? = null) : ReadWriteProperty<Element, V> {

	override fun getValue(thisRef: Element, property: KProperty<*>): V {
		val str = thisRef.attributes[attributeName ?: property.name]
		return stringToValue(str)
	}

	override fun setValue(thisRef: Element, property: KProperty<*>, value: V) {
		val str = valueToString(value)
		if (str == null) thisRef.attributes.remove(attributeName ?: property.name)
		else thisRef.attributes[attributeName ?: property.name] = str
	}

	abstract fun valueToString(value: V): String?
	abstract fun stringToValue(value: String?): V
}

inline fun <V> attributeProp(
	attributeName: String? = null, crossinline valueToString: (V) -> String?, crossinline stringToValue: (String?) -> V,
): ReadWriteProperty<Element, V> {
	return object : AttributeProperty<V>(attributeName) {
		override fun valueToString(value: V): String? = valueToString.invoke(value)

		override fun stringToValue(value: String?): V = stringToValue.invoke(value)

	}
}

fun intAttributeProperty(attributeName: String? = null): ReadWriteProperty<Element, Int?> =
	attributeProp(attributeName = attributeName,
				  valueToString = { v -> v?.toString() },
				  stringToValue = { s -> s?.toInt() })

fun stringAttributeProperty(attributeName: String? = null): ReadWriteProperty<Element, String?> =
	attributeProp(attributeName = attributeName, valueToString = { v -> v }, stringToValue = { s -> s })

fun jidAttributeProperty(attributeName: String? = null): ReadWriteProperty<Element, JID?> =
	attributeProp(attributeName = attributeName,
				  valueToString = { v -> v?.toString() },
				  stringToValue = { v: String? -> v?.toJID() })
