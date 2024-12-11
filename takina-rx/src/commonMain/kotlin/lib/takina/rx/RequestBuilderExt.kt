package lib.takina.rx

import com.badoo.reaktive.single.Single
import com.badoo.reaktive.single.single
import lib.takina.core.logger.Level
import lib.takina.core.logger.LoggerFactory
import lib.takina.core.requests.RequestBuilder
import lib.takina.core.xmpp.stanzas.Stanza

private val log = LoggerFactory.logger("lib.takina.rx.asSingle")
fun <V, STT : Stanza<*>> RequestBuilder<V, STT>.asSingle(): Single<V> = single<V>(onSubscribe = { emitter ->
    try {
        this@asSingle.response { result ->
            result.onSuccess {
                emitter.onSuccess(it)
            }
            result.onFailure {
                if (log.isLoggable(Level.FINER))
                    log.log(Level.FINER, "Handling error response for request.", it)

                emitter.onError(it)
            }
        }
        this@asSingle.send()
    } catch (e: Throwable) {
        log.warning(e) { "Something goes wrong in RX request processing." }
        emitter.onError(e)
    }
})