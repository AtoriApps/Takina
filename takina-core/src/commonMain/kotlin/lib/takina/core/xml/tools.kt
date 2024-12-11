
package lib.takina.core.xml

fun Element.setAtt(attName: String, value: String?) {
	if (value == null) {
		this.attributes.remove(attName)
	} else {
		this.attributes[attName] = value
	}
}

fun Element.getChildContent(childName: String, defaultValue: String? = null): String? {
	return this.getFirstChild(childName)?.value ?: defaultValue
}

fun Element.setChildContent(childName: String, value: String?) {
	var c = getFirstChild(childName)
	if (value == null && c != null) {
		this.remove(c)
	} else if (value != null && c != null) {
		c.value = value
	} else if (value != null && c == null) {
		c = ElementImpl(childName)
		c.value = value
		add(c)
	}
}
