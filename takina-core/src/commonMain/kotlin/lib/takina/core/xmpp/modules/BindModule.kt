
package lib.takina.core.xmpp.modules

import lib.takina.core.AbstractTakina
import lib.takina.core.Context
import lib.takina.core.Scope
import lib.takina.core.builder.TakinaConfigDsl
import lib.takina.core.eventbus.Event
import lib.takina.core.eventbus.EventDefinition
import lib.takina.core.modules.Criteria
import lib.takina.core.modules.XmppModule
import lib.takina.core.modules.XmppModuleProvider
import lib.takina.core.requests.RequestBuilder
import lib.takina.core.xml.Element
import lib.takina.core.xml.element
import lib.takina.core.xmpp.*
import lib.takina.core.xmpp.modules.auth.*
import lib.takina.core.xmpp.modules.sm.StreamManagementModule
import lib.takina.core.xmpp.stanzas.IQ
import lib.takina.core.xmpp.stanzas.IQType
import lib.takina.core.xmpp.stanzas.iq

/**
 * Event fired when resource binding process is finished.
 */
sealed class BindEvent : Event(TYPE) {

	companion object : EventDefinition<BindEvent> {

		override val TYPE = "lib.takina.core.xmpp.modules.BindEvent"
	}

	/**
	 * Bind success.
	 * @param jid bound JID.
	 */
	data class Success(val jid: JID, val inlineProtocol: Boolean) : BindEvent()

	/**
	 * Bind failure.
	 * @param error exception object.
	 */
	data class Failure(val error: Throwable) : BindEvent()

}

@TakinaConfigDsl
interface BindModuleConfig {

	var resource: String?
}

/**
 * Resource bind module. The module is integrated part of XMPP Core protocol.
 */
class BindModule(override val context: AbstractTakina) : XmppModule, InlineProtocol, BindModuleConfig {

	enum class State {

		Unknown, InProgress, Success, Failed
	}

	/**
	 * Resource bind module. The module is integrated part of XMPP Core protocol.
	 */
	companion object : XmppModuleProvider<BindModule, BindModuleConfig> {

		const val BIND2_XMLNS = "urn:xmpp:bind:0"
		const val XMLNS = "urn:ietf:params:xml:ns:xmpp-bind"
		override val TYPE = XMLNS
		override fun configure(module: BindModule, cfg: BindModuleConfig.() -> Unit) {
			module.cfg()
		}

		override fun instance(context: Context): BindModule = BindModule(context as AbstractTakina)
	}

	override val type = TYPE
	override val criteria: Criteria? = null
	override val features = arrayOf(XMLNS)

	@Deprecated("Moved to Context")
	var boundJID: FullJID? by context::boundJID
		internal set

	/**
	 * State of bind process.
	 */
	var state: State by propertySimple(Scope.Session, State.Unknown)
		internal set

	override var resource: String? = null

	/**
	 * Prepare bind request.
	 */
	fun bind(resource: String? = this.resource): RequestBuilder<BindResult, IQ> {
		val stanza = iq {
			type = IQType.Set
			"bind" {
				xmlns = XMLNS
				resource?.let {
					"resource" {
						value = it
					}
				}
			}
		}
		return context.request.iq(stanza).onSend { state = State.InProgress }.map(this::createBindResult).response {
			it.onSuccess { success ->
				bind(success.jid, false)
			}
			it.onFailure {
				state = State.Failed
				context.eventBus.fire(BindEvent.Failure(it))
			}
		}
	}

	private fun createBindResult(element: IQ): BindResult {
		val bind = element.getChildrenNS("bind", XMLNS)!!
		val jidElement = bind.getFirstChild("jid")!!
		val jid = jidElement.value!!.toFullJID()
		return BindResult(jid)
	}

	override fun process(element: Element) {
		throw XMPPException(ErrorCondition.BadRequest)
	}

	private fun bind(jid: FullJID, inlineProtocol: Boolean) {
		state = State.Success
		context.boundJID = jid
		context.eventBus.fire(BindEvent.Success(jid, inlineProtocol))
	}

	data class BindResult(val jid: FullJID)

	override fun featureFor(features: InlineFeatures, stage: InlineProtocolStage): Element? {
		return if (stage == InlineProtocolStage.AfterSasl) {
			val isResumptionAvailable =
				context.getModuleOrNull(StreamManagementModule)?.isResumptionAvailable() ?: false

			if (!isResumptionAvailable) {
				val bindInlineFeatures = features.subInline("bind", BIND2_XMLNS)
				element("bind") {
					xmlns = BIND2_XMLNS
					"tag" { +"Takina" }
					context.modules.getModules().filterIsInstance<InlineProtocol>()
						.mapNotNull { it.featureFor(bindInlineFeatures, InlineProtocolStage.AfterBind) }
						.forEach { addChild(it) }
				}
			} else null
		} else null
	}

	override fun process(response: InlineResponse) {
		response.whenExists(InlineProtocolStage.AfterSasl, "bound", BIND2_XMLNS) { boundElement ->
			bind(response.element.getFirstChild("authorization-identifier")!!.value!!.toFullJID(), true)

			InlineResponse(InlineProtocolStage.AfterBind, boundElement).let { response ->
				context.modules.getModules().filterIsInstance<InlineProtocol>().forEach { consumer ->
					consumer.process(response)
				}
			}

		}
	}

}