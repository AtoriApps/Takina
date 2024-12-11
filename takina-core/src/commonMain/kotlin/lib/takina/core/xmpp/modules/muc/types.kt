
package lib.takina.core.xmpp.modules.muc

enum class Affiliation(
	val xmppValue: String,
	@Deprecated("Use ordinal instead", ReplaceWith("ordinal")) val weight: Int,
	val isEnterOpenRoom: Boolean,
	val isRegisterWithOpenRoom: Boolean,
	val isRetrieveMemberList: Boolean,
	val isEnterMembersOnlyRoom: Boolean,
	val isBanMembersAndUnaffiliatedUsers: Boolean,
	val isEditMemberList: Boolean,
	val isEditModeratorList: Boolean,
	val isEditAdminList: Boolean,
	val isEditOwnerList: Boolean,
	val isChangeRoomDefinition: Boolean,
	val isDestroyRoom: Boolean,
	val isViewOccupantsJid: Boolean,
) {

	Outcast("outcast", 0, false, false, false, false, false, false, false, false, false, false, false, false),
	None("none", 10, true, true, false, false, false, false, false, false, false, false, false, false),
	Member("member", 20, true, true, true, true, false, false, false, false, false, false, false, false),
	Admin("admin", 30, true, true, true, true, true, true, true, false, false, false, false, true),
	Owner("owner", 40, true, true, true, true, true, true, true, true, true, true, true, true);

}

enum class Role(
	val xmppValue: String,
	@Deprecated("Use ordinal instead", ReplaceWith("ordinal")) val weight: Int,
	val isPresentInRoom: Boolean,
	val isReceiveMessages: Boolean,
	val isReceiveOccupantPresence: Boolean,
	val isPresenceBroadcastedToRoom: Boolean,
	val isChangeAvailabilityStatus: Boolean,
	val isChangeRoomNickname: Boolean,
	val isSendPrivateMessages: Boolean,
	val isInviteOtherUsers: Boolean,
	val isSendMessagesToAll: Boolean,
	val isModifySubject: Boolean,
	val isKickParticipantsAndVisitors: Boolean,
	val isGrantVoice: Boolean,
	val isRevokeVoice: Boolean,
) {

	None("none", 0, false, false, false, false, false, false, false, false, false, false, false, false, false),
	Visitor("visitor", 1, true, true, true, true, true, true, true, true, false, false, false, false, false),
	Participant("participant", 2, true, true, true, true, true, true, true, true, true, false, false, false, false),
	Moderator("moderator", 3, true, true, true, true, true, true, true, true, true, true, true, true, true);

}
