
package lib.takina.core.xmpp.modules.roster

import lib.takina.core.xmpp.BareJID

class InMemoryRosterStore : RosterStore {

	private var version: String? = null

	private val items = mutableMapOf<BareJID, RosterItem>()

	override fun getVersion(): String? = this.version

	override fun setVersion(version: String) {
		this.version = version
	}

	override fun getItem(jid: BareJID): RosterItem? = this.items[jid]

	override fun removeItem(jid: BareJID) {
		this.items.remove(jid)
	}

	override fun addItem(value: RosterItem) {
		this.items[value.jid] = value
	}

	override fun updateItem(value: RosterItem) {
		this.items[value.jid] = value
	}

	override fun getAllItems(): List<RosterItem> = items.values.toList()
}