package org.atoriapps.takina.core.xml

sealed interface XmlNode

data class XmlText(
    val text: String,
) : XmlNode

data class XmlElement(
    val name: String,
    val attributes: Map<String, String> = emptyMap(),
    val children: List<XmlNode> = emptyList(),
    val selfClosing: Boolean = false,
) : XmlNode {
    val localName: String get() = name.substringAfter(':')

    fun attribute(name: String): String? = attributes[name]

    fun childElements(localName: String? = null): List<XmlElement> = children.mapNotNull { it as? XmlElement }.filter {
        localName == null || it.localName == localName
    }

    fun firstChildElement(localName: String): XmlElement? = childElements(localName).firstOrNull()

    fun firstDescendant(localName: String): XmlElement? {
        for (child in childElements()) {
            if (child.localName == localName) return child
            val nested = child.firstDescendant(localName)
            if (nested != null) return nested
        }
        return null
    }

    fun descendants(localName: String): List<XmlElement> = buildList {
        for (child in childElements()) {
            if (child.localName == localName) add(child)
            addAll(child.descendants(localName))
        }
    }

    fun textContent(): String = buildString {
        for (child in children) {
            when (child) {
                is XmlText -> append(child.text)
                is XmlElement -> append(child.textContent())
            }
        }
    }
}

