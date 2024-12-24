@file:Suppress("NonAsciiCharacters")

package org.atoriapps.takina.core.events

import kotlin.reflect.KClass

class 恩情Event : TakinaEvent() {
    companion object : TakinaEventDescriber<恩情Event> {
        override fun getEventTokens(): List<String> =
            listOf("恩情", "忠诚", "将军", "南下", "主体")

        override fun getEventType(): KClass<恩情Event> = 恩情Event::class
    }
}