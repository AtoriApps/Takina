
package lib.takina.core.xmpp

sealed interface JID {

	val localpart: String?

	val domain: String
}

val JID.bareJID: BareJID
	get() = when (this) {
		is FullJID -> this.bareJID
		is BareJID -> this
	}

val JID.resource: String?
	get() = when (this) {
		is FullJID -> this.resource
		is BareJID -> null
	}

fun JID.copy(): JID = createJID(arrayOf(this.localpart, this.domain, this.resource))
fun JID.copy(resource: String?): JID = createJID(arrayOf(this.localpart, this.domain, resource))

fun String.toJID(): JID = createJID(parseJID(this))

fun String.toFullJID(): FullJID {
	val x = parseJID(this)
	return FullJID(BareJID(x[0], x[1]!!), x[2])
}

fun String.toBareJID(): BareJID {
	val x = parseJID(this)
	return BareJID(x[0], x[1]!!)
}

fun createJID(localpart: String? = null, domain: String, resource: String? = null): JID =
	createJID(arrayOf(localpart, domain, resource))

fun createBareJID(localpart: String? = null, domain: String): BareJID = BareJID(localpart, domain)
fun createFullJID(localpart: String? = null, domain: String, resource: String?): FullJID = FullJID(
	localpart, domain, resource
)

internal fun parseJID(jid: String): Array<String?> {
	val result = arrayOfNulls<String>(3)

	// Cut off the resource part first
	var idx = jid.indexOf('/')

	// Resource part:
	result[2] = if (idx == -1) null else jid.substring(idx + 1)

	val id = if (idx == -1) jid else jid.substring(0, idx)

	// Parse the localpart and the domain name
	idx = id.indexOf('@')
	result[0] = if (idx == -1) null else id.substring(0, idx)
	result[1] = if (idx == -1) id else id.substring(idx + 1)

	return result
}

private fun createJID(tokens: Array<String?>): JID = if (tokens[2].isNullOrBlank()) {
	BareJID(tokens[0], tokens[1]!!)
} else {
	FullJID(BareJID(tokens[0], tokens[1]!!), tokens[2])
}

