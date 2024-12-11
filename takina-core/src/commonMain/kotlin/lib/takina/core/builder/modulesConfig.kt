package lib.takina.core.builder

import lib.takina.core.modules.TakinaModule
import lib.takina.core.modules.TakinaModuleProvider
import lib.takina.core.modules.ModulesManager
import lib.takina.core.xmpp.modules.tick.TickModule

data class Item<M : TakinaModule, B : Any>(
    val provider: TakinaModuleProvider<M, B>,
    val configuration: (B.() -> Unit)? = null,
)

@TakinaConfigDsl
class ModulesConfigBuilder {

    private val providers = mutableMapOf<String, Any>()

    init {
        install(TickModule)
    }

    fun <M : TakinaModule, B : Any> install(
        provider: TakinaModuleProvider<M, B>,
        configuration: B.() -> Unit = {},
    ) {
        this.providers.remove(provider.TYPE)
        this.providers[provider.TYPE] = Item(provider, configuration)
    }
//
//
//	}

    internal fun initializeModules(modulesManager: ModulesManager) {
        val modulesToConfigure =
            providers.values.filterIsInstance<Item<*, Any>>().extendForDependencies().filterIsInstance<Item<*, Any>>()
        modulesToConfigure.forEach { (provider, configuration) ->
            val originalModule = modulesManager.getModuleOrNull<TakinaModule>(provider.TYPE)

            val currentModule = originalModule ?: provider.instance(modulesManager.context)
            provider.configure(currentModule, configuration ?: {})
            if (originalModule == null) {
                modulesManager.register(currentModule)
            }
        }

        modulesToConfigure.forEach { (provider, configuration) ->
            val module = modulesManager.getModuleOrNull<TakinaModule>(provider.TYPE)
            provider.doAfterRegistration(module!!, modulesManager)
        }

    }

}

