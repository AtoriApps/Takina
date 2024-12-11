
package lib.takina.core.xmpp.modules.presence

import lib.takina.DummyTakina
import lib.takina.core.xmpp.stanzas.Presence
import lib.takina.core.xmpp.stanzas.PresenceType
import lib.takina.core.xmpp.stanzas.Show
import lib.takina.core.xmpp.stanzas.presence
import lib.takina.core.xmpp.toBareJID
import lib.takina.core.xmpp.toJID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PresenceModuleTest {

	val takina = DummyTakina().apply {
		connect()
	}

	@Test
	fun testGetAllResources() {
		val module = PresenceModule(takina)
		module.process(presence {
			from = "a@b.c/1".toJID()
			show = Show.Away
		})
		module.process(presence {
			from = "a@b.c/2".toJID()
			show = Show.XA
		})
		module.process(presence {
			from = "a@b.c/3".toJID()
			show = Show.DnD
			priority = 100
		})
		module.process(presence {
			from = "a@b.c/3".toJID()
			show = Show.DnD
			priority = -100
		})

		val list = module.getResources("a@b.c".toBareJID())
		assertEquals(3, list.size)

	}

	@Test
	fun testGetPresenceOf() {
		val module = PresenceModule(takina)

		module.process(presence {
			id()
			from = "a@b.c/1".toJID()
		})
		module.process(presence {
			from = "a@b.c/2".toJID()
		})
		assertEquals(2, module.getResources("a@b.c".toBareJID()).size)
		assertEquals(0, module.getResources("_a@b.c".toBareJID()).size)

		assertNotNull(module.getPresenceOf("a@b.c/1".toJID()))
		assertNotNull(module.getPresenceOf("a@b.c/2".toJID()))
		assertNull(module.getPresenceOf("a@b.c/3".toJID()))

		module.process(presence {
			from = "a@b.c/2".toJID()
			type = PresenceType.Unavailable
		})

		assertEquals(1, module.getResources("a@b.c".toBareJID()).size)
		assertNull(module.getPresenceOf("a@b.c/2".toJID()))

	}

	@Test
	fun testGetPresenceOfBareJid() {
		val module = PresenceModule(takina)

		module.process(presence {
			from = "a@b.c/1".toJID()
			show = Show.Away
		})
		module.process(presence {
			from = "a@b.c/2".toJID()
			show = Show.XA
		})

		assertEquals(Show.Away, module.getBestPresenceOf("a@b.c".toBareJID())!!.show)

		module.process(presence {
			from = "a@b.c/3".toJID()
			show = Show.DnD
			priority = 100
		})

		assertEquals(Show.DnD, module.getBestPresenceOf("a@b.c".toBareJID())!!.show)

		module.process(presence {
			from = "a@b.c/3".toJID()
			show = Show.DnD
			priority = -100
		})

		assertEquals(Show.Away, module.getBestPresenceOf("a@b.c".toBareJID())!!.show)

		module.process(presence {
			from = "a@b.c/1".toJID()
			type = PresenceType.Unavailable
			show = Show.DnD
		})
		assertEquals(Show.XA, module.getBestPresenceOf("a@b.c".toBareJID())!!.show)
	}

	@Test
	fun testTypeAndShow() {
		assertEquals(TypeAndShow.Error, presence {
			type = PresenceType.Error
			show = Show.Chat
		}.typeAndShow())

		assertEquals(TypeAndShow.Chat, presence {
			show = Show.Chat
		}.typeAndShow())

		assertEquals(TypeAndShow.Unknown, presence {
			type = PresenceType.Subscribed
		}.typeAndShow())

		assertEquals(TypeAndShow.Offline, presence {
			type = PresenceType.Unavailable
		}.typeAndShow())

		assertEquals(TypeAndShow.Online, presence { }.typeAndShow())

		val p: Presence? = null
		assertEquals(TypeAndShow.Offline, p.typeAndShow())
	}

}