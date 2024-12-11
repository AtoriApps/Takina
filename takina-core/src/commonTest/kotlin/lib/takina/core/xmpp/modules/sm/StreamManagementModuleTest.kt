package lib.takina.core.xmpp.modules.sm

import lib.takina.DummyTakina
import lib.takina.core.xml.element
import lib.takina.core.xmpp.modules.auth.SASLModule
import kotlin.test.Test
import kotlin.test.assertEquals

class StreamManagementModuleTest {

	@Test
	fun testProcessAndEvents() {
		val takina = DummyTakina().also {
			it.connect()
		}

		val smm: StreamManagementModule = takina.modules[StreamManagementModule.TYPE]
		smm.withResumptionContext { ctx ->
			ctx.state = StreamManagementModule.State.active
		}

		smm.withResumptionContext { ctx ->
			assertEquals(0, ctx.incomingH)
			assertEquals(0, ctx.outgoingH)
		}
		takina.writeDirectly(element("x") {})
		takina.writeDirectly(element("iq") {})
		smm.withResumptionContext { ctx ->
			assertEquals(1, ctx.outgoingH)
			assertEquals(0, ctx.incomingH)
		}
		takina.writeDirectly(element("iq") {})
		takina.writeDirectly(element("presence") {})
		takina.writeDirectly(element("presence") {})
		takina.writeDirectly(element("presence") {})
		takina.writeDirectly(element("presence") {})
		takina.writeDirectly(element("message") {})
		takina.writeDirectly(element("starttls") { xmlns = "urn:ietf:params:xml:ns:xmpp-tls" })
		takina.writeDirectly(element("auth") { xmlns = SASLModule.XMLNS })
		takina.writeDirectly(element("stream:features") {})
		takina.writeDirectly(element("features") { xmlns = "http://etherx.jabber.org/streams" })
		takina.writeDirectly(element("a") {})
		smm.withResumptionContext { ctx ->
			assertEquals(0, ctx.incomingH)
			assertEquals(7, ctx.outgoingH)
		}

		smm.processElementReceived(element("r") { xmlns = StreamManagementModule.XMLNS })
		smm.processElementReceived(element("a") { xmlns = StreamManagementModule.XMLNS })
		smm.processElementReceived(element("challenge") { xmlns = SASLModule.XMLNS })
		smm.withResumptionContext { ctx ->
			assertEquals(0, ctx.incomingH)
			assertEquals(7, ctx.outgoingH)
		}

		smm.processElementReceived(element("iq") {})
		smm.processElementReceived(element("presence") {})
		smm.processElementReceived(element("presence") {})
		smm.processElementReceived(element("presence") {})
		smm.processElementReceived(element("message") {})
		smm.processElementReceived(element("nothing") {})
		smm.processElementReceived(element("stream:features") {})
		smm.processElementReceived(element("features") {
			xmlns = "http://etherx.jabber.org/streams"
		})
		smm.withResumptionContext { ctx ->
			assertEquals(5, ctx.incomingH)
			assertEquals(7, ctx.outgoingH)
		}

	}

}