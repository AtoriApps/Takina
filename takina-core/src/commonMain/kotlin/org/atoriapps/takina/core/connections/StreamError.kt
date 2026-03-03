package org.atoriapps.takina.core.connections

import org.atoriapps.takina.core.xml.XmlParser

internal enum class XmppStreamErrorCondition(
    val wireName: String,
    val authHardFailure: Boolean = false,
) {
    BAD_FORMAT("bad-format"),
    BAD_NAMESPACE_PREFIX("bad-namespace-prefix"),
    CONFLICT("conflict"),
    CONNECTION_TIMEOUT("connection-timeout"),
    HOST_GONE("host-gone"),
    HOST_UNKNOWN("host-unknown"),
    IMPROPER_ADDRESSING("improper-addressing"),
    INTERNAL_SERVER_ERROR("internal-server-error"),
    INVALID_FROM("invalid-from"),
    INVALID_NAMESPACE("invalid-namespace"),
    INVALID_XML("invalid-xml"),
    NOT_AUTHORIZED("not-authorized", authHardFailure = true),
    NOT_WELL_FORMED("not-well-formed"),
    POLICY_VIOLATION("policy-violation", authHardFailure = true),
    REMOTE_CONNECTION_FAILED("remote-connection-failed"),
    RESET("reset"),
    RESOURCE_CONSTRAINT("resource-constraint"),
    RESTRICTED_XML("restricted-xml"),
    SEE_OTHER_HOST("see-other-host"),
    SYSTEM_SHUTDOWN("system-shutdown"),
    UNDEFINED_CONDITION("undefined-condition"),
    UNSUPPORTED_ENCODING("unsupported-encoding"),
    UNSUPPORTED_FEATURE("unsupported-feature"),
    UNSUPPORTED_STANZA_TYPE("unsupported-stanza-type"),
    UNSUPPORTED_VERSION("unsupported-version");

    companion object {
        private val byWireName = entries.associateBy { it.wireName }

        fun fromLocalNameOrNull(localName: String): XmppStreamErrorCondition? = byWireName[localName]
    }
}

internal data class XmppStreamError(
    val condition: XmppStreamErrorCondition,
    val text: String? = null,
)

internal fun parseXmppStreamError(frame: String): XmppStreamError? {
    val root = XmlParser.parseElementOrNull(frame) ?: return null
    if (root.localName != "error") return null

    val condition = root.childElements().firstNotNullOfOrNull { child -> XmppStreamErrorCondition.fromLocalNameOrNull(child.localName) } ?: return null

    val text = root.firstChildElement("text")?.textContent()?.takeIf { it.isNotBlank() }
    return XmppStreamError(condition = condition, text = text)
}
