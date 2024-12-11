
package lib.takina.core.xmpp.modules.auth

import lib.takina.core.Context
import lib.takina.core.configuration.Configuration
import lib.takina.core.xml.Element

/**
 * Represents a SASL mechanism that can be used for authentication during an XMPP session.
 */
interface SASLMechanism {

    /**
     * Evaluating challenge received from server.
     *
     * @param input received data
     * @param saslContext current [SASLContext]
     *
     * @return calculated response
     */
    fun evaluateChallenge(input: String?, context: Context, config: Configuration, saslContext: SASLContext): String?

    /**
     * This method is used to check if mechanism can be used with current
     * session. For example if no username and passowrd is stored in
     * sessionObject, then PlainMechanism can't be used.
     *
     * @param config current [Configuration]
     *
     * @return `true` if mechanism can be used it current XMPP session.
     */
    fun isAllowedToUse(
        context: Context,
        config: Configuration,
        saslContext: SASLContext,
        streamFeatures: Element
    ): Boolean

    /**
     * Determines whether the authentication exchange has completed.
     *
     * @param saslContext current [SASLContext]
     *
     * @return `true` if exchange is complete.
     */
    fun isComplete(saslContext: SASLContext): Boolean = saslContext.complete

    /**
     * Mechanism name.
     */
    val name: String

}

interface SASLMechanismProvider<out M : SASLMechanism, C : Any> {

    val NAME: String

    fun instance(): M

    fun configure(mechanism: @UnsafeVariance M, cfg: C.() -> Unit)

}

fun SASLMechanismProvider<SASLMechanism, Any>.createInstance(cfg: Any.() -> Unit): SASLMechanism {
    val i = instance()
    configure(i, cfg)
    return i
}