package org.atoriapps.takina.core.xml

@DslMarker
annotation class XmlDsl

@XmlDsl
class XmlElementBuilder internal constructor(
    private val name: String,
) {
    private val attributes = linkedMapOf<String, String>()
    private val children = mutableListOf<XmlNode>()
    private var selfClosing = false

    fun attr(name: String, value: String?) {
        if (value != null) attributes[name] = value
    }

    fun text(value: String) {
        children += XmlText(value)
    }

    fun element(name: String, init: XmlElementBuilder.() -> Unit = {}) {
        children += xml(name, init)
    }

    fun node(node: XmlNode) {
        children += node
    }

    fun selfClosing() {
        selfClosing = true
    }

    internal fun build(): XmlElement = XmlElement(
        name = name,
        attributes = attributes.toMap(),
        children = children.toList(),
        selfClosing = selfClosing,
    )
}

fun xml(name: String, init: XmlElementBuilder.() -> Unit = {}): XmlElement {
    val builder = XmlElementBuilder(name)
    builder.init()
    return builder.build()
}

