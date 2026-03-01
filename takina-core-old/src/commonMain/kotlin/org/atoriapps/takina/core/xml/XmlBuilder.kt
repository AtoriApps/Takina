package org.atoriapps.takina.core.xml

class XmlElementBuilder internal constructor(
    private val name: String,
    private val namespace: String?,
) {
    private val attributes = linkedMapOf<String, String>()
    private val children = mutableListOf<XmlElement>()
    private var text: String? = null

    fun attr(key: String, value: String) {
        attributes[key] = value
    }

    fun text(value: String) {
        text = value
    }

    fun child(name: String, namespace: String? = null, init: XmlElementBuilder.() -> Unit = {}) {
        children += element(name, namespace, init)
    }

    fun build(): XmlElement = XmlElement(
        name = name,
        namespace = namespace,
        attributes = attributes,
        children = children,
        text = text,
    )
}

fun element(name: String, namespace: String? = null, init: XmlElementBuilder.() -> Unit = {}): XmlElement {
    val builder = XmlElementBuilder(name, namespace)
    builder.init()
    return builder.build()
}

// 共享逻辑提取
fun xDataField(varName: String, values: List<String>, fieldType: String? = null): XmlElement {
    val attrs = linkedMapOf("var" to varName)
    if (!fieldType.isNullOrBlank()) attrs["type"] = fieldType
    return XmlElement(name = "field", attributes = attrs, children = values.map { XmlElement(name = "value", text = it) })
}