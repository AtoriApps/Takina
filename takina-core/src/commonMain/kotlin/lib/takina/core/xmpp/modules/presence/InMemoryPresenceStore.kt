
package lib.takina.core.xmpp.modules.presence

import lib.takina.core.xmpp.BareJID
import lib.takina.core.xmpp.JID
import lib.takina.core.xmpp.bareJID
import lib.takina.core.xmpp.stanzas.Presence

class InMemoryPresenceStore : PresenceStore {

	private val presences = mutableMapOf<JID, Presence>()

	override fun setPresence(stanza: Presence) {
		val jid = stanza.from ?: return
		presences[jid] = stanza
	}

	override fun getPresence(jid: JID): Presence? {
		return presences[jid]
	}

	override fun removePresence(jid: JID): Presence? {
		return presences.remove(jid)
	}

	override fun getPresences(jid: BareJID): List<Presence> {
		return presences.values.filter { presence -> presence.from?.bareJID == jid }
	}

}