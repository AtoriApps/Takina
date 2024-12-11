package lib.takina.core.modules

import lib.takina.core.xml.Element

interface StanzaFilter {

    fun doFilter(element: Element?, chain: StanzaFilterChain)

}

interface StanzaFilterChain {

    fun doFilter(element: Element?)

}

fun createFilter(filter: (Element?, StanzaFilterChain) -> Unit): StanzaFilter = object : StanzaFilter {
    override fun doFilter(element: Element?, chain: StanzaFilterChain) = filter(element, chain)
}