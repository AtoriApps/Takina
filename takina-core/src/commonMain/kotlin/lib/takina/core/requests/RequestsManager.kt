
package lib.takina.core.requests

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import lib.takina.core.logger.LoggerFactory
import lib.takina.core.utils.Lock
import lib.takina.core.xml.Element
import lib.takina.core.xmpp.FullJID
import lib.takina.core.xmpp.getFromAttr
import lib.takina.core.xmpp.stanzas.IQ

class RequestsManager {

    private val log = LoggerFactory.logger("lib.takina.core.requests.RequestsManager")

    private val executor = lib.takina.core.excutor.Executor()

    private val requests = HashMap<String, Request<*, *>>()
    private val lock = Lock();

    fun register(request: Request<*, *>) {
        if (request.stanza.name == IQ.NAME) {
            lock.withLock {
                requests[key(request.stanza)] = request
            }
        }
    }

    var boundJID: FullJID? = null

    private fun key(element: Element): String = "${element.name}:${element.attributes["id"]}"

    fun getRequest(response: Element): Request<*, *>? {
        val id = key(response)

        return lock.withLock {
            val request = requests[id] ?: return@withLock null

            if (verify(request, response)) {
                requests.remove(id)
                return@withLock request
            } else {
                return@withLock null
            }
        }
    }

    private fun verify(entry: Request<*, *>, response: Element): Boolean {
        val jid = response.getFromAttr()
        val bareBoundJID = boundJID?.bareJID

        if (jid == entry.jid) return true
        else if (entry.jid == null && bareBoundJID != null && jid?.bareJID == bareBoundJID) return true

        return false
    }

    fun findAndExecute(response: Element): Boolean {
        val r: Request<*, *> = getRequest(response) ?: return false
        execute { r.setResponseStanza(response) }
        return true
    }

    private fun execute(runnable: () -> Unit) {
        executor.execute {
            try {
                runnable.invoke()
            } catch (e: Throwable) {
                log.warning(e) { "Error on processing response" }
            }
        }
    }

    fun timeoutAll(maxCreationTimestamp: Instant = Instant.DISTANT_FUTURE) {
        log.info { "Timeout all waiting requests" }

        val toTimeout = lock.withLock {
            requests.entries.filter {
                it.value.creationTimestamp < maxCreationTimestamp
            }.map { Pair(it.key, it.value) }
        }
        toTimeout.forEach { (key,value) ->
            lock.withLock {
                requests.remove(key)
            }
            // TODO: somehow this causes exception on iOS... is this thread safe??
            if (!value.isCompleted) {
                execute { value.markTimeout() }
            }
        }
    }

    fun findOutdated() {
        val now = Clock.System.now()
        val toRemove = lock.withLock {
            requests.entries.filter {
                it.value.isCompleted || it.value.creationTimestamp + it.value.timeoutDelay <= now
            }.map { Pair(it.key, it.value) }
        }
        toRemove.forEach { (key, request) ->
            lock.withLock {
                requests.remove(key)
            }
            if (request.creationTimestamp + request.timeoutDelay <= now) {
                execute(request::markTimeout)
            }
        }
    }

    fun getWaitingRequestsSize(): Int = lock.withLock { requests.size }
    fun getRequestsIDs(): String = lock.withLock { requests.values.map { it.id }.joinToString { it } }

}