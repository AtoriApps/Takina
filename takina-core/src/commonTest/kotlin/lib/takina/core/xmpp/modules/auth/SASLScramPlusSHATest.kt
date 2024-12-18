package lib.takina.core.xmpp.modules.auth

import lib.takina.DummyTakina
import lib.takina.core.builder.createConfiguration
import lib.takina.core.fromBase64
import lib.takina.core.toBase64
import lib.takina.core.xmpp.toBareJID
import kotlin.test.*

class SASLScramPlusSHATest {

    @Test
    fun test_first_message_with_authcid() {
        val scram = createSASLScramPlus(randomGenerator = {
            "fyko+d2lbbFgONRv9qkxdawL"
        })

        val configuration = createConfiguration {
            auth {
                userJID = "user@example.com".toBareJID()
                authenticationName = "differentusername"
                password { "pencil" }
            }
        }.build()
        val context = SASLContext()

        // first client message
        assertEquals(
            "p=tls-unique,a=user@example.com,n=differentusername,r=fyko+d2lbbFgONRv9qkxdawL",
            scram.evaluateChallenge(null, DummyTakina(), configuration, context)!!.fromBase64().decodeToString(),
            "Invalid first client message"
        )

    }

    @Test
    fun test_first_message_with_authcid_equals_localpart() {
        val scram = createSASLScramPlus(randomGenerator = {
            "fyko+d2lbbFgONRv9qkxdawL"
        })

        val configuration = createConfiguration {
            auth {
                userJID = "user@example.com".toBareJID()
                authenticationName = "user"
                password { "pencil" }
            }
        }.build()
        val context = SASLContext()

        // first client message
        assertEquals(
            "p=tls-unique,a=user@example.com,n=user,r=fyko+d2lbbFgONRv9qkxdawL",
            scram.evaluateChallenge(null, DummyTakina(), configuration, context)!!.fromBase64().decodeToString(),
            "Invalid first client message"
        )

    }

    private fun createSASLScramPlus(randomGenerator: () -> String): AbstractSASLScramPlus {
        return object : AbstractSASLScramPlus(name = "SCRAM-SHA-1-PLUS",
            hashAlgorithm = ScramHashAlgorithm.SHA1,
            tlsUniqueProvider = {
                byteArrayOf(
                    'D'.code.toByte(),
                    'P'.code.toByte(),
                    'I'.code.toByte()
                )
            }) {}.apply { conceGenerator = randomGenerator }
    }

    @Test
    fun test_messages_sha1() {
        val scram = createSASLScramPlus(randomGenerator = {
            "SpiXKmhi57DBp5sdE5G3H3ms"
        })

        val configuration = createConfiguration {
            auth {
                userJID = "bmalkow@example.com".toBareJID()
                password { "123456" }
            }
        }.build()
        val context = SASLContext()

        // first client message
        assertEquals(
            "p=tls-unique,,n=bmalkow,r=SpiXKmhi57DBp5sdE5G3H3ms",
            scram.evaluateChallenge(null, DummyTakina(), configuration, context)!!.fromBase64().decodeToString(),
            "Invalid first client message"
        )

        // client last message
        assertEquals(
            "c=cD10bHMtdW5pcXVlLCxEUEk=,r=SpiXKmhi57DBp5sdE5G3H3ms5kLrhitKUHVoSOmzdR,p=+zQvUd4nQqo03thSCcc2K6gueD4=",
            assertNotNull(
                scram.evaluateChallenge(
                    "r=SpiXKmhi57DBp5sdE5G3H3ms5kLrhitKUHVoSOmzdR,s=Ey6OJnGx7JEJAIJp,i=4096".encodeToByteArray()
                        .toBase64(), DummyTakina(), configuration, context
                )
            ).fromBase64().decodeToString(),
            "Invalid last client message"
        )

        assertFalse(context.complete, "It should not be completed yet.")

        assertNull(
            scram.evaluateChallenge(
                "v=NQ/f8FjeMxUuRK9F88G8tMji4pk=".encodeToByteArray().toBase64(), DummyTakina(), configuration, context
            )
        )
        assertTrue(context.complete, "It should be completed.")

    }

}