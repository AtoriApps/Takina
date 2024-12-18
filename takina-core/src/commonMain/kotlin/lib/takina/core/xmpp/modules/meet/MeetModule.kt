
package lib.takina.core.xmpp.modules.meet

import lib.takina.core.Context
import lib.takina.core.ReflectionModuleManager
import lib.takina.core.builder.TakinaConfigDsl
import lib.takina.core.eventbus.Event
import lib.takina.core.eventbus.EventDefinition
import lib.takina.core.modules.AbstractXmppModule
import lib.takina.core.modules.Criterion
import lib.takina.core.requests.RequestBuilder
import lib.takina.core.utils.Lock
import lib.takina.core.xml.Element
import lib.takina.core.xml.element
import lib.takina.core.xmpp.*
import lib.takina.core.xmpp.modules.discovery.DiscoveryModule
import lib.takina.core.xmpp.modules.jingle.AbstractJingleSessionManager
import lib.takina.core.xmpp.stanzas.*

@TakinaConfigDsl
interface MeetModuleConfig

class MeetModule(context: Context) : AbstractXmppModule(context, XMLNS, emptyArray(), CRITERIA), MeetModuleConfig {
    companion object {
        const val XMLNS = "takina:meet:0"
        val CRITERIA = Criterion.chain(Criterion.or(Criterion.name("iq"), Criterion.name("message")), Criterion.xmlns(XMLNS));
    }
    
    override fun process(element: Element) {
        when (element.name) {
            "iq" -> processIq(element.asStanza());
            "message" -> processMessage(element.asStanza());
        }
    }

    fun processIq(iq: IQ) {
        for (action in iq.getChildrenNS(XMLNS)) {
            val publishers = action.getChildren("publisher").mapNotNull { Publisher.parse(it) };
            if (publishers.isNotEmpty()) {
                when (action.name) {
                    "joined" -> context.eventBus.fire(MeetPublishersEvent.PublishersJoined(publishers));
                    "left" -> context.eventBus.fire(MeetPublishersEvent.PublishersLeft(publishers));
                    else -> throw XMPPException(ErrorCondition.BadRequest);
                }
            }
        }
        context.request.iq {
            to = from
            id(iq.id)
            type = IQType.Result
        }.send()
    }

    fun processMessage(message: Message) {
        val from = message.from ?: return;
        val invitation = MessageInitiationAction.parse(message) ?: throw XMPPException(ErrorCondition.BadRequest);
        context.eventBus.fire(MeetInvitationEvent(from, invitation));
    }

    @OptIn(ReflectionModuleManager::class)
    fun findMeetComponent(callback: (List<DiscoveryModule.Info>)->Unit) {
        val serverJid = context.boundJID!!.domain.toJID()
        context.modules.getModule<DiscoveryModule>().items(serverJid).response {
            val items = it.getOrNull()?.items ?: emptyList();
            resultCollector<DiscoveryModule.Item, DiscoveryModule.Info>(items, transform = { item, callback ->
                context.modules.getModule<DiscoveryModule>().info(item.jid, item.node).response {
                    val result: List<DiscoveryModule.Info> = it.getOrNull()?.let {
                        if (it.features.contains(XMLNS)) {
                            return@let listOf(it)
                        } else {
                            return@let null;
                        }
                    } ?: emptyList();
                    callback(result);

                }.send()
            }, callback)
        }.send()
    }

    fun createMeet(jid: JID, media: List<AbstractJingleSessionManager.Media>, participants: List<BareJID> = emptyList()): RequestBuilder<JID,IQ> {
        return context.request.iq {
            type = IQType.Set
            to = jid

            element("create") {
                xmlns = XMLNS

                for (m in media) {
                    element("media") {
                        attribute("type", m.name)
                    }
                }

                for (participant in participants) {
                    element("participant") {
                        value = participant.toString()
                    }
                }
            }
        }.map { response ->
            val id = response.getChildrenNS("create", XMLNS)!!.attributes["id"];
            "$id@${jid.domain}".toJID()
        }
    }

    fun allowJidsInMeet(jids: List<BareJID>, meetJid: JID): RequestBuilder<Unit,IQ> {
        return context.request.iq {
            type = IQType.Set
            to = meetJid

            element("allow") {
                xmlns = XMLNS
                for (jid in jids) {
                    element("participant") {
                        value = jid.toString()
                    }
                }
            }
        }.map {  }
    }

    fun denyJidsInMeet(jids: List<BareJID>, meetJid: JID): RequestBuilder<Unit,IQ> {
        return context.request.iq {
            type = IQType.Set
            to = meetJid

            element("deny") {
                xmlns = XMLNS
                for (jid in jids) {
                    element("participant") {
                        value = jid.toString()
                    }
                }
            }
        }.map {  }
    }

    fun destroy(meetJid: JID): RequestBuilder<Unit,IQ> {
        return context.request.iq {
            type = IQType.Set
            to = meetJid

            element("destroy") {
                xmlns = XMLNS
            }
        }.map {  }
    }

    fun sendMessageInvitation(action: MeetModule.MessageInitiationAction, jid: JID): RequestBuilder<Unit,Message> {
        when (action) {
            is MessageInitiationAction.Proceed -> sendMessageInvitation(MessageInitiationAction.Accept(action.id), context.boundJID!!.bareJID).send()
            is MessageInitiationAction.Reject -> {
                if (jid.bareJID != context.boundJID?.bareJID) {
                    sendMessageInvitation(MessageInitiationAction.Reject(action.id), context.boundJID!!.bareJID).send();
                }
            }
            else -> {}
        }
        
        return context.request.message {
            type = MessageType.Chat
            to = jid

            addChild(action.toElement())
        }
    }

    private fun <S,T> resultCollector(input: Collection<S>, transform: (S, (Collection<T>)->Unit) -> Unit, callback: (List<T>)->Unit) {
        val lock = Lock();
        var results = mutableListOf<T>()
        var waiting = 0;
        lock.withLock {
            for (item in input) {
                waiting = waiting.inc();
                try {
                    transform(item) {
                        lock.withLock {
                            results.addAll(it);
                            waiting = waiting.dec();
                            if (waiting == 0) {
                                callback.invoke(results);
                            }
                        }
                    }
                } catch (e: Exception) {
                    waiting = waiting.dec();
                }
            }
            if (waiting == 0) {
                callback.invoke(results);
            }
        }
    }

    sealed class MessageInitiationAction(val id: String) {

        companion object {
            fun parse(message: Message): MessageInitiationAction? {
                val action = message.getChildrenNS(XMLNS).firstOrNull() ?: return null;
                val id = action.getIdAttr() ?: return null;
                return when (action.name) {
                    "accept" -> Accept(id)
                    "propose" -> {
                        val meetJid = action.attributes["jid"]?.toJID() ?: return null;
                        val media = action.getChildren("media").mapNotNull { it.attributes["type"] }.map { AbstractJingleSessionManager.Media.valueOf(it) };
                        if (media.isEmpty()) {
                            return null;
                        }
                        return Propose(id, meetJid, media);
                    }
                    "proceed" -> Proceed(id)
                    "retract" -> Retract(id)
                    "reject" -> Reject(id);
                    else -> null
                }
            }
        }

        fun toElement(): Element {
            val name = when(this) {
                is Accept -> "accept"
                is Propose -> "propose"
                is Proceed -> "proceed"
                is Retract -> "retract"
                is Reject -> "reject"
            }
            val that = this;
            
            return element(name) {
                xmlns = XMLNS
                attribute("id", id)

                when (that) {
                    is Propose -> {
                        attribute("jid", that.meetJid.toString())
                        that.media.forEach {
                            element("media") {
                                attribute("type", it.name)
                            }
                        }
                    }
                    else -> {}
                }
            }
        }

        class Propose(id: String, val meetJid: JID, val media: List<AbstractJingleSessionManager.Media>): MessageInitiationAction(id)
        class Proceed(id: String): MessageInitiationAction(id)
        class Accept(id: String): MessageInitiationAction(id)
        class Retract(id: String): MessageInitiationAction(id)
        class Reject(id: String): MessageInitiationAction(id)
    }

    data class Publisher(val jid: BareJID, val streams: List<String>) {
        companion object {
            fun parse(element: Element): Publisher? {
                if (element.name != "publisher") return null;
                val jid = element.attributes["jid"]?.toBareJID() ?: return null;
                return Publisher(jid, element.getChildren("stream").mapNotNull { it.attributes["mid"] });
            }
        }
    }

}

class MeetInvitationEvent(val inviterJid: JID, val action: MeetModule.MessageInitiationAction): Event(TYPE) {
    companion object : EventDefinition<MeetInvitationEvent> {
        override val TYPE = "lib.takina.core.xmpp.modules.meet.MeetInvitationEvent";
    }
}

sealed class MeetPublishersEvent(type: String, val publishers: List<MeetModule.Publisher>): Event(type) {
    class PublishersJoined(publishers: List<MeetModule.Publisher>): MeetPublishersEvent(PublishersJoined.TYPE, publishers) {
        companion object: EventDefinition<PublishersJoined> {
            override val TYPE = "lib.takina.core.xmpp.modules.meet.MeetPublishersJoinedEvent";
        }
    }
    class PublishersLeft(publishers: List<MeetModule.Publisher>): MeetPublishersEvent(PublishersLeft.TYPE, publishers) {
        companion object: EventDefinition<PublishersLeft> {
            override val TYPE = "lib.takina.core.xmpp.modules.meet.MeetPublishersLeftEvent";
        }
    }
}
