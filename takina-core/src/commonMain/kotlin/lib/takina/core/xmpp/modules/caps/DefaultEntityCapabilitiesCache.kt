
package lib.takina.core.xmpp.modules.caps

/**
 * Default, in-memory capabilities cache.
 */
class DefaultEntityCapabilitiesCache : EntityCapabilitiesCache {

	private val entities = mutableMapOf<String, EntityCapabilitiesModule.Caps>()

	override fun isCached(node: String): Boolean = entities.containsKey(node)

	override fun store(node: String, caps: EntityCapabilitiesModule.Caps) {
		entities[node] = caps
	}

	override fun load(node: String): EntityCapabilitiesModule.Caps? = entities[node]
}