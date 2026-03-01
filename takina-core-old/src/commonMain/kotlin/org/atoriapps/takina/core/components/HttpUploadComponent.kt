package org.atoriapps.takina.core.components

import org.atoriapps.takina.core.AbstractTakina
import org.atoriapps.takina.core.TakinaContext
import org.atoriapps.takina.core.requests.PendingIqAwaitRequest
import org.atoriapps.takina.core.utils.IdUtils
import org.atoriapps.takina.core.xml.XmlElement
import org.atoriapps.takina.core.xml.XmlRegexUtils
import org.atoriapps.takina.core.xmpp.Jid
import org.atoriapps.takina.core.xmpp.stanzas.IqStanza
import org.atoriapps.takina.core.xmpp.stanzas.IqType

class HttpUploadComponent internal constructor(private val takina: AbstractTakina) : TakinaComponent {
    companion object : TakinaComponentProvider<HttpUploadComponent> {
        const val NAMESPACE: String = "urn:xmpp:http:upload:0"

        override fun getInstance(context: TakinaContext): HttpUploadComponent {
            val core = context as? AbstractTakina ?: error("HttpUploadComponent 只能安装在 Takina 核心上下文中")
            return HttpUploadComponent(core)
        }

        override fun getComponentType() = HttpUploadComponent::class
    }

    var allowInsecureHttpSlotUrls: Boolean = false

    fun requestSlotAwait(
        filename: String,
        size: Long,
        contentType: String? = null,
        from: Jid? = null,
        to: Jid? = null,
        timeoutMillis: Long = 10_000,
    ): PendingIqAwaitRequest {
        require(filename.isNotBlank()) { "filename 不能为空" }
        require(size > 0) { "size 必须大于 0" }

        val attrs = linkedMapOf("filename" to filename, "size" to size.toString())
        if (!contentType.isNullOrBlank()) attrs["content-type"] = contentType

        val payload = XmlElement(name = "request", namespace = NAMESPACE, attributes = attrs)
        return PendingIqAwaitRequest(
            takina = takina,
            timeoutMillis = timeoutMillis,
            stanza = IqStanza(
                id = IdUtils.newStanzaId("upload-slot"),
                from = from,
                to = to,
                type = IqType.GET,
                payload = payload,
            ),
        )
    }

    fun parseSlotResult(iqResultXml: String): Slot? {
        val slotTag = SLOT_TAG_REGEX.find(iqResultXml) ?: return null
        val slotAttrs = XmlRegexUtils.parseAttributes(slotTag.groupValues[1])
        if (XmlRegexUtils.extractNamespace(slotAttrs) != NAMESPACE) return null

        val putTag = PUT_TAG_REGEX.find(iqResultXml) ?: return null
        val getTag = GET_TAG_REGEX.find(iqResultXml) ?: return null
        val putAttrs = XmlRegexUtils.parseAttributes(putTag.groupValues[1])
        val getAttrs = XmlRegexUtils.parseAttributes(getTag.groupValues[1])
        val putUrl = putAttrs["url"] ?: return null
        val getUrl = getAttrs["url"] ?: return null
        if (!allowInsecureHttpSlotUrls && (!putUrl.startsWith("https://", ignoreCase = true) || !getUrl.startsWith("https://", ignoreCase = true))) return null

        val headers = HEADER_TAG_REGEX.findAll(iqResultXml).mapNotNull { match ->
            val attrs = XmlRegexUtils.parseAttributes(match.groupValues[1])
            val name = attrs["name"] ?: return@mapNotNull null
            if (!ALLOWED_HEADER_NAMES.any { allowed -> allowed.equals(name, ignoreCase = true) }) return@mapNotNull null
            name to match.groupValues[2]
        }.toList()

        return Slot(
            putUrl = putUrl,
            getUrl = getUrl,
            putHeaders = headers.toMap(),
            putHeadersOrdered = headers,
        )
    }

    fun parseUploadError(iqErrorXml: String): UploadError? {
        val errorTag = ERROR_TAG_REGEX.find(iqErrorXml) ?: return null
        val errorAttrs = XmlRegexUtils.parseAttributes(errorTag.groupValues[1])
        val text = ERROR_TEXT_TAG_REGEX.find(iqErrorXml)?.groupValues?.get(1)
        val condition = STANZA_ERROR_CONDITION_TAG_REGEX.find(iqErrorXml)?.groupValues?.get(1)
        val maxFileSize = MAX_FILE_SIZE_TAG_REGEX.find(iqErrorXml)?.groupValues?.get(1)?.trim()?.toLongOrNull()
        val isRetryable = condition != null && RETRYABLE_CONDITIONS.contains(condition)
        return UploadError(type = errorAttrs["type"], condition = condition, text = text, maxFileSize = maxFileSize, retryable = isRetryable)
    }

    data class Slot(
        val putUrl: String,
        val getUrl: String,
        val putHeaders: Map<String, String>,
        val putHeadersOrdered: List<Pair<String, String>>,
    )

    data class UploadError(
        val type: String?,
        val condition: String?,
        val text: String?,
        val maxFileSize: Long?,
        val retryable: Boolean,
    )
}

val TakinaContext.httpUpload: HttpUploadComponent get() = requireComponent(HttpUploadComponent)

private val SLOT_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?slot\b([^>]*)>""")
private val PUT_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?put\b([^>]*)>""")
private val GET_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?get\b([^>]*)/?>""")
private val HEADER_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?header\b([^>]*)>(.*?)</\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?header\s*>""")
private val ERROR_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?error\b([^>]*)>""")
private val ERROR_TEXT_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?text\b[^>]*>(.*?)</\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?text\s*>""")
private val STANZA_ERROR_CONDITION_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?(bad-request|conflict|feature-not-implemented|forbidden|gone|internal-server-error|item-not-found|jid-malformed|not-acceptable|not-allowed|not-authorized|policy-violation|recipient-unavailable|redirect|registration-required|remote-server-not-found|remote-server-timeout|resource-constraint|service-unavailable|subscription-required|undefined-condition|unexpected-request)\b[^>]*/?>""")
private val MAX_FILE_SIZE_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?max-file-size\b[^>]*>(.*?)</\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?max-file-size\s*>""")
private val ALLOWED_HEADER_NAMES = setOf("Authorization", "Cookie", "Expires")
private val RETRYABLE_CONDITIONS = setOf("internal-server-error", "resource-constraint", "service-unavailable", "remote-server-timeout")
