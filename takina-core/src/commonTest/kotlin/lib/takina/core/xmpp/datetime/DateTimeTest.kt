
package lib.takina.core.xmpp.datetime

import kotlinx.datetime.Instant
import lib.takina.core.parseISO8601
import lib.takina.core.timestampToISO8601
import kotlin.test.Test
import kotlin.test.assertEquals

class DateTimeTest {

	@Test
	fun testToISO8601() {
		assertEquals("2020-04-07T14:24:27.423Z", timestampToISO8601(Instant.fromEpochMilliseconds(1586269467423)))
		assertEquals("2020-11-21T13:52:45.000Z", timestampToISO8601(Instant.fromEpochMilliseconds(1605966765000)))
	}

	@Test
	fun testParseISO8601() {
		assertEquals(Instant.fromEpochMilliseconds(1586217600000), parseISO8601("2020-04-07"))
		assertEquals(Instant.fromEpochMilliseconds(1586269467000), parseISO8601("2020-04-07T14:24:27"))
		assertEquals(Instant.fromEpochMilliseconds(1586269467000), parseISO8601("2020-04-07T14:24:27Z"))
		assertEquals(Instant.fromEpochMilliseconds(1586269467423), parseISO8601("2020-04-07T14:24:27.423Z"))
		assertEquals(Instant.fromEpochMilliseconds(1586269467423), parseISO8601("2020-04-07T16:24:27.423+02:00"))
		assertEquals(Instant.fromEpochMilliseconds(1586269467000), parseISO8601("2020-04-07T16:24:27+02:00"))
		assertEquals(1586269467423, parseISO8601("2020-04-07T16:24:27.423+02:00").toEpochMilliseconds())
	}

}