package lib.takina.core.connector.socket

import org.minidns.hla.DnssecResolverApi
import org.minidns.hla.srv.SrvType
import lib.takina.core.connector.DnsResolver
import lib.takina.core.connector.SrvRecord
import lib.takina.core.exceptions.TakinaException

class DnsResolverMiniDns : DnsResolver {

	override fun resolve(domain: String, completionHandler: (Result<List<SrvRecord>>) -> Unit) {
		try {
			val res = DnssecResolverApi.INSTANCE.resolveSrv(SrvType.xmpp_client, domain)

			if (!res.wasSuccessful()) {
				completionHandler.invoke(Result.failure(TakinaException("Cannot retrieve domain $domain. responseCode=${res.responseCode}")))
			} else {
				res.answers.map {
					SrvRecord(
						port = it.port.toUInt(),
						weight = it.weight.toUInt(),
						priority = it.priority.toUInt(),
						target = it.target.toString()
					)
				}
					.let { completionHandler.invoke(Result.success(it)) }
			}
		} catch (e: Exception) {
			completionHandler.invoke(Result.failure(TakinaException("Cannot retrieve domain $domain. Exception ${e} ")))
		}
	}
}
