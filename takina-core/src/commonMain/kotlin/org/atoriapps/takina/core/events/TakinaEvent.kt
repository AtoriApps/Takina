package org.atoriapps.takina.core.events

import org.atoriapps.takina.core.utils.FunctionalUtils
import kotlin.reflect.KClass
import kotlin.time.Clock
import kotlin.time.Instant

abstract class TakinaEventProvider<EVENT : TakinaEvent>(val eventClass: KClass<EVENT>)

abstract class TakinaEvent(
     val type: String,
     val eventId: String = FunctionalUtils.newTraceId("evt"),
     val occurredAt: Instant = Clock.System.now()
)