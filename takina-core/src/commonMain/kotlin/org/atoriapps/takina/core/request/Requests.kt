package org.atoriapps.takina.core.request

import org.atoriapps.takina.core.models.BareJid
import org.atoriapps.takina.core.models.TakinaResult
import org.atoriapps.takina.core.utils.FunctionalUtils
import kotlin.time.Clock

data class MessageRequest(
    val from: BareJid?,
    val to: BareJid,
    val body: String,
    val messageId: String = FunctionalUtils.newTraceId("msg")
)

data class PresenceRequest(
    val from: BareJid?,
    val to: BareJid?,
    val show: String? = null,
    val status: String? = null
)

data class IqRequest(
    val from: BareJid?,
    val to: BareJid?,
    val type: String,
    val payload: String,
    val id: String = FunctionalUtils.newTraceId("iq")
)

data class MessageOutcome(
    val id: String,
    val sentAtEpochMillis: Long = Clock.System.now().toEpochMilliseconds()
)

data class PresenceOutcome(
    val sentAtEpochMillis: Long = Clock.System.now().toEpochMilliseconds()
)

data class IqOutcome(
    val id: String,
    val sentAtEpochMillis: Long = Clock.System.now().toEpochMilliseconds()
)

interface RequestExecutor {
    suspend fun sendMessage(request: MessageRequest): TakinaResult<MessageOutcome>
    suspend fun sendPresence(request: PresenceRequest): TakinaResult<PresenceOutcome>
    suspend fun sendIq(request: IqRequest): TakinaResult<IqOutcome>
}

@DslMarker
annotation class RequestDsl

@RequestDsl
class MessageRequestDsl {
    private var fromProvider: (() -> BareJid?)? = null
    private var toProvider: (() -> BareJid?)? = null
    private var bodyProvider: (() -> String)? = null

    var from: BareJid?
        get() = fromProvider?.invoke()
        set(value) {
            fromProvider = { value }
        }

    var to: BareJid?
        get() = toProvider?.invoke()
        set(value) {
            toProvider = { value }
        }

    var body: String
        get() = bodyProvider?.invoke() ?: ""
        set(value) {
            bodyProvider = { value }
        }

    fun from(provider: () -> BareJid?) {
        fromProvider = provider
    }

    fun to(provider: () -> BareJid?) {
        toProvider = provider
    }

    fun body(provider: () -> String) {
        bodyProvider = provider
    }

    fun build(): MessageRequest = MessageRequest(
        from = from,
        to = requireNotNull(to) { "`to` is required for message request" },
        body = body,
    )
}

@RequestDsl
class PresenceRequestDsl {
    private var fromProvider: (() -> BareJid?)? = null
    private var toProvider: (() -> BareJid?)? = null
    private var showProvider: (() -> String?)? = null
    private var statusProvider: (() -> String?)? = null

    var from: BareJid?
        get() = fromProvider?.invoke()
        set(value) {
            fromProvider = { value }
        }

    var to: BareJid?
        get() = toProvider?.invoke()
        set(value) {
            toProvider = { value }
        }

    var show: String?
        get() = showProvider?.invoke()
        set(value) {
            showProvider = { value }
        }

    var status: String?
        get() = statusProvider?.invoke()
        set(value) {
            statusProvider = { value }
        }

    fun from(provider: () -> BareJid?) {
        fromProvider = provider
    }

    fun to(provider: () -> BareJid?) {
        toProvider = provider
    }

    fun show(provider: () -> String?) {
        showProvider = provider
    }

    fun status(provider: () -> String?) {
        statusProvider = provider
    }

    fun build(): PresenceRequest = PresenceRequest(from = from, to = to, show = show, status = status)
}

@RequestDsl
class IqRequestDsl {
    private var fromProvider: (() -> BareJid?)? = null
    private var toProvider: (() -> BareJid?)? = null
    private var typeProvider: (() -> String)? = null
    private var payloadProvider: (() -> String)? = null

    var from: BareJid?
        get() = fromProvider?.invoke()
        set(value) {
            fromProvider = { value }
        }

    var to: BareJid?
        get() = toProvider?.invoke()
        set(value) {
            toProvider = { value }
        }

    var type: String
        get() = typeProvider?.invoke() ?: "get"
        set(value) {
            typeProvider = { value }
        }

    var payload: String
        get() = payloadProvider?.invoke() ?: ""
        set(value) {
            payloadProvider = { value }
        }

    fun from(provider: () -> BareJid?) {
        fromProvider = provider
    }

    fun to(provider: () -> BareJid?) {
        toProvider = provider
    }

    fun type(provider: () -> String) {
        typeProvider = provider
    }

    fun payload(provider: () -> String) {
        payloadProvider = provider
    }

    fun build(): IqRequest = IqRequest(from = from, to = to, type = type, payload = payload)
}

class PendingMessageRequest internal constructor(
    private val request: MessageRequest,
    private val executor: RequestExecutor,
) {
    suspend fun send(): TakinaResult<MessageOutcome> = executor.sendMessage(request)
}

class PendingPresenceRequest internal constructor(
    private val request: PresenceRequest,
    private val executor: RequestExecutor,
) {
    suspend fun send(): TakinaResult<PresenceOutcome> = executor.sendPresence(request)
}

class PendingIqRequest internal constructor(
    private val request: IqRequest,
    private val executor: RequestExecutor,
) {
    suspend fun send(): TakinaResult<IqOutcome> = executor.sendIq(request)
}

open class TakinaRequestApi(
    private val executor: RequestExecutor,
    private val owner: BareJid? = null,
) {
    fun message(init: MessageRequestDsl.() -> Unit): PendingMessageRequest {
        val built = MessageRequestDsl().apply(init).build()
        val fixed = if (built.from == null && owner != null) built.copy(from = owner) else built
        return PendingMessageRequest(fixed, executor)
    }

    fun presence(init: PresenceRequestDsl.() -> Unit): PendingPresenceRequest {
        val built = PresenceRequestDsl().apply(init).build()
        val fixed = if (built.from == null && owner != null) built.copy(from = owner) else built
        return PendingPresenceRequest(fixed, executor)
    }

    fun iq(init: IqRequestDsl.() -> Unit): PendingIqRequest {
        val built = IqRequestDsl().apply(init).build()
        val fixed = if (built.from == null && owner != null) built.copy(from = owner) else built
        return PendingIqRequest(fixed, executor)
    }
}
