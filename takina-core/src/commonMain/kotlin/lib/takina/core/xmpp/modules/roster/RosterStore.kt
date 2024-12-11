
package lib.takina.core.xmpp.modules.roster

import lib.takina.core.xmpp.BareJID

/**
 *  Roster store keeps received roster items.
 */
interface RosterStore {

	/**
	 * Returns roster version saved in store.
	 */
	fun getVersion(): String?

	/**
	 * Saves received roster version.
	 * @param version version of received roster.
	 */
	fun setVersion(version: String)

	/**
	 * Returns roster item for given bare JID.
	 */
	fun getItem(jid: BareJID): RosterItem?

	/**
	 * Removes roster item identified by given bare JID from store.
	 */
	fun removeItem(jid: BareJID)

	/**
	 * Adds roster item to store.
	 * @param value roster item to add.
	 */
	fun addItem(value: RosterItem)

	/**
	 * Updates roster item (identified by [RosterItem.jid]) in store.
	 * @param value roster item to update.
	 */
	fun updateItem(value: RosterItem)

	/**
	 * Returns all roster items saved in store.
	 */
	fun getAllItems(): List<RosterItem>
}