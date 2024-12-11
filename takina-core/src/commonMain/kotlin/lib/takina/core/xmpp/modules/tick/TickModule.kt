package lib.takina.core.xmpp.modules.tick

import lib.takina.core.AbstractTakina
import lib.takina.core.AbstractTakina.State.*
import lib.takina.core.Context
import lib.takina.core.TakinaStateChangeEvent
import lib.takina.core.builder.TakinaConfigDsl
import lib.takina.core.logger.LoggerFactory
import lib.takina.core.modules.TakinaModule
import lib.takina.core.modules.TakinaModuleProvider
import lib.takina.core.modules.ModulesManager

@TakinaConfigDsl
interface TickModuleConfig {

    var tickTimer: TickTimer

}

interface TickTimer {
    fun startTimer(context: Context)
    fun stopTimer(context: Context)
}

class TickModule(override val context: Context) : TakinaModule, TickModuleConfig {

    companion object : TakinaModuleProvider<TickModule, TickModuleConfig> {
        override val TYPE = "takina:tick"
        override fun instance(context: Context): TickModule = TickModule(context)

        override fun configure(module: TickModule, cfg: TickModuleConfig.() -> Unit) =
            module.cfg()

        override fun doAfterRegistration(module: TickModule, moduleManager: ModulesManager) =
            module.context.eventBus.register(TakinaStateChangeEvent, module::doOnTakinaStateChange)
    }

    private val log = LoggerFactory.logger("lib.takina.core.xmpp.modules.tick.TickModule")

    override val type: String = TYPE
    override val features: Array<String>? = null

    override var tickTimer: TickTimer = createTickTimer()

    private fun doOnTakinaStateChange(event: TakinaStateChangeEvent) {
        when (event.newState) {
            Connecting -> {
                log.info("Starting Ticker.")
                tickTimer.startTimer(context)
            }

            Disconnecting, Disconnected, Stopped -> {
                log.info("Stopping Ticker.")
                tickTimer.stopTimer(context)
            }

            Connected -> {
                log.info("Takina connected.")
            }
        }
    }

}

expect fun createTickTimer(): TickTimer