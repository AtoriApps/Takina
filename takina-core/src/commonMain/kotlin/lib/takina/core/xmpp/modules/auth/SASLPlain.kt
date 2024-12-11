
package lib.takina.core.xmpp.modules.auth

import lib.takina.core.Context
import lib.takina.core.configuration.Configuration
import lib.takina.core.configuration.JIDPasswordSaslConfig
import lib.takina.core.toBase64
import lib.takina.core.xml.Element

class SASLPlain : SASLMechanism {

    companion object : SASLMechanismProvider<SASLPlain, Unit> {
        override val NAME = "PLAIN"

        override fun instance(): SASLPlain = SASLPlain()

        override fun configure(mechanism: SASLPlain, cfg: Unit.() -> Unit) {}

    }

    override val name = NAME

    override fun evaluateChallenge(
        input: String?,
        context: Context,
        config: Configuration,
        saslContext: SASLContext
    ): String? {
        if (saslContext.complete) return null
        val credentials = config.sasl as JIDPasswordSaslConfig

        val authcId = credentials.authcId ?: credentials.userJID.localpart!!
        val authzId = if (credentials.authcId != null) {
            credentials.userJID.toString()
        } else null
        val password = credentials.passwordCallback.invoke()

        saslContext.completed()
        return buildString {
            if (authzId != null) append(authzId)
            append('\u0000')
            append(authcId)
            append('\u0000')
            append(password)
        }.toBase64()
    }

    override fun isAllowedToUse(
        context: Context,
        config: Configuration,
        saslContext: SASLContext,
        streamFeatures: Element
    ): Boolean =
        config.sasl is JIDPasswordSaslConfig

}