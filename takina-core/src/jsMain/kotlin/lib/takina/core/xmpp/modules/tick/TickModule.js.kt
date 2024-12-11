package lib.takina.core.xmpp.modules.tick

import kotlinx.browser.window
import lib.takina.core.Context
import lib.takina.core.TickEvent

actual fun createTickTimer(): TickTimer  = DefaultTickTimer()

class DefaultTickTimer() : TickTimer {

    private var intervalHandler: Int = -1

    private var tickCounter: Long = 0

    override fun startTimer(context: Context) {
        intervalHandler = window.setInterval({ tick(context) }, 2000)
    }

    override fun stopTimer(context: Context) {
        window.clearInterval(intervalHandler)
    }

    private fun tick(context: Context) {
        context.eventBus.fire(TickEvent(++tickCounter))
    }

}