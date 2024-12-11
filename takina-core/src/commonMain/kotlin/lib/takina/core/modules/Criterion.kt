
package lib.takina.core.modules

import lib.takina.core.xml.Element

class Criterion private constructor() {

	companion object {

		fun element(predicate: (Element) -> Boolean): Criteria = object : Criteria {
			override fun match(element: Element): Boolean = predicate.invoke(element)
		}

		fun or(vararg crits: Criteria): Criteria = object : Criteria {
			override fun match(element: Element): Boolean =
				crits.any(predicate = { criteria -> criteria.match(element) })

			override fun toString(): String = "OR(" + crits.joinToString(", ") { it.toString() } + ")"
		}

		fun and(vararg crits: Criteria) = object : Criteria {
			override fun match(element: Element): Boolean =
				crits.all(predicate = { criteria -> criteria.match(element) })

			override fun toString(): String = "AND(" + crits.joinToString(", ") { it.toString() } + ")"
		}

		fun not(crit: Criteria) = object : Criteria {
			override fun match(element: Element): Boolean = !crit.match(element)
			override fun toString(): String = "!$crit"
		}

		fun name(name: String): Criteria {
			return object : Criteria {
				override fun match(element: Element): Boolean = name == element.name
				override fun toString(): String = "{ name == $name }"
			}
		}

		fun nameAndXmlns(name: String, xmlns: String): Criteria {
			return object : Criteria {
				override fun match(element: Element): Boolean = name == element.name && xmlns == element.xmlns
				override fun toString(): String = "{ name == $name && xmlns == ${xmlns} }"
			}
		}

		fun xmlns(xmlns: String): Criteria {
			return object : Criteria {
				override fun match(element: Element): Boolean = xmlns == element.xmlns
				override fun toString(): String = "{ xmlns == ${xmlns} }"
			}
		}

		fun chain(vararg children: Criteria): Criteria {

			fun find(children: List<Element>, cr: Criteria): Element? {
				return children.firstOrNull { element -> cr.match(element) }
			}

			return object : Criteria {
				override fun match(element: Element): Boolean {
					var current: Element? = element
					val it = children.iterator()
					if (!it.hasNext() || !it.next()
							.match(current!!)
					) return false

					while (it.hasNext()) {
						val cr = it.next()
						current = find(current!!.children, cr)
						if (current == null) return false
					}

					return true
				}

				override fun toString(): String = children.joinToString(".") { it.toString() }
			}
		}

	}
}