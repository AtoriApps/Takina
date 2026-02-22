package org.atoriapps.takina.core

import org.atoriapps.takina.core.components.OmemoComponent
import org.atoriapps.takina.core.components.omemo
import org.atoriapps.takina.core.utils.Base64Codec
import org.atoriapps.takina.core.xmpp.toBareJid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OmemoComponentTest {
    @Test
    fun fetchDeviceList_shouldUseOmemo2DevicesNode() {
        val takina = createTakina(registerAllComponents = false) {
            registerComponent(OmemoComponent)
            addAccount {
                jid = "alice@example.com".toBareJid()
                password = "password"
            }
        }

        val xml = takina.omemo.fetchDeviceListAwait(owner = "bob@example.com".toBareJid(), from = "alice@example.com".toBareJid()).toXml()
        assertTrue(xml.contains("node='urn:xmpp:omemo:2:devices'"))
        assertTrue(xml.contains("to='bob@example.com'"))
    }

    @Test
    fun parseBundle_shouldValidateAndParseOmemo2Fields() {
        val takina = createTakina(registerAllComponents = false) {
            registerComponent(OmemoComponent)
            addAccount {
                jid = "alice@example.com".toBareJid()
                password = "password"
            }
        }

        val local = takina.omemo.bootstrapLocalDevice("bob@example.com".toBareJid())
        val spk = Base64Codec.encode(local.signedPreKeyPublicKey)
        val spks = Base64Codec.encode(local.signedPreKeySignature)
        val ik = Base64Codec.encode(local.identityPublicKey)
        val pk1 = Base64Codec.encode(local.preKeys[0].publicKey)
        val pk2 = Base64Codec.encode(local.preKeys[1].publicKey)
        val xml = """
            <iq type='result' from='bob@example.com' id='b1'>
              <pubsub xmlns='http://jabber.org/protocol/pubsub'>
                <items node='urn:xmpp:omemo:2:bundles:${local.deviceId}'>
                  <item id='current'>
                    <bundle xmlns='urn:xmpp:omemo:2'>
                      <spk id='${local.signedPreKeyId}'>$spk</spk>
                      <spks>$spks</spks>
                      <ik>$ik</ik>
                      <prekeys>
                        <pk id='1'>$pk1</pk>
                        <pk id='2'>$pk2</pk>
                      </prekeys>
                    </bundle>
                  </item>
                </items>
              </pubsub>
            </iq>
        """.trimIndent()

        val parsed = takina.omemo.parseBundleResult(xml)
        assertNotNull(parsed)
        assertEquals(local.deviceId, parsed.deviceId)
        assertEquals(local.signedPreKeyId, parsed.bundle.signedPreKeyId)
        assertEquals(2, parsed.bundle.preKeys.size)
    }

    @Test
    fun encryptAndDecrypt_shouldRoundTripBetweenTwoAccounts() {
        val takina = createTakina(registerAllComponents = false) {
            registerComponent(OmemoComponent)
            addAccount {
                jid = "alice@example.com".toBareJid()
                password = "password"
            }
            addAccount {
                jid = "bob@example.com".toBareJid()
                password = "password"
            }
        }

        val alice = "alice@example.com".toBareJid()
        val bob = "bob@example.com".toBareJid()
        val aliceLocal = takina.omemo.bootstrapLocalDevice(alice)
        val bobLocal = takina.omemo.bootstrapLocalDevice(bob)
        takina.omemo.store.saveRemoteDeviceIds(alice, bob, listOf(bobLocal.deviceId))
        takina.omemo.store.saveRemoteBundle(alice, bob, bobLocal.deviceId, bobLocal.toPublicBundle())
        takina.omemo.store.saveRemoteDeviceIds(bob, alice, listOf(aliceLocal.deviceId))
        takina.omemo.store.saveRemoteBundle(bob, alice, aliceLocal.deviceId, aliceLocal.toPublicBundle())

        val encrypted = takina.omemo.encryptMessage(from = alice, to = bob, plaintext = "hello-omemo")
        val decrypted = takina.omemo.decryptMessage(self = bob, messageXml = encrypted.toXml())
        assertNotNull(decrypted)
        assertEquals("hello-omemo", decrypted.plaintext)
        assertEquals(aliceLocal.deviceId, decrypted.senderDeviceId)
    }
}
