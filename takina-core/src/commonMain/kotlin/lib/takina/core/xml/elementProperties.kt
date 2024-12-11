
package lib.takina.core.xml

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

abstract class ElementProperty<V>(private val elementName: String? = null) : ReadWriteProperty<Element, V> {

	override fun getValue(thisRef: Element, property: KProperty<*>): V {
		val str = thisRef.getFirstChild(elementName ?: property.name)?.value
		return stringToValue(str)
	}

	override fun setValue(thisRef: Element, property: KProperty<*>, value: V) {
		val strValue = valueToString(value)
		val childName = elementName ?: property.name
		var c = thisRef.getFirstChild(childName)
		if (value == null && c != null) {
			thisRef.remove(c)
		} else if (value != null && c != null) {
			c.value = strValue
		} else if (value != null && c == null) {
			c = ElementImpl(childName)
			c.value = strValue
			thisRef.add(c)
		}
	}

	abstract fun valueToString(value: V): String?
	abstract fun stringToValue(value: String?): V
}

inline fun <V> elementProperty(
	elementName: String? = null, crossinline valueToString: (V) -> String?, crossinline stringToValue: (String?) -> V,
): ReadWriteProperty<Element, V> {
	return object : ElementProperty<V>(elementName) {
		override fun valueToString(value: V): String? = valueToString.invoke(value)

		override fun stringToValue(value: String?): V = stringToValue.invoke(value)
	}
}

fun intElementProperty(elementName: String? = null): ReadWriteProperty<Element, Int?> =
	elementProperty(elementName = elementName,
					valueToString = { v -> v?.toString() },
					stringToValue = { s -> s?.toInt() })

fun intWithDefaultElementProperty(
	elementName: String? = null, defaultValue: Int,
): ReadWriteProperty<Element, Int> = elementProperty(elementName = elementName,
													 valueToString = { v -> v.toString() },
													 stringToValue = { s -> s?.toInt() ?: defaultValue })

fun stringElementProperty(elementName: String? = null): ReadWriteProperty<Element, String?> =
	elementProperty(elementName = elementName, valueToString = { v -> v }, stringToValue = { s -> s })

