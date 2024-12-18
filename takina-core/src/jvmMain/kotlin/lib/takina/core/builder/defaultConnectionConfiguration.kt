package lib.takina.core.builder

import lib.takina.core.configuration.ConnectionConfig
import lib.takina.core.connector.socket.*
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

/**
 * The DefaultTLSProcessorFactory variable represents the default implementation of the TLSProcessorFactory interface.
 */
val DefaultTLSProcessorFactory: TLSProcessorFactory = DefaultTLSProcessor

actual fun defaultConnectionConfiguration(
	accountBuilder: ConfigurationBuilder,
	defaultDomain: String,
): ConnectionConfig = SocketConnectorConfig(
	domain = defaultDomain,
	hostname = null,
	port = 5222,
	trustManager = object : X509TrustManager {
		override fun checkClientTrusted(p0: Array<out X509Certificate>?, p1: String?) {
		}

		override fun checkServerTrusted(p0: Array<out X509Certificate>?, p1: String?) {
		}

		override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
	},
	dnsResolver = DnsResolverMiniDns(),
	hostnameVerifier = DefaultHostnameVerifier(),
	tlsProcessorFactory = DefaultTLSProcessorFactory
)