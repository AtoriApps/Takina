
package lib.takina.core.modules

import lib.takina.core.xml.Element

interface Criteria {

	fun match(element: Element): Boolean

}