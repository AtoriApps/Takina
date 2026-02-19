package org.atoriapps.takina.core

import org.atoriapps.takina.core.components.DiscoveryComponent
import org.atoriapps.takina.core.components.CapabilitiesComponent
import org.atoriapps.takina.core.components.CarbonsComponent
import org.atoriapps.takina.core.components.MessageReceiptsComponent
import org.atoriapps.takina.core.components.StreamManagementComponent
import org.atoriapps.takina.core.components.TakinaComponent
import org.atoriapps.takina.core.components.TakinaComponentProvider
import org.atoriapps.takina.core.connections.ConnectionConfig
import org.atoriapps.takina.core.connections.SecurityMode
import org.atoriapps.takina.core.connections.defaultPort
import org.atoriapps.takina.core.utils.LanguageUtils.clzName
import org.atoriapps.takina.core.utils.LogUtils
import org.atoriapps.takina.core.xmpp.Jid
import org.atoriapps.takina.core.xmpp.bareJid
import kotlin.reflect.KClass

fun createTakina(registerAllComponents: Boolean = true, init: TakinaConfiguration.() -> Unit): Takina {
    val configuration = TakinaConfiguration()
    if (registerAllComponents) configuration.registerAllComponents() else configuration.registerCoreComponents()
    configuration.init()
    return Takina(configuration)
}

@TakinaConfigDsl
class TakinaConfiguration {
    internal val accountConfigurations = mutableListOf<AccountConfiguration>()
    internal val componentConfigurationsForAdd = mutableListOf<Any>()
    internal val componentConfigurations
        get() = componentConfigurationsForAdd.filterIsInstance<ComponentConfigurationPair<TakinaComponent>>()

    class ComponentConfigurationPair<T : TakinaComponent>(
        val clz: KClass<T>,
        val config: ComponentConfiguration<T>,
    )

    // 组件化的思路

    internal fun registerAllComponents() {
        registerCoreComponents()
        registerOptionalComponents()
    }

    // 核心组件
    internal fun registerCoreComponents() {
        // registerComponent(XxComponent)
    }

    // 可选组件
    internal fun registerOptionalComponents() {
        registerComponent(CapabilitiesComponent)
        registerComponent(CarbonsComponent)
        registerComponent(DiscoveryComponent)
        registerComponent(MessageReceiptsComponent)
        registerComponent(StreamManagementComponent)
    }

    fun addAccount(init: AccountConfiguration.() -> Unit) {
        val config = AccountConfiguration().apply(init)
        val jid = config.jid?.bareJid ?: throw IllegalArgumentException("账号 JID 不能为空")
        config.requirePasswordProvider()
        require(accountConfigurations.none { it.jid?.bareJid == jid }) { "账号 $jid 已被添加" }
        LogUtils.debug("TakinaConfiguration", "添加账号", jid)
        accountConfigurations += config
    }

    fun <COMPONENT : TakinaComponent> registerComponent(provider: TakinaComponentProvider<COMPONENT>) {
        val clz = provider.getComponentType()
        require(componentConfigurations.none { it.clz == clz }) { "组件 ${clz.clzName} 已被注册" }
        componentConfigurationsForAdd += ComponentConfigurationPair(clz, ComponentConfiguration(provider))
    }

    fun <COMPONENT : TakinaComponent> onConfigureComponent(
        provider: TakinaComponentProvider<COMPONENT>,
        init: COMPONENT.(TakinaContext) -> Unit,
    ) {
        val clz = provider.getComponentType()
        val configuration = componentConfigurations.find { it.clz == clz } as ComponentConfigurationPair<COMPONENT>?
            ?: throw IllegalArgumentException("组件 ${clz.clzName} 尚未注册")
        configuration.config.configurers += init
    }

    @TakinaConfigDsl
    class AccountConfiguration {
        var jid: Jid?
            get() = jidProvider?.invoke()
            set(value) {
                jidProvider = { value }
            }

        var host: String?
            get() = hostProvider?.invoke()
            set(value) {
                hostProvider = { value }
            }

        var port: Int?
            get() = portProvider?.invoke()
            set(value) {
                portProvider = { value }
            }

        var securityMode: SecurityMode
            get() = securityModeProvider?.invoke() ?: SecurityMode.START_TLS
            set(value) {
                securityModeProvider = { value }
            }

        var resource: String
            get() = resourceProvider?.invoke() ?: "takina"
            set(value) {
                resourceProvider = { value }
            }

        var connectTimeoutMillis: Int
            get() = connectTimeoutMillisProvider?.invoke() ?: 10_000
            set(value) {
                connectTimeoutMillisProvider = { value }
            }

        var streamLanguage: String
            get() = streamLanguageProvider?.invoke() ?: "en"
            set(value) {
                streamLanguageProvider = { value }
            }

        private var jidProvider: (() -> Jid?)? = null
        private var hostProvider: (() -> String?)? = null
        private var portProvider: (() -> Int?)? = null
        private var securityModeProvider: (() -> SecurityMode)? = { SecurityMode.START_TLS }
        private var resourceProvider: (() -> String)? = { "takina" }
        private var connectTimeoutMillisProvider: (() -> Int)? = { 10_000 }
        private var streamLanguageProvider: (() -> String)? = { "en" }
        private var endpointProvider: (() -> EndpointValues)? = null

        private var passwordProviderCallback: (() -> String)? = null

        fun jid(provider: () -> Jid?) {
            jidProvider = provider
        }

        fun host(provider: () -> String?) {
            hostProvider = provider
        }

        fun port(provider: () -> Int?) {
            portProvider = provider
        }

        fun securityMode(provider: () -> SecurityMode) {
            securityModeProvider = provider
        }

        fun resource(provider: () -> String) {
            resourceProvider = provider
        }

        fun connectTimeoutMillis(provider: () -> Int) {
            connectTimeoutMillisProvider = provider
        }

        fun streamLanguage(provider: () -> String) {
            streamLanguageProvider = provider
        }

        fun password(callback: () -> String) {
            passwordProviderCallback = callback
        }

        fun endpoint(host: String, port: Int? = null, securityMode: SecurityMode = this.securityMode) {
            endpointProvider = {
                EndpointValues(
                    host = host,
                    port = port,
                    securityMode = securityMode,
                )
            }
        }

        fun endpointValues(provider: () -> EndpointValues) {
            endpointProvider = provider
        }

        fun endpoint(init: EndpointBuilder.() -> Unit) {
            val builder = EndpointBuilder().apply(init)
            endpointProvider = {
                EndpointValues(
                    host = builder.host,
                    port = builder.port,
                    securityMode = builder.securityMode,
                )
            }
        }

        internal fun resolveConnectionConfig(): ConnectionConfig {
            val bareJid = jid?.bareJid ?: throw IllegalStateException("账号 JID 不能为空")
            val endpoint = endpointProvider?.invoke()
            val mode = endpoint?.securityMode ?: securityMode
            return ConnectionConfig(
                jid = bareJid,
                host = endpoint?.host ?: host ?: bareJid.domain,
                securityMode = mode,
                port = endpoint?.port ?: port ?: mode.defaultPort,
                resource = resource,
                connectTimeoutMillis = connectTimeoutMillis,
                streamLanguage = streamLanguage,
            )
        }

        internal fun requirePasswordProvider(): () -> String =
            passwordProviderCallback ?: throw IllegalStateException("账号 ${jid?.bareJid ?: "<unknown>"} 的密码提供者不能为空")

        @TakinaConfigDsl
        class EndpointBuilder {
            var host: String?
                get() = hostProvider?.invoke()
                set(value) {
                    hostProvider = { value }
                }

            var port: Int?
                get() = portProvider?.invoke()
                set(value) {
                    portProvider = { value }
                }

            var securityMode: SecurityMode?
                get() = securityModeProvider?.invoke()
                set(value) {
                    securityModeProvider = { value }
                }

            private var hostProvider: (() -> String?)? = null
            private var portProvider: (() -> Int?)? = null
            private var securityModeProvider: (() -> SecurityMode?)? = null

            fun host(provider: () -> String?) {
                hostProvider = provider
            }

            fun port(provider: () -> Int?) {
                portProvider = provider
            }

            fun securityMode(provider: () -> SecurityMode?) {
                securityModeProvider = provider
            }
        }

        data class EndpointValues(
            val host: String? = null,
            val port: Int? = null,
            val securityMode: SecurityMode? = null,
        )
    }

    @TakinaConfigDsl
    class ComponentConfiguration<COMPONENT : TakinaComponent>(val provider: TakinaComponentProvider<COMPONENT>) {
        val configurers: MutableList<COMPONENT.(TakinaContext) -> Unit> = mutableListOf()
    }
}
