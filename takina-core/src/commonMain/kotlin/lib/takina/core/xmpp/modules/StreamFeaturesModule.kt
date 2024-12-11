
package lib.takina.core.xmpp.modules

import lib.takina.core.Context
import lib.takina.core.Scope
import lib.takina.core.builder.TakinaConfigDsl
import lib.takina.core.eventbus.Event
import lib.takina.core.eventbus.EventDefinition
import lib.takina.core.modules.XmppModule
import lib.takina.core.modules.XmppModuleProvider
import lib.takina.core.xml.Element

data class StreamFeaturesEvent(val features: Element) : Event(TYPE) {

	companion object : EventDefinition<StreamFeaturesEvent> {

		override val TYPE = "lib.takina.core.xmpp.modules.StreamFeaturesEvent"
	}
}

@TakinaConfigDsl
interface StreamFeaturesModuleConfig

/**
 * Stream features module. The module is integrated part of XMPP Core protocol.
 *
 * Module keeps information about set of features provided by current XMPP stream.
 */
class StreamFeaturesModule(override val context: Context) : XmppModule, StreamFeaturesModuleConfig {

	companion object : XmppModuleProvider<StreamFeaturesModule, StreamFeaturesModuleConfig> {

		override val TYPE = "StreamFeaturesModule"
		override fun instance(context: Context): StreamFeaturesModule = StreamFeaturesModule(context)

		override fun configure(module: StreamFeaturesModule, cfg: StreamFeaturesModuleConfig.() -> Unit) = module.cfg()
	}

	override val type = TYPE
	override val criteria = lib.takina.core.modules.Criterion.and(
		lib.takina.core.modules.Criterion.name("features"),
		lib.takina.core.modules.Criterion.xmlns("http://etherx.jabber.org/streams")
	)

	/**
	 * Keeps whole received `<stream:features>` element.
	 */
	var streamFeatures: Element? by propertySimple(Scope.Stream, null)
		private set

	override val features: Array<String>? = null

	/**
	 * Returns `true` if feature with given name and xmlns exists in stream features element.
	 * @param name stream feature name
	 * @param xmlns stream feature xmlns
	 */
	fun isFeatureAvailable(name: String, xmlns: String): Boolean = streamFeatures?.getChildrenNS(name, xmlns) != null

	/**
	 * Returns feature element or `null` if doesn't exist.
	 * @param name stream feature name
	 * @param xmlns stream feature xmlns
	 */
	fun getFeatureOrNull(name: String, xmlns: String): Element? = streamFeatures?.getChildrenNS(name, xmlns)


	override fun process(element: Element) {
		streamFeatures = element
		context.eventBus.fire(StreamFeaturesEvent(element))
	}
}