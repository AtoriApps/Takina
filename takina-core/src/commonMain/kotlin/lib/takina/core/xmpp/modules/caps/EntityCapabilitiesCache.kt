
package lib.takina.core.xmpp.modules.caps

/**
 * Cache for entity capabilities.
 */
interface EntityCapabilitiesCache {

	/**
	 * Check if given node capabilities exists in cache storage.
	 * @param node node name to check.
	 */
	fun isCached(node: String): Boolean

	/**
	 * Save node capabilities into cache.
	 * @param[node] entity node name
	 * @param[caps] node capabilities to store.
	 */
	fun store(node: String, caps: EntityCapabilitiesModule.Caps)

	/**
	 * Gets capabilities of node from cache.
	 * @param node node name
	 * @return [EntityCapabilitiesModule.Caps] or `null`.
	 */
	fun load(node: String): EntityCapabilitiesModule.Caps?
}