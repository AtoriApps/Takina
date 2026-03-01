package org.atoriapps.takina.core.models

data class BareJid(
    val local: String,
    val domain: String,
) {
    override fun toString(): String = "$local@$domain"
}

data class FullJid(
    val bare: BareJid,
    val resource: String,
) {
    override fun toString(): String = "${bare}/$resource"
}

data class Jid(
    val bare: BareJid,
    val resource: String? = null,
) {
    fun toBareJid(): BareJid = bare

    override fun toString(): String = resource?.let { "${bare}/$it" } ?: bare.toString()
}

fun String.toBareJid(): BareJid {
    val value = trim()
    val at = value.indexOf('@')
    require(at in 1 until value.lastIndex) { "Invalid bare JID: $value" }
    return BareJid(local = value.substring(0, at), domain = value.substring(at + 1))
}

fun String.toJid(): Jid {
    val value = trim()
    val slash = value.indexOf('/')
    return if (slash < 0) Jid(value.toBareJid())
    else Jid(
        bare = value.substring(0, slash).toBareJid(),
        resource = value.substring(slash + 1),
    )
}
