
package lib.takina.core.xmpp.modules.mam

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import lib.takina.core.Context
import lib.takina.core.builder.TakinaConfigDsl
import lib.takina.core.eventbus.Event
import lib.takina.core.eventbus.EventDefinition
import lib.takina.core.logger.LoggerFactory
import lib.takina.core.modules.*
import lib.takina.core.parseISO8601
import lib.takina.core.requests.ConsumerPublisher
import lib.takina.core.requests.RequestBuilder
import lib.takina.core.requests.RequestConsumerBuilder
import lib.takina.core.requests.XMPPError
import lib.takina.core.timestampToISO8601
import lib.takina.core.xml.Element
import lib.takina.core.xmpp.*
import lib.takina.core.xmpp.forms.FieldType
import lib.takina.core.xmpp.forms.FormType
import lib.takina.core.xmpp.forms.JabberDataForm
import lib.takina.core.xmpp.modules.RSM
import lib.takina.core.xmpp.stanzas.*
import kotlin.time.Duration.Companion.seconds

data class MAMMessageEvent(
	val resultStanza: Message, val queryId: String, val id: String, val forwardedStanza: ForwardedStanza<Message>,
) : Event(TYPE) {

	companion object : EventDefinition<MAMMessageEvent> {

		override val TYPE = "lib.takina.core.xmpp.modules.mam.MAMMessageEvent"
	}
}

class ForwardedStanza<TYPE : Stanza<*>>(val resultId: String, private val element: Element) : Element by element {

	val timestamp: Instant? by lazy(this::getXmppDelay)

	val stanza: TYPE
		get() = getForwardedStanza()

	private fun getXmppDelay(): Instant? {
		return element.getChildrenNS("delay", "urn:xmpp:delay")?.let {
			it.attributes["stamp"]?.let { stamp -> parseISO8601(stamp) }
		}
	}

	private fun getForwardedStanza(): TYPE {
		val e = element.getFirstChild("message")!!
		return wrap(e)
	}

}

enum class DefaultBehaviour(val xmppValue: String) {

	/**
	 * All messages are archived by default.
	 */
	Always("always"),

	/**
	 * Messages are never archived by default.
	 */
	Never("never"),

	/**
	 * Messages are archived only if the contact's bare JID is in the user's roster.
	 */
	Roster("roster")
}

data class Preferences(val default: DefaultBehaviour, val always: Collection<BareJID>, val never: Collection<BareJID>)

@TakinaConfigDsl
interface MAMModuleConfig
class MAMModule(override val context: Context) : XmppModule, MAMModuleConfig {

	data class Fin(val complete: Boolean = false, val rsm: RSM.Result?)

	private data class RegisteredQuery(
		val queryId: String,
		val createdTimestamp: Instant,
		var validUntil: Instant,
		var publisher: ConsumerPublisher<ForwardedStanza<Message>>? = null,
	)

	companion object : XmppModuleProvider<MAMModule, MAMModuleConfig> {

		const val XMLNS = "urn:xmpp:mam:2"
		override val TYPE = XMLNS
		override fun instance(context: Context): MAMModule = MAMModule(context)

		override fun configure(module: MAMModule, cfg: MAMModuleConfig.() -> Unit) = module.cfg()

		override fun doAfterRegistration(module: MAMModule, moduleManager: ModulesManager) = module.initialize()

	}

	override val type = TYPE
	override val criteria: Criteria =
		Criterion.chain(Criterion.name(Message.NAME), Criterion.nameAndXmlns("result", XMLNS))
	override val features = arrayOf(XMLNS)

	private val requests = ExpiringMap<String, RegisteredQuery>()

	private val log = LoggerFactory.logger("lib.takina.core.xmpp.modules.mam.MAMModule")

	private fun initialize() {
		requests.expirationChecker = {
			it.validUntil < Clock.System.now()
		}
		requests.eventBus = context.eventBus
	}

	override fun process(element: Element) {
		val result = element.getChildrenNS("result", XMLNS) ?: return
		val queryId = result.attributes["queryid"] ?: return
		val query = requests.get(queryId) ?: return
		val resultId = result.attributes["id"] ?: return

		val forwarded = result.getChildrenNS("forwarded", "urn:xmpp:forward:0") ?: return

		forwarded.getFirstChild("message") ?: return

		val forwardedStanza = ForwardedStanza<Message>(resultId, forwarded)
		try {
			query.publisher?.publish(forwardedStanza)
		} catch (e: Exception) {
			log.warning(e) { "Error on calling consumer for ${element.getAsString()}" }
		}
		context.eventBus.fire(MAMMessageEvent(wrap(element), queryId, resultId, forwardedStanza))
	}

	private fun prepareForm(with: String? = null, start: Instant? = null, end: Instant? = null): Element? {
		val form = JabberDataForm.create(FormType.Submit)
		form.addField("FORM_TYPE", FieldType.Hidden).fieldValue = "urn:xmpp:mam:2"

		if (start != null) form.addField("start", FieldType.TextSingle).fieldValue = timestampToISO8601(start)
		if (end != null) form.addField("end", FieldType.TextSingle).fieldValue = timestampToISO8601(end)
		if (with != null) form.addField("with", FieldType.JidSingle).fieldValue = with

		return if (form.element.children.size > 1) form.createSubmitForm() else null
	}

	fun query(
		to: BareJID? = null,
		node: String? = null,
		rsm: RSM.Query? = null,
		with: String? = null,
		start: Instant? = null,
		end: Instant? = null,
	): RequestConsumerBuilder<ForwardedStanza<Message>, Fin, IQ> {
		val queryId = nextUID()
		val form: Element? = prepareForm(with, start, end)
		val stanza = iq {
			if (to != null) this.to = to
			type = IQType.Set
			query(XMLNS) {
				attribute("queryid", queryId)
				if (node != null) attribute("node", node)
				if (form != null) addChild(form)
				if (rsm != null) addChild(rsm.toElement())
			}
		}

		val q = RegisteredQuery(queryId, Clock.System.now(), Clock.System.now() + 30.seconds)
		requests.put(queryId, q)

		val builder =
			RequestConsumerBuilder<ForwardedStanza<Message>, IQ, IQ>(context, stanza) { it as IQ }.map { element ->
				createResponse(element, q)
			}
		q.publisher = builder.publisher

		return builder
	}

	private fun createResponse(responseStanza: Element, registeredQuery: RegisteredQuery): Fin {
		val fin = responseStanza.getChildrenNS("fin", XMLNS)
		registeredQuery.validUntil = Clock.System.now() + 10.seconds
		val rsm: RSM.Result? = fin?.getChildrenNS(RSM.NAME, RSM.XMLNS)?.let { p -> RSM.parseResult(p) }
		return Fin(
			complete = fin?.attributes?.get("complete").toBool(), rsm = rsm
		)
	}

	private fun String?.toBool(): Boolean {
		return when (this) {
			"1", "true" -> true
			else -> false
		}
	}

	private fun parsePreferences(iq: IQ): Preferences {
		val prefs =
			iq.getChildrenNS("prefs", XMLNS) ?: throw XMPPError(iq, ErrorCondition.BadRequest, "No 'prefs' element")
		val always = prefs.getChildren("always").mapNotNull { p -> p.getFirstChild("jid")?.value?.toBareJID() }.toList()
		val never = prefs.getChildren("never").mapNotNull { p -> p.getFirstChild("jid")?.value?.toBareJID() }.toList()
		val b = prefs.attributes["default"]
		val default = DefaultBehaviour.values().find { db -> db.xmppValue == b } ?: throw XMPPException(
			ErrorCondition.BadRequest, "Unknown default value: $b"
		)
		return Preferences(default, always, never)
	}

	fun retrievePreferences(): RequestBuilder<Preferences, IQ> {
		return context.request.iq {
			type = IQType.Get
			"prefs" {
				xmlns = XMLNS
			}
		}.map(this@MAMModule::parsePreferences)
	}

	fun updatePreferences(preferences: Preferences): RequestBuilder<Unit, IQ> {
		return context.request.iq {
			type = IQType.Set
			"prefs" {
				xmlns = XMLNS
				attributes["default"] = preferences.default.xmppValue
				"always" {
					preferences.always.forEach { jid ->
						"jid" { +"$jid" }
					}
				}
				"never" {
					preferences.never.forEach { jid ->
						"jid" { +"$jid" }
					}
				}
			}
		}.map { }
	}

}