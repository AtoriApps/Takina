
package lib.takina.core.modules

import lib.takina.core.Context

abstract class AbstractXmppModule(
	override val context: Context,
	final override val type: String,
	final override val features: Array<String>,
	final override val criteria: Criteria? = null,
) : XmppModule