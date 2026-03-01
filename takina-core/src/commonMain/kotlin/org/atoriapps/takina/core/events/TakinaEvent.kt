package org.atoriapps.takina.core.events

import org.atoriapps.takina.core.utils.FunctionalUtils
import kotlin.reflect.KClass
import kotlin.time.Clock
import kotlin.time.Instant

interface TakinaEvent {
    val eventId: String
    val occurredAt: Instant
    val type: String
}

interface TakinaEventProvider<EVENT : TakinaEvent> {
    val eventClass: KClass<EVENT>
}

abstract class StaticEventProvider<EVENT : TakinaEvent>(
    final override val eventClass: KClass<EVENT>,
) : TakinaEventProvider<EVENT>

open class BasicTakinaEvent(
    override val type: String,
    override val eventId: String = FunctionalUtils.newTraceId("evt"),
    override val occurredAt: Instant = Clock.System.now(),
) : TakinaEvent
