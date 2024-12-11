
package lib.takina.core.xml.parser

import lib.takina.core.xml.Element

interface StreamListener {

	fun onNextElement(element: Element)

	fun onStreamClose()

	fun onStreamOpened(attrs: Map<String, String>)

}