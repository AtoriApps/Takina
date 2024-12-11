package lib.takina.core.xmpp.modules.auth

import lib.takina.core.eventbus.Event
import lib.takina.core.eventbus.EventDefinition

enum class State {

    Unknown,
    InProgress,
    Success,
    Failed
}

interface MechanismData

class SASLContext {

    var mechanism: SASLMechanism? = null
        internal set

    var mechanismData: MechanismData? = null

    var state: State = State.Unknown
        internal set

    var complete = false
        private set

    fun completed() {
        this.complete = true
    }

    override fun toString(): String {
        return "SASLContext(mechanism=$mechanism, state=$state, complete=$complete)"
    }

}

sealed class SASLEvent : Event(TYPE) {

    companion object : EventDefinition<SASLEvent> {

        override val TYPE = "lib.takina.core.xmpp.modules.auth.SASLEvent"
    }

    data class SASLStarted(val mechanism: String) : SASLEvent()
    class SASLSuccess : SASLEvent()
    data class SASLError(val error: SASLModule.SASLError, val description: String?) : SASLEvent()
}
