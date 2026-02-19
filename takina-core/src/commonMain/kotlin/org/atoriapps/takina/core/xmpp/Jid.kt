package org.atoriapps.takina.core.xmpp

import org.atoriapps.takina.core.exceptions.JidFormatException

sealed interface Jid {
    val userName: String?
    val domain: String
    val resource: String?
}

val Jid.bareJid: BareJid
    get() = when (this) {
        is BareJid -> this
        is FullJid -> bareJid
    }

val Jid.bareJID: BareJid
    get() = bareJid

fun Jid.copy(): Jid = createJid(userName = userName, domain = domain, resource = resource)
fun Jid.copy(resource: String?): Jid = createJid(userName = userName, domain = domain, resource = resource)

fun String.toJid(): Jid = parseJid(this).toJid()
fun String.toFullJid(): FullJid = parseJid(this).toFullJid()
fun String.toBareJid(): BareJid = parseJid(this).toBareJid()
fun String.toBareJID(): BareJid = toBareJid()

fun createJid(userName: String? = null, domain: String, resource: String? = null): Jid {
    val parsed = ParsedJid(
        userName = normalizeLocalPart(userName),
        domain = normalizeDomain(domain),
        resource = normalizeResource(resource),
    )
    return parsed.toJid()
}

fun createBareJid(userName: String? = null, domain: String): BareJid =
    createJid(userName = userName, domain = domain, resource = null) as BareJid

fun createFullJid(userName: String? = null, domain: String, resource: String): FullJid =
    createJid(userName = userName, domain = domain, resource = resource) as FullJid

private data class ParsedJid(
    val userName: String?,
    val domain: String,
    val resource: String?,
) {
    fun toJid(): Jid = if (resource == null) BareJid(userName, domain) else FullJid(BareJid(userName, domain), resource)
    fun toFullJid(): FullJid = resource?.let { FullJid(BareJid(userName, domain), it) }
        ?: throw JidFormatException("full JID requires a resource")
    fun toBareJid(): BareJid = BareJid(userName, domain)
}

private fun parseJid(raw: String): ParsedJid {
    val jid = raw.trim()
    if (jid.isEmpty()) throw JidFormatException("JID cannot be blank")

    val slashIndex = jid.indexOf('/')
    val barePart = if (slashIndex == -1) jid else jid.substring(0, slashIndex)
    val resourcePart = if (slashIndex == -1) null else jid.substring(slashIndex + 1)

    val atIndex = barePart.indexOf('@')
    val userName = if (atIndex == -1) null else barePart.substring(0, atIndex)
    val domain = if (atIndex == -1) barePart else barePart.substring(atIndex + 1)

    return ParsedJid(
        userName = normalizeLocalPart(userName),
        domain = normalizeDomain(domain),
        resource = normalizeResource(resourcePart),
    )
}

private fun normalizeLocalPart(value: String?): String? {
    if (value == null) return null
    if (value.isBlank()) throw JidFormatException("localpart cannot be blank")
    if (value.any { it.isWhitespace() || it.isControlCharacter() }) {
        throw JidFormatException("localpart contains invalid whitespace/control characters")
    }
    return value
}

private fun normalizeDomain(value: String): String {
    val normalized = value.trim().lowercase()
    if (normalized.isEmpty()) throw JidFormatException("domain cannot be blank")
    if (normalized.startsWith('.') || normalized.endsWith('.')) {
        throw JidFormatException("domain cannot start/end with '.'")
    }
    if (normalized.any { it.isWhitespace() || it.isControlCharacter() }) {
        throw JidFormatException("domain contains invalid whitespace/control characters")
    }
    return normalized
}

private fun normalizeResource(value: String?): String? {
    if (value == null) return null
    if (value.isEmpty()) throw JidFormatException("resource cannot be empty")
    if (value.any { it.isControlCharacter() }) throw JidFormatException("resource contains control characters")
    return value
}

private fun Char.isControlCharacter(): Boolean = code in 0x00..0x1F || code == 0x7F

class BareJid(
    override val userName: String? = null,
    override val domain: String,
) : Jid {
    override val resource: String? = null

    override fun toString(): String = if (userName != null) "$userName@$domain" else domain

    override fun equals(other: Any?): Boolean = when (other) {
        is BareJid -> userName == other.userName && domain == other.domain
        is FullJid -> userName == other.userName && domain == other.domain
        else -> false
    }

    override fun hashCode(): Int = arrayOf(userName, domain).contentHashCode()
}

class FullJid(
    val bareJid: BareJid,
    override val resource: String,
) : Jid {
    constructor(userName: String? = null, domain: String, resource: String) : this(BareJid(userName, domain), resource)

    val bareJID: BareJid
        get() = bareJid

    override val domain: String
        get() = bareJid.domain

    override val userName: String?
        get() = bareJid.userName

    override fun toString(): String = "${bareJid}/$resource"

    override fun equals(other: Any?): Boolean = when (other) {
        is FullJid -> userName == other.userName && domain == other.domain && resource == other.resource
        is BareJid -> userName == other.userName && domain == other.domain
        else -> false
    }

    override fun hashCode(): Int = arrayOf(userName, domain, resource).contentHashCode()
}
