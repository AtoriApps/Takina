
package lib.takina.core.modules

import lib.takina.core.Context
import lib.takina.core.xml.Element
import lib.takina.core.xmpp.ErrorCondition
import lib.takina.core.xmpp.XMPPException
import lib.takina.core.xmpp.stanzas.IQ
import lib.takina.core.xmpp.stanzas.IQType
import lib.takina.core.xmpp.stanzas.wrap

/**
 * An abstract class representing an XMPP IQ module.
 *
 * @param context The context object.
 * @param type The type of the module.
 * @param features The array of features supported by the module.
 * @param criteria The criteria for matching the XML element.
 */
abstract class AbstractXmppIQModule(
	context: Context, type: String, features: Array<String>, criteria: Criteria,
) : AbstractXmppModule(context, type, features, criteria) {

	final override fun process(element: Element) {
		val iq: IQ = wrap(element)
		when (iq.type) {
			IQType.Set -> processSet(iq)
			IQType.Get -> processGet(iq)
			else -> throw XMPPException(ErrorCondition.BadRequest)
		}
	}

	/**
	 * Processes the IQ element with the Get type.
	 *
	 * @param element The IQ element to process.
	 */
	abstract fun processGet(element: IQ)

	/**
	 * Processes the IQ element with the Set type.
	 *
	 * @param element The IQ element to process.
	 */
	abstract fun processSet(element: IQ)

}