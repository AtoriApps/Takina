@file:Suppress("NonAsciiCharacters")

package org.atoriapps.takina.core.components

import org.atoriapps.takina.core.TakinaContext
import org.atoriapps.takina.core.utils.LogUtils
import kotlin.reflect.KClass

class 恩情Component : TakinaComponent {
    companion object : TakinaComponentProvider<恩情Component> {
        private const val TAG = "恩情Component"

        override fun getInstance(context: TakinaContext): 恩情Component {
            LogUtils.debug("恩情Component", "将军从丹东来，换我一城雪白，想吃广东菜，获取实例")
            return 恩情Component()
        }

        override fun getComponentType() = 恩情Component::class

        override fun configure(context: TakinaContext, component: 恩情Component) {
            LogUtils.debug("恩情Component", "你的盐我的醋，配置本组件")
            component.喊话内容 = "我们伟大的祖国：人民共和国，万岁！"
        }
    }

    lateinit var 喊话内容: String

    init {
        LogUtils.debug(TAG, "将军的恩情还不完，故初始化本组件")
    }

    fun 喊话() {
        LogUtils.info(TAG, "将军说", 喊话内容)
    }
}