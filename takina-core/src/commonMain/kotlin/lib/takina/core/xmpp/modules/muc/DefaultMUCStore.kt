
package lib.takina.core.xmpp.modules.muc

import lib.takina.core.xmpp.BareJID

class DefaultMUCStore : MUCStore {

	private val rooms = mutableMapOf<BareJID, Room>()

	override fun findRoom(roomJID: BareJID): Room? = rooms[roomJID]

	override fun createRoom(roomJID: BareJID, nickname: String): Room {
		val room = Room(roomJID, nickname, null, State.NotJoined)
		this.rooms[roomJID] = room
		return room
	}
}