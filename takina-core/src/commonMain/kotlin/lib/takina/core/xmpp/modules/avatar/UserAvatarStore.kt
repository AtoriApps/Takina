
package lib.takina.core.xmpp.modules.avatar

import lib.takina.core.xmpp.BareJID

interface UserAvatarStore {

	fun store(userJID: BareJID, avatarID: String?, data: UserAvatarModule.Avatar?)
	fun load(userJID: BareJID, avatarID: String): UserAvatarModule.Avatar?
	fun isStored(userJID: BareJID, avatarID: String): Boolean
}