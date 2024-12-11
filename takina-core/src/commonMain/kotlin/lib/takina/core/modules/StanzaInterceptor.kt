
package lib.takina.core.modules

import lib.takina.core.xml.Element

@Deprecated("Use StanzaFilter instead")
interface StanzaInterceptor {

    fun afterReceive(element: Element): Element?

    fun beforeSend(element: Element): Element

}

class BeforeSendInterceptorFilter(private val interceptor: StanzaInterceptor) : StanzaFilter {
    override fun doFilter(element: Element?, chain: StanzaFilterChain) {
        val res = element?.let { interceptor.beforeSend(element) }
        chain.doFilter(res)
    }
}

class AfterReceiveInterceptorFilter(private val interceptor: StanzaInterceptor) : StanzaFilter {
    override fun doFilter(element: Element?, chain: StanzaFilterChain) {
        val res = element?.let { interceptor.afterReceive(element) }
        chain.doFilter(res)
    }
}