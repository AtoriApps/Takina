package lib.takina.core.xmpp.modules.fileupload

import kotlinx.datetime.Instant
import lib.takina.core.AbstractTakina
import lib.takina.core.Context
import lib.takina.core.builder.TakinaConfigDsl
import lib.takina.core.fromISO8601
import lib.takina.core.modules.TakinaModule
import lib.takina.core.modules.TakinaModuleProvider
import lib.takina.core.requests.RequestBuilder
import lib.takina.core.requests.XMPPError
import lib.takina.core.requests.findCondition
import lib.takina.core.xmpp.ErrorCondition
import lib.takina.core.xmpp.JID
import lib.takina.core.xmpp.XMPPException
import lib.takina.core.xmpp.stanzas.IQ
import lib.takina.core.xmpp.stanzas.IQType

sealed class SlotRequestException(
	response: IQ, error: ErrorCondition, description: String?
) : XMPPError(response, error, description)

class FileTooLargeSlotRequestException(
	response: IQ, error: ErrorCondition, description: String?, val maxFileSize: Long
) : SlotRequestException(response, error, description)

class QuotaSlotRequestException(response: IQ, error: ErrorCondition, description: String?, val tryAt: Instant?) :
	SlotRequestException(response, error, description)

@TakinaConfigDsl
interface FileUploadModuleConfig
class FileUploadModule(override val context: AbstractTakina) : TakinaModule, FileUploadModuleConfig {

	companion object : TakinaModuleProvider<FileUploadModule, FileUploadModuleConfig> {

		const val XMLNS = "urn:xmpp:http:upload:0"
		override val TYPE = XMLNS

		override fun instance(context: Context): FileUploadModule = FileUploadModule(context as AbstractTakina)

		override fun configure(module: FileUploadModule, cfg: FileUploadModuleConfig.() -> Unit) = module.cfg()

	}

	override val type = TYPE
	override val features = arrayOf(XMLNS)

	fun requestSlot(
		jid: JID, filename: String, size: Long, contentType: String = "application/octet-stream"
	): RequestBuilder<Slot, IQ> {
		return context.request.iq {
			id()
			type = IQType.Get
			to = jid
			"request" {
				xmlns = XMLNS
				attributes["filename"] = filename
				attributes["size"] = size.toString()
				contentType.let {
					attributes["content-type"] = it
				}
			}
		}.errorConverter(transform = ::createXMPPError).map { iq ->
			val s = iq.getChildrenNS("slot", XMLNS) ?: throw XMPPException(
				ErrorCondition.BadRequest, "Missing 'slot' element"
			)
			val getUrl = s.getFirstChild("get")?.attributes?.get("url") ?: throw XMPPException(
				ErrorCondition.BadRequest, "Missing 'get' element or `url` attribute."
			)
			val putUrl = s.getFirstChild("put")?.attributes?.get("url") ?: throw XMPPException(
				ErrorCondition.BadRequest, "Missing 'put' element or `url` attribute."
			)
			val headers =
				s.getFirstChild("put")!!.getChildren("header").associate { it.attributes["name"]!! to (it.value ?: "") }
					.toMap()

			Slot(putUrl = putUrl, getUrl = getUrl, contentLength = size, contentType = contentType, headers = headers)
		}
	}

	private fun createXMPPError(stanza: IQ): XMPPError {
		val error = findCondition(stanza)
		val ftl = stanza.findChild("iq", "error", "file-too-large")
		if (ftl != null) {
			val maxFileSize = ftl.getFirstChild("max-file-size")?.value?.toLong() ?: -1L
			return FileTooLargeSlotRequestException(stanza, error.condition, error.message, maxFileSize)
		}
		val q = stanza.findChild("iq", "error", "retry")
		if (q != null) {
			val t = q.attributes["stamp"]?.fromISO8601()
			return QuotaSlotRequestException(stanza, error.condition, error.message, t)
		}
		return XMPPError(stanza, error.condition, error.message)
	}

}

data class Slot(
	val putUrl: String,
	val getUrl: String,
	val contentLength: Long,
	val contentType: String,
	val headers: Map<String, String>
)