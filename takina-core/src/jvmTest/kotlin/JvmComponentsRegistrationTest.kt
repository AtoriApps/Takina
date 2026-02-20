package org.atoriapps.takina.core

import org.atoriapps.takina.core.components.ConnectionDiscoveryComponent
import org.atoriapps.takina.core.components.CsiPushComponent
import org.atoriapps.takina.core.components.HttpUploadComponent
import org.atoriapps.takina.core.components.MamComponent
import org.atoriapps.takina.core.components.MucComponent
import org.atoriapps.takina.core.components.RosterComponent
import org.atoriapps.takina.core.xmpp.toBareJid
import kotlin.test.Test
import kotlin.test.assertNotNull

class JvmComponentsRegistrationTest {
    @Test
    fun registerAll_shouldInstallP0ComponentsOnJvm() {
        val takina = createTakina {
            addAccount {
                jid = "alice@example.com".toBareJid()
                password { "password" }
            }
        }

        assertNotNull(takina.findComponent(RosterComponent))
        assertNotNull(takina.findComponent(MamComponent))
        assertNotNull(takina.findComponent(MucComponent))
        assertNotNull(takina.findComponent(CsiPushComponent))
        assertNotNull(takina.findComponent(HttpUploadComponent))
        assertNotNull(takina.findComponent(ConnectionDiscoveryComponent))
    }
}
