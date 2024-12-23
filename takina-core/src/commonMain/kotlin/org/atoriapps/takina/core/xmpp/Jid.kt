package org.atoriapps.takina.core.xmpp

// 抄来的，根据需要改改

sealed interface Jid {
    val userName: String?

    // 域不能为Null
    val domain: String
}

val Jid.bareJID: BareJid
    get() = when (this) {
        is FullJid -> this.bareJID
        is BareJid -> this
    }

// TODO：如果没有Res，要不要抛出？
val Jid.resource: String?
    get() = when (this) {
        is FullJid -> this.resource
        is BareJid -> null
    }

fun Jid.copy(): Jid = createJid(arrayOf(this.userName, this.domain, this.resource))
fun Jid.copy(resource: String?): Jid = createJid(arrayOf(this.userName, this.domain, resource))

fun String.toJid(): Jid = createJid(parseJid(this))

fun String.toFullJid(): FullJid {
    val x = parseJid(this)
    return FullJid(BareJid(x[0], x[1]!!), x[2])
}

fun String.toBareJid(): BareJid {
    val x = parseJid(this)
    return BareJid(x[0], x[1]!!)
}

fun createJid(userName: String? = null, domain: String, resource: String? = null): Jid =
    createJid(arrayOf(userName, domain, resource))

fun createBareJid(userName: String? = null, domain: String): BareJid = BareJid(userName, domain)
fun createFullJid(userName: String? = null, domain: String, resource: String?): FullJid = FullJid(
    userName, domain, resource
)

internal fun parseJid(jid: String): Array<String?> {
    val result = arrayOfNulls<String>(3)

    // Cut off the resource part first
    var idx = jid.indexOf('/')

    // Resource part:
    result[2] = if (idx == -1) null else jid.substring(idx + 1)

    val id = if (idx == -1) jid else jid.substring(0, idx)

    // Parse the userName and the domain name
    idx = id.indexOf('@')
    result[0] = if (idx == -1) null else id.substring(0, idx)
    result[1] = if (idx == -1) id else id.substring(idx + 1)

    return result
}

private fun createJid(tokens: Array<String?>): Jid =
    if (tokens[2].isNullOrBlank()) BareJid(tokens[0], tokens[1]!!)
    else FullJid(BareJid(tokens[0], tokens[1]!!), tokens[2])

class BareJid(override val userName: String? = null, override val domain: String) : Jid {
    override fun toString(): String {
        return (when {
            userName != null -> "$userName@$domain"
            else -> domain
        })
    }

    override fun equals(other: Any?): Boolean = equalsJID(this, other)

    override fun hashCode(): Int = arrayOf(userName, domain).contentHashCode()
}

class FullJid(val bareJID: BareJid, val resource: String?) : Jid {
    constructor(userName: String? = null, domain: String, resource: String? = null) : this(
        BareJid(userName, domain), resource
    )

    override val domain
        get() = bareJID.domain

    override val userName
        get() = bareJID.userName

    override fun toString(): String = bareJID.toString() + if (resource != null) "/$resource" else ""

    override fun equals(other: Any?): Boolean = equalsJID(this, other)

    override fun hashCode(): Int = arrayOf(userName, domain).contentHashCode()
}

internal fun equalsJID(jid: Jid, other: Any?): Boolean = if (jid === other) {
    true
} else if (jid is FullJid && other is FullJid) {
    jid.userName == other.userName && jid.domain == other.domain && jid.resource == other.resource
} else if (jid is BareJid && other is BareJid) {
    jid.userName == other.userName && jid.domain == other.domain
} else if (jid is FullJid && other is BareJid) {
    jid.userName == other.userName && jid.domain == other.domain
} else if (jid is BareJid && other is FullJid) {
    jid.userName == other.userName && jid.domain == other.domain
} else false