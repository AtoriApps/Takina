
package lib.takina.core.xmpp.modules.sims

import lib.takina.core.Context
import lib.takina.core.builder.TakinaConfigDsl
import lib.takina.core.modules.AbstractXmppModule
import lib.takina.core.modules.XmppModuleProvider
import lib.takina.core.xml.Element
import lib.takina.core.xmpp.ErrorCondition
import lib.takina.core.xmpp.XMPPException

@TakinaConfigDsl
interface ReferenceModuleConfig

class ReferenceModule(context: Context) : ReferenceModuleConfig, AbstractXmppModule(context, TYPE, arrayOf(XMLNS)) {

	companion object : XmppModuleProvider<ReferenceModule, ReferenceModuleConfig> {

		const val XMLNS = "urn:xmpp:reference:0"
		override val TYPE = XMLNS

		override fun instance(context: Context): ReferenceModule = ReferenceModule(context)

		override fun configure(module: ReferenceModule, cfg: ReferenceModuleConfig.() -> Unit) = module.cfg()
	}

	override fun process(element: Element) = throw XMPPException(ErrorCondition.FeatureNotImplemented)

}