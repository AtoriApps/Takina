
package lib.takina.core.modules

import lib.takina.core.ReflectionModuleManager
import lib.takina.core.builder.ConfigurationException
import lib.takina.core.logger.LoggerFactory
import lib.takina.core.modules.filter.StanzaFilterProcessor
import lib.takina.core.xml.Element
import kotlin.reflect.KClass

class ModulesManager {

    private val log = LoggerFactory.logger("lib.takina.core.modules.ModulesManager")

    lateinit var context: lib.takina.core.Context

    private val modulesByType: MutableMap<String, TakinaModule> = HashMap()
    private val modulesByClass: MutableMap<KClass<*>, TakinaModule> = HashMap()

    private val modulesOrdered = mutableListOf<XmppModule>()

    private val incomingStanzaFilters = StanzaFilterProcessor()

    private val outgoingStanzaFilters = StanzaFilterProcessor()

    fun register(module: TakinaModule) {
        log.fine { "Registering module '${module.type}'" }
        if (modulesByType.containsKey(module.type)) throw ConfigurationException("Module '${module.type}' is installed already.")
        modulesByType[module.type] = module
        modulesByClass[module::class] = module
        if (module is XmppModule) modulesOrdered.add(module)
    }

    internal fun processReceiveInterceptors(element: Element, result: (Result<Element?>) -> Unit) {
        incomingStanzaFilters.doFilters(element, result)
    }

    internal fun processOutgoingFilters(element: Element, result: (Result<Element?>) -> Unit) {
        outgoingStanzaFilters.doFilters(element, result)
    }

    @Deprecated("Use registerOutgoingFilter() or registerIncomingFilter()")
    fun registerInterceptors(stanzaInterceptors: Array<StanzaInterceptor>) {
        stanzaInterceptors.forEach { interceptor ->
            outgoingStanzaFilters.addToChain(BeforeSendInterceptorFilter(interceptor))
            incomingStanzaFilters.addToChain(AfterReceiveInterceptorFilter(interceptor))
        }
    }

    fun registerOutgoingFilter(filter: StanzaFilter) = outgoingStanzaFilters.addToChain(filter)

    fun registerIncomingFilter(filter: StanzaFilter) = incomingStanzaFilters.addToChain(filter)

    fun getAvailableFeatures(): Array<String> =
        modulesByType.values
            .mapNotNull { xmppModule -> xmppModule.features }
            .flatMap { it.asList() }
            .toTypedArray()

    fun isRegistered(type: String): Boolean = this.modulesByType.containsKey(type)

    @ReflectionModuleManager
    fun isRegistered(cls: KClass<*>): Boolean = this.modulesByClass.containsKey(cls)

    @ReflectionModuleManager
    inline fun <reified T : XmppModule> isRegistered(): Boolean = isRegistered(T::class)

    fun getModules(): Collection<TakinaModule> = this.modulesByType.values.toList()

    fun getModulesFor(element: Element): Array<XmppModule> {
        return modulesOrdered.filter { xmppModule ->
            (xmppModule.criteria != null && xmppModule.criteria!!.match(element))
        }.toTypedArray()
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : TakinaModule> getModule(type: String): T {
        val module = this.modulesByType[type] ?: throw throw NullPointerException("Module '$type' not registered!")
        return module as T
    }

    @ReflectionModuleManager
    @Suppress("UNCHECKED_CAST")
    fun <T : TakinaModule> getModule(cls: KClass<T>): T {
        val module = this.modulesByClass[cls] ?: throw throw NullPointerException("Module not registered!")
        return module as T
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : TakinaModule> getModuleOrNull(type: String): T? {
        return this.modulesByType[type] as T?
    }

    @ReflectionModuleManager
    @Suppress("UNCHECKED_CAST")
    fun <T : TakinaModule> getModuleOrNull(cls: KClass<T>): T? {
        return this.modulesByClass[cls] as T?
    }

    @ReflectionModuleManager
    inline fun <reified T : TakinaModule> getModule(): T = getModule(T::class)

    operator fun <T : TakinaModule> get(type: String): T = getModule(type)

    fun <T : TakinaModule> getModule(provider: TakinaModuleProvider<T, out Any>): T = getModule(provider.TYPE)

    @ReflectionModuleManager
    operator fun <T : TakinaModule> get(cls: KClass<T>): T = getModule(cls)

    fun <T : TakinaModule> getModuleOrNull(provider: TakinaModuleProvider<T, out Any>): T? =
        getModuleOrNull(provider.TYPE)

}