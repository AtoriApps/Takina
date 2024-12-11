
package lib.takina.core.xmpp.modules.discovery

import lib.takina.core.xmpp.BareJID

interface NodeDetailsProvider {

	fun getIdentities(sender: BareJID?, node: String?): List<DiscoveryModule.Identity>
	fun getFeatures(sender: BareJID?, node: String?): List<String>
	fun getItems(sender: BareJID?, node: String?): List<DiscoveryModule.Item>

}