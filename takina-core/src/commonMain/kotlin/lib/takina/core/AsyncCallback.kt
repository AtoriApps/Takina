
package lib.takina.core

import lib.takina.core.xml.Element
import lib.takina.core.xmpp.ErrorCondition

interface AsyncCallback {

	fun oSuccess(responseStanza: Element)

	fun onError(responseStanza: Element, condition: ErrorCondition)

	fun onTimeout()

}