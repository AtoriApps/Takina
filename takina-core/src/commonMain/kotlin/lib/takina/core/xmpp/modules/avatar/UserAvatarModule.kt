
package lib.takina.core.xmpp.modules.avatar

import kotlinx.serialization.Serializable
import lib.takina.core.Context
import lib.takina.core.eventbus.Event
import lib.takina.core.eventbus.EventDefinition
import lib.takina.core.logger.LoggerFactory
import lib.takina.core.modules.Criteria
import lib.takina.core.modules.ModulesManager
import lib.takina.core.modules.XmppModule
import lib.takina.core.modules.XmppModuleProvider
import lib.takina.core.requests.RequestBuilder
import lib.takina.core.xml.Element
import lib.takina.core.xml.element
import lib.takina.core.xmpp.*
import lib.takina.core.xmpp.modules.pubsub.PubSubItemEvent
import lib.takina.core.xmpp.modules.pubsub.PubSubModule
import lib.takina.core.xmpp.stanzas.IQ
import lib.takina.core.xmpp.stanzas.Message

data class UserAvatarUpdatedEvent(val jid: BareJID, val avatarId: String) : Event(TYPE) {

	companion object : EventDefinition<UserAvatarUpdatedEvent> {

		override val TYPE = "lib.takina.core.xmpp.modules.avatar.UserAvatarUpdatedEvent"
	}
}

interface UserAvatarModuleConfig {

	var store: UserAvatarStore

}

class UserAvatarModule(override val context: Context, private val pubSubModule: PubSubModule) : XmppModule,
	UserAvatarModuleConfig {

	companion object : XmppModuleProvider<UserAvatarModule, UserAvatarModuleConfig> {

		override val TYPE = "urn:xmpp:avatar"
		const val XMLNS_DATA = "urn:xmpp:avatar:data"
		const val XMLNS_METADATA = "urn:xmpp:avatar:metadata"
		override fun instance(context: Context): UserAvatarModule =
			UserAvatarModule(context, pubSubModule = context.modules.getModule(PubSubModule))

		override fun configure(module: UserAvatarModule, cfg: UserAvatarModuleConfig.() -> Unit) = module.cfg()

		override fun requiredModules() = listOf(PubSubModule)

		override fun doAfterRegistration(module: UserAvatarModule, moduleManager: ModulesManager) = module.initialize()

	}

	private val log = LoggerFactory.logger("lib.takina.core.xmpp.modules.avatar.UserAvatarModule")

	override val criteria: Criteria? = null
	override val features: Array<String> = arrayOf("$XMLNS_METADATA+notify")
	override val type: String = TYPE

	override var store: UserAvatarStore = object : UserAvatarStore {

		private val items = mutableMapOf<String, Avatar>()

		override fun store(userJID: BareJID, avatarID: String?, data: Avatar?) {
			if (avatarID == null) return
			if (data == null) {
				items.remove(avatarID)
			} else {
				items[avatarID] = data
			}
		}

		override fun load(userJID: BareJID, avatarID: String): Avatar? = items[avatarID]

		override fun isStored(userJID: BareJID, avatarID: String): Boolean = items.containsKey(avatarID)
	}

	private fun initialize() {
		context.eventBus.register(PubSubItemEvent) { event ->
			if (event.nodeName == XMLNS_METADATA && event is PubSubItemEvent.Published) {
				val metadata =
					event.content?.let { if (it.name == "metadata" && it.xmlns == XMLNS_METADATA) it else null }
				if (event.itemId != null && metadata != null) processMetadataItem(event.stanza, metadata)
			}
		}
	}

	private fun parseInfo(info: Element): AvatarInfo {
		return AvatarInfo(
			info.attributes["bytes"]!!.toInt(),
			info.attributes["height"]?.toInt(),
			info.attributes["id"]!!,
			info.attributes["type"]!!,
			info.attributes["url"],
			info.attributes["width"]?.toInt()
		)
	}

	private fun processMetadataItem(stanza: Message, metadata: Element) {
		val userJID = stanza.from?.bareJID ?: return
		val info = metadata.getFirstChild("info")?.let { parseInfo(it) }
		if (info == null) {
			store.store(userJID, null, null)
			return
		}

		val stored = store.isStored(userJID, info.id)
		if (!stored) {
			retrieveAvatar(
				userJID.toString().toJID(), info.id
			).response {
				if (it.isSuccess) {
					log.finest { "Storing UserAvatar data $info.id " + it.getOrNull() }
					val avatar = it.getOrNull()?.let { data ->
						if (data.base64Data == null) {
							null
						} else {
							Avatar(info, data)
						}
					}
					store.store(userJID, info.id, avatar)
					log.fine { "Stored data! $userJID" }
					context.eventBus.fire(UserAvatarUpdatedEvent(userJID, info.id))
				}
			}.send()
		} else {
			context.eventBus.fire(UserAvatarUpdatedEvent(userJID, info.id))
		}
	}

	@Serializable
	data class Avatar(val info: AvatarInfo, val data: AvatarData)

	@Serializable
	data class AvatarData(val id: String, val base64Data: String?)

	@Serializable
	data class AvatarInfo(
		val bytes: Int, val height: Int?, val id: String, val type: String, val url: String?, val width: Int?,
	)

	fun retrieveAvatar(jid: JID, avatarID: String): RequestBuilder<AvatarData, IQ> {
		return pubSubModule.retrieveItem(jid.bareJID, XMLNS_DATA, avatarID).map { response ->
			val item = response.items.first()
			val data = item.content!!.value
			AvatarData(avatarID, data)
		}
	}

	override fun process(element: Element) = throw XMPPException(ErrorCondition.FeatureNotImplemented)

	fun publish(data: AvatarData): RequestBuilder<PubSubModule.PublishingInfo, IQ> {
		val payload = element("data") {
			xmlns = XMLNS_DATA
			+data.base64Data!!
		}
		return pubSubModule.publish(null, XMLNS_DATA, data.id, payload)
	}

	fun publish(data: AvatarInfo): RequestBuilder<PubSubModule.PublishingInfo, IQ> {
		val payload = element("metadata") {
			xmlns = XMLNS_METADATA
			"info" {
				attribute("id", data.id)
				attribute("type", data.type)
				attribute("bytes", "${data.bytes}")
				data.height?.let { attribute("height", "$it") }
				data.width?.let { attribute("width", "$it") }
				data.url?.let { attribute("url", it) }
			}
		}
		return pubSubModule.publish(null, XMLNS_METADATA, data.id, payload)
	}

}
