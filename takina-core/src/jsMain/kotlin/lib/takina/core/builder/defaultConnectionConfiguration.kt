package lib.takina.core.builder

import lib.takina.core.configuration.ConnectionConfig
import lib.takina.core.connector.WebSocketConnectorConfig

actual fun defaultConnectionConfiguration(
	accountBuilder: ConfigurationBuilder,
	defaultDomain: String,
): ConnectionConfig {
	val d = defaultDomain
	return WebSocketConnectorConfig(domain = defaultDomain, webSocketUrl = null, allowUnsecureConnection = false)
}