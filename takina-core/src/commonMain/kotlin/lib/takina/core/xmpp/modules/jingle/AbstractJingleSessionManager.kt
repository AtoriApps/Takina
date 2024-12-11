
package lib.takina.core.xmpp.modules.jingle

import lib.takina.core.AbstractTakina
import lib.takina.core.Context
import lib.takina.core.eventbus.Event
import lib.takina.core.eventbus.EventDefinition
import lib.takina.core.eventbus.handler
import lib.takina.core.logger.LoggerFactory
import lib.takina.core.utils.Lock
import lib.takina.core.xmpp.*
import lib.takina.core.xmpp.modules.presence.ContactChangeStatusEvent
import lib.takina.core.xmpp.stanzas.PresenceType

abstract class AbstractJingleSessionManager<S : AbstractJingleSession>(
	name: String
) : Jingle.SessionManager {

	abstract fun createSession(context: Context, jid: JID, sid: String, role: Content.Creator, initiationType: InitiationType): S
	abstract fun reportIncomingCall(session: S, media: List<Media>)
	open fun reportIncomingCallAction(session: S, action: MessageInitiationAction) {
	}

	private val log = LoggerFactory.logger(name)

	protected var sessions: List<S> = emptyList()
	private val lock = Lock();
	
	private val contactChangeStatusEventHandler = handler<ContactChangeStatusEvent> { event ->
		if (event.lastReceivedPresence.type == PresenceType.Unavailable) {
			val toClose =
				sessions.filter { it.jid == event.presence.from && it.account == event.context.boundJID?.bareJID }
			toClose.forEach { it.terminate(TerminateReason.Success) }
		}
	}

	abstract fun isDescriptionSupported(descrition: MessageInitiationDescription): Boolean

	fun register(takina: AbstractTakina) {
		takina.eventBus.register(ContactChangeStatusEvent, this.contactChangeStatusEventHandler)
	}

	fun unregister(takina: AbstractTakina) {
		takina.eventBus.unregister(ContactChangeStatusEvent, this.contactChangeStatusEventHandler)
	}

	fun session(context: Context, jid: JID, sid: String?): S? {
		return context.boundJID?.bareJID?.let { account ->
			session(account, jid, sid);
		}
	}

	fun session(account: BareJID, jid: JID, sid: String?): S? =
		lock.withLock {
			sessions.firstOrNull { it.account == account && (sid == null || it.sid == sid) && (it.jid == jid || (it.jid.resource == null && it.jid.bareJID == jid.bareJID)) }
		}

	fun open(
		context: Context,
		jid: JID,
		sid: String,
		role: Content.Creator,
		initiationType: InitiationType,
	): S {
		return lock.withLock {
			val session = this.createSession(context, jid, sid, role, initiationType);
			sessions = sessions + session
			return@withLock session
		}
	}

	fun close(account: BareJID, jid: JID, sid: String): S? = lock.withLock {
		return@withLock session(account, jid, sid)?.let { session ->
			sessions = sessions - session
			return@let session
		}
	}

	fun close(session: AbstractJingleSession) {
		close(session.account, session.jid, session.sid)
	}

	enum class ContentType {
		audio,
		video,
		filetransfer
	}

	enum class Media {
		audio,
		video
	}

	override fun messageInitiation(context: Context, fromJid: JID, action: MessageInitiationAction) {
		when (action) {
			is MessageInitiationAction.Propose -> {
				if (this.session(context, fromJid, action.id) != null) {
					return;
				}
				val session = open(context, fromJid, action.id, Content.Creator.responder, InitiationType.Message);
				reportIncomingCall(session, action.media);
				reportIncomingCallAction(session, action);
			}
			is MessageInitiationAction.Retract -> {
				session(context, fromJid, action.id)?.let {
					reportIncomingCallAction(it, action)
				}
				sessionTerminated(context, fromJid, action.id)
			}
			is MessageInitiationAction.Accept, is MessageInitiationAction.Reject -> {
				session(context, fromJid, action.id)?.let {
					reportIncomingCallAction(it, action)
				}
				sessionTerminated(context.boundJID!!.bareJID, action.id)
			}
			is MessageInitiationAction.Proceed -> {
				val session = session(context, fromJid, action.id) ?: return;
				reportIncomingCallAction(session, action);
				session.accepted(fromJid);
			}
		}
	}

	override fun sessionInitiated(context: Context, jid: JID, sid: String, contents: List<Content>, bundle: List<String>?) {
		val sdp = SDP(contents, bundle ?: emptyList());
		val media = sdp.contents.map { it.description?.media?.let {  Media.valueOf(it)} }.filterNotNull()

		log.finest("calling session initiated for jid: ${jid}, sid: $sid, sdp: $media, bundle: $bundle")

		session(context, jid, sid)?.let { session ->
			log.finest("initiating session that already existed for jid: ${jid}, sid: $sid, sdp: $media, bundle: $bundle")
			session.initiated(contents, bundle)
		} ?: run {
			log.finest("creating an initiating session for jid: ${jid}, sid: $sid, sdp: $media, bundle: $bundle")
			val session = open(context, jid, sid, Content.Creator.responder, InitiationType.Iq);
			session.initiated(contents, bundle)
			reportIncomingCall(session, media);
		}
	}

	@Throws(XMPPException::class)
	override fun sessionAccepted(
		context: Context,
		jid: JID,
		sid: String,
		contents: List<Content>,
		bundle: List<String>?
	) {
		val session = session(context, jid, sid) ?: throw XMPPException(ErrorCondition.ItemNotFound);
		session.accepted(contents, bundle);
	}

	override fun sessionTerminated(context: Context, jid: JID, sid: String) {
		session(context, jid, sid)?.terminated()
	}

	fun sessionTerminated(account: BareJID, sid: String) {
		val toTerminate = lock.withLock {
			return@withLock sessions.filter { it.account == account && it.sid == sid }
		}
		toTerminate.forEach { it.terminated() }
	}

	@Throws(XMPPException::class)
	override fun transportInfo(context: Context, jid: JID, sid: String, contents: List<Content>) {
		val session = session(context, jid, sid) ?: throw XMPPException(ErrorCondition.ItemNotFound);
		for (content in contents) {
			content.transports.flatMap { it.candidates }.forEach { session.addCandidate(it, content.name) }
		}
	}

	protected fun fireIncomingSessionEvent(context: AbstractTakina, session: S, media: List<String>) {
		context.eventBus.fire(JingleIncomingSessionEvent(session, media))
	}
}

class JingleIncomingSessionEvent(val session: AbstractJingleSession, @Suppress("unused") val media: List<String>) :
	Event(TYPE) {

	companion object : EventDefinition<JingleIncomingSessionEvent> {

		override val TYPE = "lib.takina.core.xmpp.modules.jingle.JingleIncomingSession"
	}
}