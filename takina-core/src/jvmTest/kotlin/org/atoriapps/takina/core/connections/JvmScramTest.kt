package org.atoriapps.takina.core.connections

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JvmScramTest {
    @Test
    fun `scram sha1 matches rfc5802 known vector`() {
        val clientFirstBare = "n=user,r=fyko+d2lbbFgONRv9qkxdawL"
        val serverFirst = "r=fyko+d2lbbFgONRv9qkxdawL3rfcNHYJY1ZVvWVs7j,s=QSXCR+Q6sek8bf92,i=4096"

        val final = Scram.buildClientFinal(
            mechanism = ScramMechanism.SHA_1,
            password = "pencil",
            clientFirstBare = clientFirstBare,
            serverFirstMessage = serverFirst,
        )

        assertTrue(
            final.fullMessage.endsWith("p=v0X8v3Bz2T0CJGbJQyF0X+HI4Ts="),
            "Unexpected client proof: ${final.fullMessage}",
        )
        assertEquals("rmF9pqV8S7suAoZWja4dJRkFsKQ=", final.expectedServerSignatureBase64)
    }

    @Test
    fun `scram mechanism support check is available`() {
        assertTrue(ScramMechanism.SHA_1.isRuntimeSupported())
    }
}
