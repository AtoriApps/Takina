
package lib.takina.core.xmpp.modules.presence

import lib.takina.core.xmpp.BareJID
import lib.takina.core.xmpp.JID
import lib.takina.core.xmpp.stanzas.Presence

/**
 * Presence store keeps last received presence stanza per JID.
 */
interface PresenceStore {

	fun setPresence(stanza: Presence)

	fun getPresence(jid: JID): Presence?

	fun removePresence(jid: JID): Presence?

	fun getPresences(jid: BareJID): List<Presence>

}