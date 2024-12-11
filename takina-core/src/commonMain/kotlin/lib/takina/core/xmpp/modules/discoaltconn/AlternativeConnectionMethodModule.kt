package lib.takina.core.xmpp.modules.discoaltconn

import lib.takina.core.Context
import lib.takina.core.builder.TakinaConfigDsl
import lib.takina.core.logger.LoggerFactory
import lib.takina.core.modules.TakinaModule
import lib.takina.core.modules.TakinaModuleProvider
import lib.takina.core.xml.parser.parseXMLOrNull

/**
 * Configuration of [AlternativeConnectionMethodModule]
 */
@TakinaConfigDsl
interface AlternativeConnectionMethodModuleConfig

/**
 * Connection method definition.
 */
data class HostLink(
	/**
	 * Relation type: `urn:xmpp:alt-connections:websocket` or `urn:xmpp:alt-connections:xbosh`.
	 */
	val rel: String,
	/**
	 * Connection URL.
	 */
	val href: String
)

/**
 * Module is implementing Discovering Alternative XMPP Connection Methods ([XEP-0156](https://xmpp.org/extensions/xep-0156.html)).
 *
 */
class AlternativeConnectionMethodModule(override val context: Context) : TakinaModule,
	AlternativeConnectionMethodModuleConfig {

	companion object :
		TakinaModuleProvider<AlternativeConnectionMethodModule, AlternativeConnectionMethodModuleConfig> {

		override val TYPE = "urn:xmpp:alt-connections"

		override fun instance(context: Context): AlternativeConnectionMethodModule =
			AlternativeConnectionMethodModule(context)

		override fun configure(
			module: AlternativeConnectionMethodModule, cfg: AlternativeConnectionMethodModuleConfig.() -> Unit
		) = module.cfg()
	}

	override val type = TYPE
	override val features = null
	private val log =
		LoggerFactory.logger("lib.takina.core.xmpp.modules.discoaltconn.AlternativeConnectionMethodModule")


	/**
	 * Look up for list of alternative connection method.
	 *
	 * @param domain XMPP domain
	 * @param callback called when list is retrieved and when some errors occur.
	 */
	fun discovery(domain: String, callback: (List<HostLink>) -> Unit) {
		val url = "https://$domain/.well-known/host-meta"
		log.finer { "Loading host info from $url" }
		loadRemoteContent(url) {
			log.finer { "Received: $it" }
			if (it.isBlank()) callback(emptyList())
			val element = parseXMLOrNull(it)
			if (element == null) {
				callback(emptyList())
			} else {
				val result = element.getChildren("Link").map {
					HostLink(
						rel = it.attributes["rel"]!!, href = it.attributes["href"]!!
					)
				}
				callback(result)
			}
		}
	}
}