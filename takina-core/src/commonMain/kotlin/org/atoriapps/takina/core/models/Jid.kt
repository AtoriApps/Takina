package org.atoriapps.takina.core.models

class JidFormatException(message: String) : IllegalArgumentException(message)

sealed interface Jid {
    val local: String
    val domain: String
    val resource: String?
}

val Jid.bareJid: BareJid
    get() = when (this) {
        is BareJid -> this
        is FullJid -> bare
    }

fun Jid.copy(resource: String? = this.resource): Jid = createJid(local = local, domain = domain, resource = resource)

fun String.toJid(): Jid = parseJid(this).toJid()
fun String.toFullJid(): FullJid = parseJid(this).toFullJid()
fun String.toBareJid(): BareJid = parseJid(this).toBareJid()
fun String.toBareJidOrNull(): BareJid? = runCatching { toBareJid() }.getOrNull()

fun createJid(local: String, domain: String, resource: String? = null): Jid {
    val parsed = ParsedJid(
        local = normalizeLocalPart(local),
        domain = normalizeDomain(domain),
        resource = normalizeResource(resource),
    )

    return parsed.toJid()
}

fun createBareJid(local: String, domain: String): BareJid = createJid(local = local, domain = domain, resource = null) as BareJid

fun createFullJid(local: String, domain: String, resource: String): FullJid = createJid(local = local, domain = domain, resource = resource) as FullJid

private data class ParsedJid(
    val local: String,
    val domain: String,
    val resource: String?,
) {
    fun toJid(): Jid = if (resource == null) BareJid(local, domain) else FullJid(BareJid(local, domain), resource)
    fun toFullJid(): FullJid = resource?.let { FullJid(BareJid(local, domain), it) } ?: throw JidFormatException("full JID requires a resource")
    fun toBareJid(): BareJid = BareJid(local, domain)
}

private fun parseJid(raw: String): ParsedJid {
    val jid = raw.trim()
    if (jid.isEmpty()) throw JidFormatException("Jid cannot be blank")

    val slashIndex = jid.indexOf('/')
    val barePart = if (slashIndex == -1) jid else jid.substring(0, slashIndex)
    val resourcePart = if (slashIndex == -1) null else jid.substring(slashIndex + 1)
    if (barePart.isEmpty()) throw JidFormatException("Jid bare part cannot be blank")

    val atIndex = barePart.indexOf('@')
    if (atIndex <= 0 || atIndex == barePart.lastIndex) throw JidFormatException("Jid must be in the form local@domain")
    if (barePart.indexOf('@', startIndex = atIndex + 1) != -1) throw JidFormatException("Jid contains multiple '@' symbols")

    val local = barePart.substring(0, atIndex)
    val domain = barePart.substring(atIndex + 1)

    return ParsedJid(
        local = normalizeLocalPart(local),
        domain = normalizeDomain(domain),
        resource = normalizeResource(resourcePart),
    )
}


private fun normalizeLocalPart(value: String): String = if (value.isBlank()) throw JidFormatException("LocalPart cannot be blank")
else if (value.any { it.isWhitespace() || it.isControlCharacter() }) throw JidFormatException("LocalPart contains invalid whitespace/control characters")
else if ('@' in value || '/' in value) throw JidFormatException("LocalPart cannot contain '@' or '/'")
else return value

private fun normalizeDomain(value: String): String {
    val normalized = value.trim().lowercase()

    return if (normalized.isEmpty()) throw JidFormatException("domain cannot be blank")
    else if (normalized.startsWith('.') || normalized.endsWith('.')) throw JidFormatException("domain cannot start/end with '.'")
    else if (normalized.any { it.isWhitespace() || it.isControlCharacter() }) throw JidFormatException("domain contains invalid whitespace/control characters")
    else if ('@' in normalized || '/' in normalized) throw JidFormatException("domain cannot contain '@' or '/'")
    else normalized
}

private fun normalizeResource(value: String?) = if (value == null) null
else if (value.isEmpty()) throw JidFormatException("resource cannot be empty")
else if (value.any { it.isControlCharacter() }) throw JidFormatException("resource contains control characters")
else value

private fun Char.isControlCharacter(): Boolean = code in 0x00..0x1F || code == 0x7F

class BareJid(override val local: String, override val domain: String) : Jid {
    init {
        normalizeLocalPart(local)
        require(domain == normalizeDomain(domain)) { "domain must be normalized; use toBareJid/createBareJid for automatic normalization" }
    }

    override val resource: String? = null

    override fun toString(): String = "$local@$domain"

    override fun equals(other: Any?): Boolean = other is BareJid && local == other.local && domain == other.domain

    override fun hashCode(): Int = arrayOf(local, domain).contentHashCode()
}

class FullJid(val bare: BareJid, override val resource: String) : Jid {
    init {
        normalizeResource(resource)
    }

    constructor(local: String, domain: String, resource: String) : this(BareJid(local, domain), resource)

    val bareJID: BareJid
        get() = bare

    override val local: String
        get() = bare.local

    override val domain: String
        get() = bare.domain

    val userName: String
        get() = local

    override fun toString(): String = "${bare}/$resource"

    override fun equals(other: Any?): Boolean = other is FullJid && bare == other.bare && resource == other.resource

    override fun hashCode(): Int = arrayOf(bare, resource).contentHashCode()
}
