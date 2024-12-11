package lib.takina.core.builder

import lib.takina.core.Context
import lib.takina.core.modules.XmppModule
import lib.takina.core.modules.XmppModuleProvider
import lib.takina.core.xmpp.modules.PingModule

object Module1 : XmppModuleProvider<PingModule, Any> {

	override val TYPE = "m1"

	override fun instance(context: Context): PingModule {
		TODO("Not yet implemented")
	}

	override fun configure(module: PingModule, cfg: Any.() -> Unit) {}

	override fun requiredModules(): List<XmppModuleProvider<XmppModule, out Any>> = listOf(Module2, Module3)
}

object Module2 : XmppModuleProvider<PingModule, Any> {

	override val TYPE = "m2"

	override fun instance(context: Context): PingModule {
		TODO("Not yet implemented")
	}

	override fun configure(module: PingModule, cfg: Any.() -> Unit) {}

	override fun requiredModules(): List<XmppModuleProvider<XmppModule, out Any>> = listOf(Module3)
}

object Module3 : XmppModuleProvider<PingModule, Any> {

	override val TYPE = "m3"

	override fun instance(context: Context): PingModule {
		TODO("Not yet implemented")
	}

	override fun configure(module: PingModule, cfg: Any.() -> Unit) {}

	override fun requiredModules(): List<XmppModuleProvider<XmppModule, out Any>> = listOf(Module4)
}

object Module4 : XmppModuleProvider<PingModule, Any> {

	override val TYPE = "m4"

	override fun instance(context: Context): PingModule {
		TODO("Not yet implemented")
	}

	override fun configure(module: PingModule, cfg: Any.() -> Unit) {}

}

