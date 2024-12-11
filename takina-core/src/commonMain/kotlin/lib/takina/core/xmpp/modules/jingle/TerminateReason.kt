
package lib.takina.core.xmpp.modules.jingle

import lib.takina.core.xml.Element
import lib.takina.core.xml.element

enum class TerminateReason(val value: String) {

	AlternativeSession("alternative-session"),
	Busy("busy"),
	Cancel("cancel"),
	ConnectivityError("connectivity-error"),
	Decline("decline"),
	Expired("expired"),
	FailedApplication("failed-application"),
	FailedTransport("failed-transport"),
	GeneralError("general-error"),
	Gone("gone"),
	IncompatibleParameters("incompatible-parameters"),
	MediaError("media-error"),
	SecurityError("security-error"),
	Success("success"),
	Timeout("timeout"),
	UnsupportedApplications("unsupported-applications"),
	UnsupportedTransports("unsupported-transports");

	fun toElement(): Element = element(value) {}
	fun toReasonElement(): Element = element("reason") {
		addChild(toElement())
	}

	companion object {

		fun fromValue(value: String) = values().find { it.value == value }
	}
}