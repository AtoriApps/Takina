
package lib.takina.core.xmpp.modules.chatstates

import kotlinx.datetime.Clock
import lib.takina.core.TickEvent
import lib.takina.core.eventbus.Event
import lib.takina.core.eventbus.EventBus
import lib.takina.core.eventbus.EventDefinition
import lib.takina.core.eventbus.EventHandler
import lib.takina.core.xmpp.BareJID
import kotlin.time.Duration.Companion.seconds

data class OwnChatStateChangeEvent(
	val jid: BareJID, val oldState: ChatState, val state: ChatState, val sendUpdate: Boolean,
) : Event(TYPE) {

	companion object : EventDefinition<OwnChatStateChangeEvent> {

		override val TYPE = "lib.takina.core.xmpp.modules.chatstates.OwnChatStateChangeEvent"
	}
}

class ChatStateMachine(
	/**
	 * Recipient of chat state notifications.
	 */
	val jid: BareJID,
	/**
	 * EventBus from Takina.
	 */
	private val eventBus: EventBus,
	/**
	 * if `true` then chat state publishing will be done by `ChatStateModule`. If `false` then client developer is
	 * responsible to send notification to recipient.
	 */
	var sendUpdatesAutomatically: Boolean = false
) : EventHandler<TickEvent> {

	var currentState: ChatState = ChatState.Inactive
		private set
	private var updateTime = Clock.System.now()

	private fun setNewState(newState: ChatState, allowedToSendUpdate: Boolean) {
		val oldState = currentState
		updateTime = Clock.System.now()
		if (currentState != newState) {
			currentState = newState
			eventBus.fire(
				OwnChatStateChangeEvent(
					jid, oldState, newState, sendUpdatesAutomatically && allowedToSendUpdate
				)
			)
		}
	}

	/**
	 * Calculates new Chat State based on time. Have to be called periodically.
	 */
	fun update() {
		val now = Clock.System.now()
		when {
			currentState == ChatState.Active && updateTime < now - 120.seconds -> ChatState.Inactive
			currentState == ChatState.Inactive && updateTime < now - 600.seconds -> ChatState.Gone
			currentState == ChatState.Composing && updateTime < now - 30.seconds -> ChatState.Paused
			else -> null
		}?.let { calculatedState ->
			setNewState(calculatedState, true)
		}
	}

	/**
	 * User activated chat window.
	 */
	fun focused() {
		setNewState(ChatState.Active, true)
	}

	/**
	 * User deactivated chat window.
	 */
	fun focusLost() {
		setNewState(ChatState.Inactive, true)
	}

	/**
	 * Chat window is closed by user.
	 */
	fun closeChat() {
		setNewState(ChatState.Gone, true)
	}

	/**
	 * User composing a message. Function may be called every key press.
	 */
	fun composing() {
		setNewState(ChatState.Composing, true)
	}

	/**
	 * User send message and stop typing.
	 */
	fun sendingMessage() {
		setNewState(ChatState.Active, false)
	}

	override fun onEvent(event: TickEvent) {
		update()
	}

}