package org.atoriapps.takina.core

import org.atoriapps.takina.core.components.TakinaComponent
import org.atoriapps.takina.core.components.TakinaComponentProvider
import org.atoriapps.takina.core.components.恩情Component
import org.atoriapps.takina.core.utils.LanguageUtils.clzName
import org.atoriapps.takina.core.utils.LogUtils
import org.atoriapps.takina.core.xmpp.Jid
import kotlin.reflect.KClass

fun createTakina(registerAllComponents: Boolean = true, init: TakinaConfiguration.() -> Unit): Takina {
    val takinaConfiguration = TakinaConfiguration()

    if (registerAllComponents) takinaConfiguration.registerAllComponents()
    else takinaConfiguration.registerCoreComponents()

    takinaConfiguration.init()

    return Takina(takinaConfiguration)
}

@TakinaConfigDsl
class TakinaConfiguration {
    val accountConfigurations = mutableListOf<AccountConfiguration>()
    val componentConfigurationsForAdd = mutableListOf<Any>() // 为什么Any？因为傻逼编译器机制。访问用下面那个
    val componentConfigurations get() = componentConfigurationsForAdd.filterIsInstance<ComponentConfigurationPair<TakinaComponent>>()

    class ComponentConfigurationPair<T : TakinaComponent>(
        val clz: KClass<T>, val config: TakinaConfiguration.ComponentConfiguration<T>
    )

    // 涉及服务器以及安全配置怎么搞，而且是适用于单个账号还是实例全局？

    internal fun registerAllComponents() {
        registerCoreComponents()
    }

    internal fun registerCoreComponents() {
        // registerComponent
        registerComponent(恩情Component)
    }

    fun addAccount(init: AccountConfiguration.() -> Unit) {
        val config = AccountConfiguration().apply(init)
        if (config.jid == null) throw IllegalArgumentException("账号的 JID 不能为空")
        else if (config.passwordProviderCallback == null) throw IllegalArgumentException("账号的密码提供者不能为空")
        else if (accountConfigurations.any { it.jid == config.jid }) throw IllegalStateException("账号 ${config.jid} 已被添加")
        else {
            LogUtils.debug("TakinaConfiguration", "添加账号", config.jid!!)
            accountConfigurations.add(config)
        }
    }

    fun <COMPONENT : TakinaComponent> registerComponent(provider: TakinaComponentProvider<COMPONENT>) {
        val clz = provider.getComponentType()

        if (componentConfigurations.any { it.clz == clz })
            throw IllegalArgumentException("组件 ${clz.clzName} 已被注册")
        else componentConfigurationsForAdd.add(
            ComponentConfigurationPair(
                clz,
                ComponentConfiguration(provider)
            )
        )
    }

    fun <COMPONENT : TakinaComponent> onConfigureComponent(
        provider: TakinaComponentProvider<COMPONENT>,
        init: COMPONENT.(TakinaContext) -> Unit
    ) {
        val clz = provider.getComponentType()

        val configuration =
            componentConfigurations.find { it.clz == clz } as ComponentConfigurationPair<COMPONENT>?
                ?: throw IllegalArgumentException("组件 ${clz.clzName} 尚未注册")

        // 不可取消哦 😊
        configuration.config.configurers.add(init)
    }

    @TakinaConfigDsl
    class AccountConfiguration {
        var jid: Jid? = null

        internal var passwordProviderCallback: (() -> String)? = null

        // 通过回调函数获取密码，这样可以懒加载密码：有必要吗？
        fun password(callback: () -> String) {
            passwordProviderCallback = callback
        }
    }

    @TakinaConfigDsl
    class ComponentConfiguration<COMPONENT : TakinaComponent>(val provider: TakinaComponentProvider<COMPONENT>) {
        var configurers: MutableList<(COMPONENT.(TakinaContext) -> Unit)> = mutableListOf()
    }
}