
package lib.takina.core.xmpp.modules.jingle

import lib.takina.core.xml.Element
import lib.takina.core.xmpp.modules.jingle.AbstractJingleSessionManager.Media

sealed class MessageInitiationAction(open val id: String, val actionName: String) {
	
	class Propose(override val id: String, val descriptions: List<MessageInitiationDescription>, val data: List<Element>? = null) :
		MessageInitiationAction(id, "propose") {
			val media = descriptions.map { Media.valueOf(it.media) }
		}

	class Retract(override val id: String) : MessageInitiationAction(id, "retract")

	class Accept(override val id: String) : MessageInitiationAction(id, "accept")
	class Proceed(override val id: String) : MessageInitiationAction(id, "proceed")
	class Reject(override val id: String) : MessageInitiationAction(id, "reject")

	companion object {

		fun parse(actionEl: Element): MessageInitiationAction? {
			val id = actionEl.attributes["id"] ?: return null
			when (actionEl.name) {
				"accept" -> return Accept(id)
				"proceed" -> return Proceed(id)
				"propose" -> {
					val descriptions = actionEl.children.mapNotNull { MessageInitiationDescription.parse(it) }
					if (descriptions.isNotEmpty()) {
						return Propose(id, descriptions, actionEl.children.filterNot { it.name == "description" })
					} else {
						return null
					}
				}

				"retract" -> return Retract(id)
				"reject" -> return Reject(id)
				else -> return null
			}
		}
	}
}