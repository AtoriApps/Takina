
package lib.takina.core.connector

import lib.takina.core.configuration.ConnectionConfig

data class WebSocketConnectorConfig(
	val domain: String, val webSocketUrl: String?, val allowUnsecureConnection: Boolean
) : ConnectionConfig