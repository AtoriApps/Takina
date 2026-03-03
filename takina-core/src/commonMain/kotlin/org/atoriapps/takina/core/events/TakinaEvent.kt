package org.atoriapps.takina.core.events

import org.atoriapps.takina.core.utils.IdsUtils
import kotlin.reflect.KClass
import kotlin.time.Clock
import kotlin.time.Instant

abstract class TakinaEventProvider<EVENT : TakinaEvent>(val eventClass: KClass<EVENT>)

abstract class TakinaEvent(
     val type: String,
     val eventId: String = IdsUtils.newPrefixedId("evt"),
     val occurredAt: Instant = Clock.System.now()
)