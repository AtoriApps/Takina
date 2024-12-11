
package lib.takina.core.modules

import lib.takina.core.ClearedEvent
import lib.takina.core.Context
import lib.takina.core.Scope
import lib.takina.core.eventbus.EventBus
import lib.takina.core.logger.LoggerFactory
import lib.takina.core.xml.Element
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

interface TakinaModuleProvider<out M : TakinaModule, Configuration : Any> {

	/**
	 * Module identifier.
	 */
	val TYPE: String

	/**
	 * Crreates instance of module.
	 */
	fun instance(context: Context): M

	/**
	 * Applies configuration to module.
	 */
	fun configure(module: @UnsafeVariance M, cfg: Configuration.() -> Unit)

	/**
	 * Return list of dependent modules.
	 */
	fun requiredModules(): List<TakinaModuleProvider<TakinaModule, out Any>> = emptyList()

	fun doAfterRegistration(module: @UnsafeVariance M, moduleManager: ModulesManager) {}

}

/**
 * Main Module Provider interface.
 */
interface XmppModuleProvider<out M : XmppModule, Configuration : Any> : TakinaModuleProvider<M, Configuration>

interface TakinaModule {

	/**
	 * Module identifier.
	 */
	val type: String

	/**
	 * Takina context.
	 */
	val context: Context

	/**
	 * List of features provided by module.
	 */
	val features: Array<String>?

}

/**
 * Main Module interface.
 */
interface XmppModule : TakinaModule {

	/**
	 * Module selection criteria for incoming stanza.
	 */
	val criteria: Criteria?

	/**
	 * Process incoming stanza.
	 */
	fun process(element: Element)

	fun <T> propertySimple(scope: Scope, initialValue: T): ReadWriteProperty<Any?, T> =
		context.propertySimple(scope, initialValue)

	fun <T> property(scope: Scope, initialValueFactory: (() -> T)): ReadWriteProperty<Any?, T> =
		context.property(scope, initialValueFactory)

}

class ClearableValue<T>(
	eventBus: EventBus, private val scope: Scope, private val initialValueFactory: (() -> T),
) : ReadWriteProperty<Any?, T> {

	private val log = LoggerFactory.logger("lib.takina.core.modules.ClearableValue")

	private var value = initialValueFactory.invoke()

	init {
		eventBus.register(ClearedEvent, this::clear)
		log.finest { "Registered cleaner scope=$scope; initialValueFactory=$initialValueFactory" }
	}

	private fun clear(event: ClearedEvent) {
		log.finest("ClearEvent #event")
		if (event.scopes.contains(scope)) {
			this.value = initialValueFactory.invoke()
			log.fine { "Restoring initial value. Scope=$scope; value=$value" }
		}
	}

	override fun getValue(thisRef: Any?, property: KProperty<*>): T {
		return value
	}

	override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
		this.value = value
	}

}

fun <T> Context.propertySimple(scope: Scope, initialValue: T): ReadWriteProperty<Any?, T> =
	ClearableValue(eventBus, scope) { initialValue }

fun <T> Context.property(scope: Scope, initialValueFactory: (() -> T)): ReadWriteProperty<Any?, T> =
	ClearableValue(eventBus, scope, initialValueFactory)
