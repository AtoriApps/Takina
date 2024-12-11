
package lib.takina.core.xmpp.modules.spam

import lib.takina.core.Context
import lib.takina.core.builder.TakinaConfigDsl
import lib.takina.core.eventbus.Event
import lib.takina.core.eventbus.EventDefinition
import lib.takina.core.exceptions.TakinaException
import lib.takina.core.modules.AbstractXmppIQModule
import lib.takina.core.modules.Criterion
import lib.takina.core.modules.XmppModuleProvider
import lib.takina.core.requests.RequestBuilder
import lib.takina.core.xml.Element
import lib.takina.core.xmpp.BareJID
import lib.takina.core.xmpp.ErrorCondition
import lib.takina.core.xmpp.XMPPException
import lib.takina.core.xmpp.stanzas.IQ
import lib.takina.core.xmpp.stanzas.IQType
import lib.takina.core.xmpp.toBareJID

enum class Reason {

	NotSpecified,

	/**
	 * Used for reporting a JID that is sending unwanted messages.
	 */
	Spam,

	/**
	 * Used for reporting general abuse.
	 */
	Abuse
}

/**
 * Event fired when server push information about changes in list of blocked contacts
 *
 * Note, that server push those information only when entity retrieved previously list of blocked contacts!
 */
sealed class BlockingCommandEvent : Event(TYPE) {

	companion object : EventDefinition<BlockingCommandEvent> {

		override val TYPE = "lib.takina.core.xmpp.modules.spam.BlockingCommandEvent"
	}

	/**
	 * Event fired when new contact was blocked.
	 */
	data class Blocked(val jid: BareJID, val reason: Reason, val text: String?) : BlockingCommandEvent()

	/**
	 * Event fired, when contact was unblocked.
	 */
	data class Unblocked(val jid: BareJID) : BlockingCommandEvent()

	/**
	 * Event fired when all contacts was unblocked.
	 */
	class UnblockedAll : BlockingCommandEvent()
}

@TakinaConfigDsl
interface BlockingCommandModuleConfig

/**
 * Blocking command module.
 *
 * Module implements [Blocking Command](https://xmpp.org/extensions/xep-0191.html) and [Spam Reporting](https://xmpp.org/extensions/xep-0377.html) extension.
 */
class BlockingCommandModule(override val context: Context) : BlockingCommandModuleConfig, AbstractXmppIQModule(
	context, TYPE, arrayOf(XMLNS), Criterion.chain(Criterion.name(IQ.NAME), Criterion.xmlns(XMLNS))
) {

	companion object : XmppModuleProvider<BlockingCommandModule, BlockingCommandModuleConfig> {

		const val XMLNS = "urn:xmpp:blocking"
		const val XMLNS_REPORT = "urn:xmpp:reporting:0"
		override val TYPE = XMLNS

		override fun instance(context: Context): BlockingCommandModule = BlockingCommandModule(context)

		override fun configure(module: BlockingCommandModule, cfg: BlockingCommandModuleConfig.() -> Unit) =
			module.cfg()

	}

	override fun processGet(element: IQ) = throw XMPPException(ErrorCondition.BadRequest)

	override fun processSet(element: IQ) {
		if (element.from != null) throw XMPPException(ErrorCondition.NotAllowed)
		element.getFirstChild("block")
			?.let { processBlock(it) }
		element.getFirstChild("unblock")
			?.let { processUnblock(it) }
	}

	private fun processUnblock(unblock: Element) {
		val items = unblock.children
		if (items.isEmpty()) {
			context.eventBus.fire(BlockingCommandEvent.UnblockedAll())
		} else {
			items.filter { it.name == "item" }
				.forEach {
					context.eventBus.fire(BlockingCommandEvent.Unblocked(it.attributes["jid"]!!.toBareJID()))
				}
		}
	}

	private fun processBlock(block: Element) {
		block.children.filter { it.name == "item" }
			.forEach {
				val (reason, text) = it.getChildrenNS("report", XMLNS_REPORT)
					?.let { report ->
						val text = report.getFirstChild("text")?.value
						val reason = when {
							report.getFirstChild("abuse") != null -> Reason.Abuse
							report.getFirstChild("spam") != null -> Reason.Spam
							else -> Reason.NotSpecified
						}
						Pair(reason, text)
					} ?: Pair<Reason, String?>(Reason.NotSpecified, null)
				context.eventBus.fire(BlockingCommandEvent.Blocked(it.attributes["jid"]!!.toBareJID(), reason, text))
			}
	}

	/**
	 *  Prepares retrieving blocked contacts request.
	 *  @return request builder provides list of blocked [BareJID]s.
	 */
	fun retrieveList(): RequestBuilder<List<BareJID>, IQ> = context.request.iq {
		type = IQType.Get
		"blocklist" {
			xmlns = XMLNS
		}
	}
		.map { value -> createRetrieveResponse(value) }

	private fun createRetrieveResponse(stanza: IQ): List<BareJID> {
		return stanza.getChildrenNS("blocklist", XMLNS)?.children?.filter { it.name == "item" }
			?.map { it.attributes["jid"]!!.toBareJID() } ?: emptyList()
	}

	/**
	 * Prepare request to block contact or report as SPAM.
	 * @param jid [BareJID] to be blocked.
	 * @param reason [Reason] of block. By default it is [NotSpecified][Reason.NotSpecified].
	 * @param text human readable description of blocking reason. Used only if [reason] is equals to [Abuse][Reason.Abuse] or [Spam][Reason.Spam]
	 */
	fun block(jid: BareJID, reason: Reason = Reason.NotSpecified, text: String? = null): RequestBuilder<Unit, IQ> =
		context.request.iq {
			type = IQType.Set
			"block" {
				xmlns = XMLNS
				"item" {
					attributes["jid"] = jid.toString()
					if (reason != Reason.NotSpecified) {
						"report" {
							xmlns = "urn:xmpp:reporting:0"
							when (reason) {
								Reason.Abuse -> "abuse" {}
								Reason.Spam -> "spam" {}
								else -> throw TakinaException("Unsupported reason $reason")
							}
							text?.let { "text" { +it } }
						}
					}
				}
			}
		}
			.map { }

	/**
	 * Prepares request to unblock specific contacts.
	 * @param jids [BareJID]s to unblock. If not provided, then all blocked contacts will be unblocked!
	 */
	fun unblock(vararg jids: BareJID): RequestBuilder<Unit, IQ> = context.request.iq {
		type = IQType.Set
		"unblock" {
			xmlns = XMLNS
			jids.forEach { jid ->
				"item" {
					attributes["jid"] = jid.toString()
				}
			}
		}
	}
		.map { }

	/**
	 * Prepares request to unblock all blocked contacts.
	 */
	fun unblockAll(): RequestBuilder<Unit, IQ> = unblock()

}