package org.atoriapps.takina.core

import kotlin.test.Test
import kotlin.test.assertFailsWith
import org.atoriapps.takina.core.api.createTakina
import org.atoriapps.takina.core.control.ApplyMode
import org.atoriapps.takina.core.feature.FeatureKey
import org.atoriapps.takina.core.feature.TakinaFeature
import org.atoriapps.takina.core.feature.TakinaFeatureProvider
import org.atoriapps.takina.core.models.ScopeKind
import org.atoriapps.takina.core.models.toBareJid
import kotlin.test.assertEquals

class FeaturesDslBehaviorTest {
    private class MutableFeature : TakinaFeature {
        override val key: FeatureKey = FeatureKey("mutable")
        override val supportedScopes: Set<ScopeKind> = setOf(ScopeKind.GLOBAL)
        override val applyMode: ApplyMode = ApplyMode.IMMEDIATE

        var configured: Boolean = false

        companion object  : TakinaFeatureProvider<MutableFeature> {
            override val key: FeatureKey = FeatureKey("mutable")
            override val featureType = MutableFeature::class
            var created: MutableFeature? = null
            override fun create(): MutableFeature = MutableFeature().also { created = it }
        }
    }

    @Test
    fun `configure fails when feature is not installed`() {
        MutableFeature.created = null
        assertFailsWith<IllegalArgumentException> {
            createTakina {
                addAccount {
                    jid = "alice@example.com".toBareJid()
                    password = "secret"
                }
                features {
                    configure(MutableFeature) {
                        configured = true
                    }
                }
            }
        }
    }

    @Test
    fun `installAndConfigure installs then configures feature`() {
        MutableFeature.created = null
        createTakina {
            addAccount {
                jid = "alice@example.com".toBareJid()
                password = "secret"
            }
            features {
                installAndConfigure(MutableFeature) {
                    configured = true
                }
            }
        }
        assertEquals(MutableFeature.created?.configured, true)
    }

    @Test
    fun `configure works after install`() {
        MutableFeature.created = null
        createTakina {
            addAccount {
                jid = "alice@example.com".toBareJid()
                password = "secret"
            }
            features {
                install(MutableFeature)
                configure(MutableFeature) {
                    configured = true
                }
            }
        }
        assertEquals(MutableFeature.created?.configured, true)
    }
}
