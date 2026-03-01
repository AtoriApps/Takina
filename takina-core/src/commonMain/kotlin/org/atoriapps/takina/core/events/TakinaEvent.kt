package org.atoriapps.takina.core.events

import org.atoriapps.takina.core.models.newId
import kotlin.reflect.KClass
import kotlin.time.Clock
import kotlin.time.Instant

interface TakinaEvent {
    val eventId: String
    val occurredAt: Instant
    val type: String
}

interface TakinaEventType<T : TakinaEvent> {
    val kClass: KClass<T>
}

abstract class StaticEventType<T : TakinaEvent>(
    final override val kClass: KClass<T>,
) : TakinaEventType<T>

open class SimpleTakinaEvent(
    override val type: String,
    override val eventId: String = newId("evt"),
    override val occurredAt: Instant = Clock.System.now(),
) : TakinaEvent
