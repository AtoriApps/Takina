
package lib.takina.core.eventbus

import lib.takina.core.Takina
import lib.takina.core.builder.createConfiguration
import lib.takina.core.eventbus.EventBusInterface.Companion.ALL_EVENTS
import lib.takina.core.xmpp.toBareJID
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.*

class EventBusMultiThreadTest {

	private val takina = Takina(createConfiguration {
		auth {
			userJID = "testuser@tigase.org".toBareJID()
			passwordCallback = { "testuserpassword" }
		}
	})
	val eventBus = EventBus(takina)

	private var working: Boolean = false

	@Test
	@Throws(Exception::class)
	fun testMultiThread() {
		val result0 = ConcurrentLinkedQueue<String>()
		val result1 = ConcurrentLinkedQueue<String>()
		val result2 = ConcurrentLinkedQueue<String>()

		eventBus.register(TestEvent) { }

		eventBus.register<TestEvent>(ALL_EVENTS) { event ->
			try {
				result0.add(event.value)
			} catch (e: Exception) {
				e.printStackTrace()
			}
		}
		eventBus.register(TestEvent) { event ->
			try {
				result1.add(event.value)
			} catch (e: Exception) {
				e.printStackTrace()
			}
		}
		eventBus.register(TestEvent) { event ->
			try {
				result2.add(event.value)
			} catch (e: Exception) {
				e.printStackTrace()
			}
		}

		val threads = mutableListOf<Thread>()
		val ttt = object : EventHandler<TestEvent> {
			@Override
			override fun onEvent(event: TestEvent) {

			}
		}
		val x = object : Thread() {
			@Override
			override fun run() {
				while (working) {
					eventBus.register(TestEvent, ttt)
					eventBus.unregister(TestEvent, ttt)
				}
				println("Stop")
			}
		}

		working = true
		x.start()

		for (i in 0 until THREADS) {
			val t = Thread(Worker("t:$i"))
			t.name = "t:$i"
			threads.add(t)
			t.start()
		}



		while (threads.stream()
				.filter { t -> t.isAlive }
				.count() > 0
		) {
			Thread.sleep(510)
		}
		working = false

		assertEquals(THREADS * EVENTS, result0.size)
		assertEquals(THREADS * EVENTS, result1.size)
		assertEquals(THREADS * EVENTS, result2.size)
	}

	internal class TestEvent(val value: String?) : Event(TYPE) {

		companion object : EventDefinition<TestEvent> {

			override val TYPE = "test:event"
		}

	}

	internal inner class Worker constructor(private val prefix: String) : Runnable {

		override fun run() {
			try {
				for (i in 0 until EVENTS) {
					eventBus.fire(TestEvent(prefix + "_" + i))
				}
			} catch (e: Exception) {
				e.printStackTrace()
				fail(prefix + " :: " + e.message)
			}

		}
	}

	companion object {

		private const val EVENTS = 1000
		private const val THREADS = 1000
	}

}
