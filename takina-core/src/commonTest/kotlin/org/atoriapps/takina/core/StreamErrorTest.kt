package org.atoriapps.takina.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.atoriapps.takina.core.connections.XmppStreamErrorCondition
import org.atoriapps.takina.core.connections.parseXmppStreamError

class StreamErrorTest {
    @Test
    fun `stream error enum covers all RFC conditions`() {
        assertEquals(25, XmppStreamErrorCondition.entries.size)
        assertNotNull(XmppStreamErrorCondition.fromLocalNameOrNull("not-authorized"))
        assertNotNull(XmppStreamErrorCondition.fromLocalNameOrNull("unsupported-version"))
    }

    @Test
    fun `parse stream error resolves condition and text`() {
        val parsed = parseXmppStreamError(
            "<stream:error xmlns:stream='http://etherx.jabber.org/streams'>" +
                "<not-authorized xmlns='urn:ietf:params:xml:ns:xmpp-streams'/>" +
                "<text xmlns='urn:ietf:params:xml:ns:xmpp-streams'>bad auth</text>" +
                "</stream:error>"
        )

        assertNotNull(parsed)
        assertEquals(XmppStreamErrorCondition.NOT_AUTHORIZED, parsed.condition)
        assertEquals("bad auth", parsed.text)
        assertTrue(parsed.condition.authHardFailure)
    }

    @Test
    fun `parse stream error ignores non error frames`() {
        assertNull(parseXmppStreamError("<message/>"))
        assertNull(parseXmppStreamError("<stream:error><x/></stream:error>"))
        assertFalse(XmppStreamErrorCondition.INTERNAL_SERVER_ERROR.authHardFailure)
    }
}
