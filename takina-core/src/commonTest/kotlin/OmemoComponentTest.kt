package org.atoriapps.takina.core

import org.atoriapps.takina.core.components.OmemoComponent
import org.atoriapps.takina.core.components.OmemoComponent.OmemoProtocolPreference
import org.atoriapps.takina.core.components.OmemoComponent.OmemoProtocolVersion
import org.atoriapps.takina.core.components.OmemoComponent.RemoteOmemoSupport
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
    fun fetchBundle_shouldUseOmemo2BundlesNodeWithItemId() {
        val takina = createTakina(registerAllComponents = false) {
            registerComponent(OmemoComponent)
            addAccount {
                jid = "alice@example.com".toBareJid()
                password = "password"
            }
        }

        val xml = takina.omemo.fetchBundleAwait(owner = "bob@example.com".toBareJid(), deviceId = 31415, from = "alice@example.com".toBareJid()).toXml()
        assertTrue(xml.contains("node='urn:xmpp:omemo:2:bundles'"))
        assertTrue(xml.contains("<item id='31415'/>"))
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
    fun parseBundle_shouldSupportOmemo2ModernNode() {
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
        val xml = """
            <iq type='result' from='bob@example.com' id='b2'>
              <pubsub xmlns='http://jabber.org/protocol/pubsub'>
                <items node='urn:xmpp:omemo:2:bundles'>
                  <item id='${local.deviceId}'>
                    <bundle xmlns='urn:xmpp:omemo:2'>
                      <spk id='${local.signedPreKeyId}'>$spk</spk>
                      <spks>$spks</spks>
                      <ik>$ik</ik>
                      <prekeys>
                        <pk id='1'>$pk1</pk>
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
        assertEquals(OmemoProtocolVersion.V2, parsed.version)
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
        takina.omemo.defaultProtocolPreference = OmemoProtocolPreference.AUTO
        takina.omemo.autoFallbackVersion = OmemoProtocolVersion.V1
        val aliceLocal = takina.omemo.bootstrapLocalDevice(alice)
        val bobLocal = takina.omemo.bootstrapLocalDevice(bob)
        takina.omemo.store.saveRemoteDeviceIds(account = alice, owner = bob, version = OmemoProtocolVersion.V1, deviceIds = listOf(bobLocal.deviceId))
        takina.omemo.store.saveRemoteBundle(account = alice, owner = bob, version = OmemoProtocolVersion.V1, deviceId = bobLocal.deviceId, bundle = bobLocal.toPublicBundle())
        takina.omemo.store.saveRemoteDeviceIds(account = bob, owner = alice, version = OmemoProtocolVersion.V1, deviceIds = listOf(aliceLocal.deviceId))
        takina.omemo.store.saveRemoteBundle(account = bob, owner = alice, version = OmemoProtocolVersion.V1, deviceId = aliceLocal.deviceId, bundle = aliceLocal.toPublicBundle())

        val encrypted = takina.omemo.encryptMessage(from = alice, to = bob, plaintext = "hello-omemo")
        assertTrue(encrypted.toXml().contains("<iv>"))
        val decrypted = takina.omemo.decryptMessage(self = bob, messageXml = encrypted.toXml())
        assertNotNull(decrypted)
        assertEquals("hello-omemo", decrypted.plaintext)
        assertEquals(aliceLocal.deviceId, decrypted.senderDeviceId)
        assertEquals(OmemoProtocolVersion.V1, decrypted.version)
    }

    @Test
    fun autoPreference_shouldChooseV2WhenRemoteSupportsV2() {
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
        takina.omemo.defaultProtocolPreference = OmemoProtocolPreference.AUTO
        takina.omemo.saveRemoteOmemoSupport(alice, bob, RemoteOmemoSupport(supportsV2 = true, supportsV1 = false))
        val aliceLocal = takina.omemo.bootstrapLocalDevice(alice)
        val bobLocal = takina.omemo.bootstrapLocalDevice(bob)
        takina.omemo.store.saveRemoteDeviceIds(account = alice, owner = bob, version = OmemoProtocolVersion.V2, deviceIds = listOf(bobLocal.deviceId))
        takina.omemo.store.saveRemoteBundle(account = alice, owner = bob, version = OmemoProtocolVersion.V2, deviceId = bobLocal.deviceId, bundle = bobLocal.toPublicBundle())
        takina.omemo.store.saveRemoteDeviceIds(account = bob, owner = alice, version = OmemoProtocolVersion.V2, deviceIds = listOf(aliceLocal.deviceId))
        takina.omemo.store.saveRemoteBundle(account = bob, owner = alice, version = OmemoProtocolVersion.V2, deviceId = aliceLocal.deviceId, bundle = aliceLocal.toPublicBundle())

        val encrypted = takina.omemo.encryptMessage(from = alice, to = bob, plaintext = "hello-v2-auto")
        val decrypted = takina.omemo.decryptMessage(self = bob, messageXml = encrypted.toXml())
        assertNotNull(decrypted)
        assertEquals(OmemoProtocolVersion.V2, decrypted.version)
    }

    @Test
    fun autoPreference_shouldPreferV1WhenRemoteSupportsBoth() {
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
        takina.omemo.defaultProtocolPreference = OmemoProtocolPreference.AUTO
        takina.omemo.saveRemoteOmemoSupport(alice, bob, RemoteOmemoSupport(supportsV2 = true, supportsV1 = true))
        val aliceLocal = takina.omemo.bootstrapLocalDevice(alice)
        val bobLocal = takina.omemo.bootstrapLocalDevice(bob)
        takina.omemo.store.saveRemoteDeviceIds(account = alice, owner = bob, version = OmemoProtocolVersion.V1, deviceIds = listOf(bobLocal.deviceId))
        takina.omemo.store.saveRemoteBundle(account = alice, owner = bob, version = OmemoProtocolVersion.V1, deviceId = bobLocal.deviceId, bundle = bobLocal.toPublicBundle())
        takina.omemo.store.saveRemoteDeviceIds(account = bob, owner = alice, version = OmemoProtocolVersion.V1, deviceIds = listOf(aliceLocal.deviceId))
        takina.omemo.store.saveRemoteBundle(account = bob, owner = alice, version = OmemoProtocolVersion.V1, deviceId = aliceLocal.deviceId, bundle = aliceLocal.toPublicBundle())

        val encrypted = takina.omemo.encryptMessage(from = alice, to = bob, plaintext = "prefer-v1")
        val decrypted = takina.omemo.decryptMessage(self = bob, messageXml = encrypted.toXml())
        assertNotNull(decrypted)
        assertEquals(OmemoProtocolVersion.V1, decrypted.version)
    }

    @Test
    fun encrypt_shouldIncludeOwnOtherDevicesInHeaderKeys() {
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
        val aliceOther = takina.omemo.bootstrapLocalDevice("alice-other@example.com".toBareJid()).copy(account = alice)
        val bobLocal = takina.omemo.bootstrapLocalDevice(bob)
        takina.omemo.store.saveRemoteDeviceIds(account = alice, owner = bob, version = OmemoProtocolVersion.V1, deviceIds = listOf(bobLocal.deviceId))
        takina.omemo.store.saveRemoteBundle(account = alice, owner = bob, version = OmemoProtocolVersion.V1, deviceId = bobLocal.deviceId, bundle = bobLocal.toPublicBundle())
        takina.omemo.store.saveRemoteDeviceIds(account = alice, owner = alice, version = OmemoProtocolVersion.V1, deviceIds = listOf(aliceLocal.deviceId, aliceOther.deviceId))
        takina.omemo.store.saveRemoteBundle(account = alice, owner = alice, version = OmemoProtocolVersion.V1, deviceId = aliceOther.deviceId, bundle = aliceOther.toPublicBundle())
        takina.omemo.saveRemoteOmemoSupport(alice, bob, RemoteOmemoSupport(supportsV2 = false, supportsV1 = true))

        val encrypted = takina.omemo.encryptMessage(from = alice, to = bob, plaintext = "multi-device", preference = OmemoProtocolPreference.AUTO)
        val xml = encrypted.toXml()
        assertTrue(xml.contains("rid='${aliceOther.deviceId}'"))
        assertTrue(xml.contains("rid='${bobLocal.deviceId}'"))
    }
}
