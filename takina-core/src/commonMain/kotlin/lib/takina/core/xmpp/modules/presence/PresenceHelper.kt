
package lib.takina.core.xmpp.modules.presence

import lib.takina.core.xmpp.stanzas.Presence
import lib.takina.core.xmpp.stanzas.PresenceType
import lib.takina.core.xmpp.stanzas.Show

enum class TypeAndShow {

	/**
	 * The entity or resource is actively interested in chatting.
	 */
	Chat,

	/**
	 * The entity or resource is online.
	 */
	Online,

	/**
	 * The entity or resource is busy (dnd = "Do Not Disturb").
	 */
	Dnd,

	/**
	 * The entity or resource is temporarily away.
	 */
	Away,

	/**
	 * The entity or resource is away for an extended period (xa =
	 * "eXtended Away").
	 */
	Xa,

	/**
	 * The entity or resource is offline.
	 */
	Offline,

	/**
	 * Server returns error instead of presence of entity or resource.
	 */
	Error,

	/**
	 * Type and Show cannot be calculated.
	 */
	Unknown
}

/**
 * Calculate logical status of presence based on stanza type and presence show field.
 */
fun Presence?.typeAndShow(): TypeAndShow {
	if (this == null) return TypeAndShow.Offline
	val type = this.type
	val show = this.show
	return when (type) {
		PresenceType.Error -> TypeAndShow.Error
		PresenceType.Unavailable -> TypeAndShow.Offline
		null -> when (show) {
			null -> TypeAndShow.Online
			Show.XA -> TypeAndShow.Xa
			Show.DnD -> TypeAndShow.Dnd
			Show.Away -> TypeAndShow.Away
			Show.Chat -> TypeAndShow.Chat
		}

		else -> TypeAndShow.Unknown
	}
}