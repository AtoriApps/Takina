
package lib.takina.core.connector.socket

import lib.takina.core.configuration.ConnectionConfig
import lib.takina.core.connector.DnsResolver
import javax.net.ssl.TrustManager

data class SocketConnectorConfig(
	val domain: String,
	val hostname: String?,
	val port: Int,
	val trustManager: TrustManager,
	val dnsResolver: DnsResolver,
	val hostnameVerifier: XMPPHostnameVerifier,
	val tlsProcessorFactory: TLSProcessorFactory
) : ConnectionConfig