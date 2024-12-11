package lib.takina.core.xmpp.modules.auth

import lib.takina.core.builder.ConfigItemBuilder
import lib.takina.core.builder.ConfigurationBuilder
import lib.takina.core.builder.TakinaConfigDsl
import lib.takina.core.builder.ConfigurationException
import lib.takina.core.configuration.DomainProvider
import lib.takina.core.configuration.SaslConfig

data class AnonymousSaslConfig(override val domain: String) : SaslConfig, DomainProvider

@TakinaConfigDsl
class AnonymousSaslConfigBuilder : ConfigItemBuilder<AnonymousSaslConfig> {

	var domain: String? = null

	override fun build(root: ConfigurationBuilder): AnonymousSaslConfig =
		AnonymousSaslConfig(domain = this.domain ?: throw ConfigurationException("Domain is not specified."))
}

fun ConfigurationBuilder.authAnonymous(init: AnonymousSaslConfigBuilder.() -> Unit) {
	val n = AnonymousSaslConfigBuilder()
	n.init()
	this.auth = n
}