
package lib.takina.core.xmpp.modules.jingle

enum class Action(val value: String) {

	ContentAccept("content-accept"),
	ContentAdd("content-add"),
	ContentModify("content-modify"),
	ContentReject("content-reject"),
	ContentRemove("content-remove"),
	DescriptionInfo("description-info"),
	SecurityInfo("security-info"),
	SessionAccept("session-accept"),
	SessionInfo("session-info"),
	SessionInitiate("session-initiate"),
	SessionTerminate("session-terminate"),
	TransportAccept("transport-accept"),
	TransportInfo("transport-info"),
	TransportReject("transport-reject"),
	TransportReplace("transport-replace");

	companion object {

		fun fromValue(value: String) = values().find { it.value == value }
	}
}