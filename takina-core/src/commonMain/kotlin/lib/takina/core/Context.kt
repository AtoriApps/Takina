
package lib.takina.core

import lib.takina.core.configuration.Configuration
import lib.takina.core.eventbus.EventBus
import lib.takina.core.modules.ModulesManager
import lib.takina.core.requests.RequestBuilderFactory
import lib.takina.core.xmpp.FullJID
import lib.takina.core.xmpp.modules.auth.SASLContext

interface Context {

	val eventBus: EventBus

	val config: Configuration

	val writer: PacketWriter

	val modules: ModulesManager

	val request: RequestBuilderFactory

	val authContext: SASLContext

	val boundJID: FullJID?
}