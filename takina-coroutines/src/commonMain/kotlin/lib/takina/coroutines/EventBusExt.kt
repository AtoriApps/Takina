package lib.takina.coroutines

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import lib.takina.core.eventbus.Event
import lib.takina.core.eventbus.EventBusInterface
import lib.takina.core.eventbus.EventHandler

fun <T : Event> EventBusInterface.asFlow(type: String? = null): Flow<T> = channelFlow {

	val handler = object : EventHandler<T> {
		override fun onEvent(event: T) {
			channel.trySend(event)
		}
	}

	try {
		this@asFlow.register(type ?: EventBusInterface.ALL_EVENTS, handler)
	} catch (e: Throwable) {
		channel.close(e)
	}
	awaitClose {
		unregister(handler)
	}
}.buffer(capacity = Channel.UNLIMITED)
