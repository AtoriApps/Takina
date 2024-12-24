@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.atoriapps.takina.core

import org.atoriapps.takina.core.components.TakinaComponent
import org.atoriapps.takina.core.connections.TakinaConnection
import org.atoriapps.takina.core.events.AbstractTakinaEventBus
import org.atoriapps.takina.core.events.TakinaEventBus
import org.atoriapps.takina.core.utils.LanguageUtils.clzName
import org.atoriapps.takina.core.utils.LogUtils
import org.atoriapps.takina.core.xmpp.Jid


abstract class AbstractTakina(val config: TakinaConfiguration) : TakinaContext {
    companion object {
        private const val TAG = "AbstractTakina"
    }

    final override val events = TakinaEventBus(this)

    protected val components = mutableListOf<TakinaComponent>()
    protected val accountConnections = mutableListOf<TakinaConnection>()

    init {
        // 先执行抽象类的构造函数

        LogUtils.debug(TAG, "开始初始化 通用Takina")

        // TODO：这些是否应该在实现类的构造函数中执行？
        LogUtils.debug(TAG, "开始安装和配置组件")
        config.componentConfigurations.forEach { cfg ->
            val clzName = cfg.clz.clzName

            LogUtils.debug(TAG, "正在安装", clzName)
            val component = cfg.config.provider.getInstance(this)

            // 安装后配置前是不是要插入 初始化通用Takina本身 的代码？

            // 但是哈，外面没有configurer怎么办？
            LogUtils.debug(TAG, "正在配置", clzName)
            // 先使用内置配置器
            cfg.config.provider.configure(this, component)
            // 再执行外部配置器
            cfg.config.configurers.forEach {
                it(component, this)
            }
            components.add(component)
        }

        // TODO：请在这里收集 Config 里的账号，以后不要再直接使用 Config

        LogUtils.debug(TAG, "初始化 通用Takina 完成")
    }

    // TODO：到底要不要异步？如果要，Js侧能搞定吗？
    abstract fun connectAll()
    abstract fun connectAllAsync()

    abstract fun disconnectAll()
    abstract fun disconnectAllAsync()

    abstract fun connect(jid: Jid)
}

expect class Takina(cfg: TakinaConfiguration) : AbstractTakina

interface TakinaContext {
    // 暴露给外界以访问EventBus、PackagesBuilder、AccountConnections
    // TODO：谁要访问？
    val events: AbstractTakinaEventBus
}

@DslMarker
annotation class TakinaConfigDsl