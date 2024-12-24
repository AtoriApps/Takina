@file:Suppress("NonAsciiCharacters")

package org.atoriapps.takina.core.components

import org.atoriapps.takina.core.TakinaContext
import kotlin.reflect.KClass

interface TakinaComponent {
}

interface TakinaComponentProvider<COMPONENT : TakinaComponent> {
    fun getInstance(context: TakinaContext): COMPONENT

    // 默认空体实现
    fun configure(context: TakinaContext, component: @UnsafeVariance COMPONENT) {}

    fun getComponentType(): KClass<COMPONENT>
}
