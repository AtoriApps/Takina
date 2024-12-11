package lib.takina.core.configuration

import lib.takina.core.builder.ConfigItemBuilder
import lib.takina.core.builder.ConfigurationBuilder
import lib.takina.core.builder.TakinaConfigDsl
import lib.takina.core.builder.ConfigurationException
import lib.takina.core.xmpp.BareJID

interface JIDPasswordSaslConfig : SaslConfig, UserJIDProvider {

	override val userJID: BareJID

	val passwordCallback: () -> String

	val authcId: String?
}

data class JIDPasswordAuthConfig(
	override val userJID: BareJID,
	override val authcId: String?,
	override val passwordCallback: () -> String,
) : JIDPasswordSaslConfig, DomainProvider, UserJIDProvider {

	override val domain: String
		get() = userJID.domain
}

@TakinaConfigDsl
class JIDPasswordAuthConfigBuilder : ConfigItemBuilder<JIDPasswordAuthConfig> {

	var userJID: BareJID? = null

	var authenticationName: String? = null

	var passwordCallback: (() -> String)? = null

	fun password(callback: (() -> String)?) {
		this.passwordCallback = callback
	}

	override fun build(root: ConfigurationBuilder): JIDPasswordAuthConfig {
		return JIDPasswordAuthConfig(
			userJID = userJID ?: throw ConfigurationException("User JID not specified."),
			passwordCallback = passwordCallback ?: throw ConfigurationException("Password not specified."),
			authcId = authenticationName
		)
	}

}
