
package lib.takina.core.xmpp.modules

import lib.takina.core.Context
import lib.takina.core.builder.TakinaConfigDsl
import lib.takina.core.eventbus.Event
import lib.takina.core.eventbus.EventDefinition
import lib.takina.core.modules.Criterion
import lib.takina.core.modules.XmppModule
import lib.takina.core.modules.XmppModuleProvider
import lib.takina.core.xml.Element
import lib.takina.core.xmpp.StreamError

/**
 * XMPP stream error.
 *
 * @property element whole received `<stream:error>` element.
 * @property condition parsed stream error enum to easy check kind of error.
 * @property errorElement error condition element.
 */
data class StreamErrorEvent(val element: Element, val condition: StreamError, val errorElement: Element) : Event(TYPE) {

	companion object : EventDefinition<StreamErrorEvent> {

		override val TYPE = "lib.takina.core.xmpp.modules.StreamErrorEvent"
	}
}

@TakinaConfigDsl
interface StreamErrorModuleConfig

/**
 * Stream Error Handler. The module is integrated part of XMPP Core protocol.
 */
class StreamErrorModule(override val context: Context) : XmppModule, StreamErrorModuleConfig {

	/**
	 * Stream Error Handler. The module is integrated part of XMPP Core protocol.
	 */
	companion object : XmppModuleProvider<StreamErrorModule, StreamErrorModuleConfig> {

		override val TYPE = "StreamErrorModule"
		override fun instance(context: Context): StreamErrorModule = StreamErrorModule(context)

		override fun configure(module: StreamErrorModule, cfg: StreamErrorModuleConfig.() -> Unit) = module.cfg()

		const val XMLNS = "urn:ietf:params:xml:ns:xmpp-streams"
	}

	override val type = TYPE
	override val criteria = Criterion.and(
		Criterion.name("error"), Criterion.xmlns("http://etherx.jabber.org/streams")
	)
	override val features: Array<String>? = null

	private fun getByElementName(name: String): StreamError {
		for (e in StreamError.values()) {
			if (e.elementName == name) {
				return e
			}
		}
		return StreamError.UNKNOWN_STREAM_ERROR
	}

	override fun process(element: Element) {
		val c = element.getChildrenNS(XMLNS).first()
		val e = getByElementName(c.name)

		context.eventBus.fire(StreamErrorEvent(element, e, c))
	}
}