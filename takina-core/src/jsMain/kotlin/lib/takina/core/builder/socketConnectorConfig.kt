package lib.takina.core.builder

import lib.takina.core.connector.WebSocketConnectorConfig

@TakinaConfigDsl
class WebSocketConnectionBuilder : ConnectionConfigItemBuilder<WebSocketConnectorConfig> {

	/**
	 * URL of WebSocket. If specified, then auto-detection of WS endpoint with
	 * [AlternativeConnectionMethodModule][lib.takina.core.xmpp.modules.discoaltconn.AlternativeConnectionMethodModule]
	 * will be disabled.
	 */
	var webSocketUrl: String? = null

	/**
	 * Allow unsecure (`ws://`) connection. By default `false`.
	 */
	var allowUnsecureConnection: Boolean = false

	override fun build(root: ConfigurationBuilder, defaultDomain: String?): WebSocketConnectorConfig {
		return WebSocketConnectorConfig(
			domain = defaultDomain ?: throw ConfigurationException("Cannot determine domain name."),
			webSocketUrl = webSocketUrl,
			allowUnsecureConnection = allowUnsecureConnection
		)
	}
}

fun ConfigurationBuilder.webSocketConnector(init: WebSocketConnectionBuilder.() -> Unit) {
	val n = WebSocketConnectionBuilder()
	n.init()
	this.connection = n
}