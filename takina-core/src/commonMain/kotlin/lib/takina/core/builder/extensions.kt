package lib.takina.core.builder

import lib.takina.core.xmpp.modules.BindModule
import lib.takina.core.xmpp.modules.BindModuleConfig
import lib.takina.core.xmpp.modules.auth.SASL2Module
import lib.takina.core.xmpp.modules.auth.SASL2ModuleConfig
import lib.takina.core.xmpp.modules.auth.SASLModule
import lib.takina.core.xmpp.modules.auth.SASLModuleConfig
import lib.takina.core.xmpp.modules.caps.EntityCapabilitiesModule
import lib.takina.core.xmpp.modules.caps.EntityCapabilitiesModuleConfig
import lib.takina.core.xmpp.modules.discovery.DiscoveryModule
import lib.takina.core.xmpp.modules.discovery.DiscoveryModuleConfiguration
import lib.takina.core.xmpp.modules.presence.PresenceModule
import lib.takina.core.xmpp.modules.presence.PresenceModuleConfig
import lib.takina.core.xmpp.modules.roster.RosterModule
import lib.takina.core.xmpp.modules.roster.RosterModuleConfiguration

fun ConfigurationBuilder.bind(cfg: BindModuleConfig.() -> Unit) =
	this.install(BindModule, configuration = cfg)

fun ConfigurationBuilder.sasl(cfg: SASLModuleConfig.() -> Unit) =
	this.install(SASLModule, configuration = cfg)

fun ConfigurationBuilder.sasl2(cfg: SASL2ModuleConfig.() -> Unit) =
	this.install(SASL2Module, configuration = cfg)

fun ConfigurationBuilder.discovery(cfg: DiscoveryModuleConfiguration.() -> Unit) =
	this.install(DiscoveryModule, configuration = cfg)

fun ConfigurationBuilder.capabilities(cfg: EntityCapabilitiesModuleConfig.() -> Unit) =
	this.install(EntityCapabilitiesModule, configuration = cfg)

fun ConfigurationBuilder.presence(cfg: PresenceModuleConfig.() -> Unit) =
	this.install(PresenceModule, configuration = cfg)

fun ConfigurationBuilder.roster(cfg: RosterModuleConfiguration.() -> Unit) =
	this.install(RosterModule, configuration = cfg)

