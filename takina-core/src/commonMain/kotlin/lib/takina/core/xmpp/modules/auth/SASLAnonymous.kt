
package lib.takina.core.xmpp.modules.auth

import lib.takina.core.Context
import lib.takina.core.configuration.Configuration
import lib.takina.core.xml.Element

class SASLAnonymous : SASLMechanism {

    companion object : SASLMechanismProvider<SASLAnonymous, Unit> {
        override val NAME = "ANONYMOUS"

        override fun instance(): SASLAnonymous = SASLAnonymous()

        override fun configure(mechanism: SASLAnonymous, cfg: Unit.() -> Unit) {}

    }

    override val name = NAME

    override fun evaluateChallenge(
        input: String?,
        context: Context,
        config: Configuration,
        saslContext: SASLContext
    ): String? {
        saslContext.completed()
        return null
    }

    override fun isAllowedToUse(
        context: Context,
        config: Configuration,
        saslContext: SASLContext,
        streamFeatures: Element
    ): Boolean = true

}