package org.atoriapps.takina.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.atoriapps.takina.core.api.createTakina
import org.atoriapps.takina.core.control.ApplyMode
import org.atoriapps.takina.core.feature.ApiProvidingFeature
import org.atoriapps.takina.core.feature.FeatureApi
import org.atoriapps.takina.core.feature.FeatureApiKey
import org.atoriapps.takina.core.feature.FeatureKey
import org.atoriapps.takina.core.feature.TakinaFeatureProvider
import org.atoriapps.takina.core.feature.featureApiKey
import org.atoriapps.takina.core.models.ScopeKind
import org.atoriapps.takina.core.models.toBareJid

class FeatureApiTypingTest {
    interface PingApi : FeatureApi {
        fun ping(): String
    }

    interface OtherApi : FeatureApi

    private class PingFeature : ApiProvidingFeature<PingApi> {
        override val key: FeatureKey = FeatureKey("ping")
        override val supportedScopes: Set<ScopeKind> = setOf(ScopeKind.GLOBAL)
        override val applyMode: ApplyMode = ApplyMode.IMMEDIATE
        override val apiKey: FeatureApiKey<PingApi> = featureApiKey(key)
        override fun api(): PingApi = object : PingApi {
            override fun ping(): String = "pong"
        }

        companion object : TakinaFeatureProvider<PingFeature> {
            override val key: FeatureKey = FeatureKey("ping")
            override val featureType = PingFeature::class
            val apiKey: FeatureApiKey<PingApi> = featureApiKey(key)
            override fun create(): PingFeature = PingFeature()
        }
    }

    @Test
    fun `feature api is retrieved in typed way without cast`() {
        val takina = createTakina {
            addAccount {
                jid = "alice@example.com".toBareJid()
                password = "secret"
            }

            features {
                install(PingFeature)
            }
        }

        val pingApi = takina.api(PingFeature.apiKey)
        assertEquals("pong", pingApi.ping())

        val wrongKey = featureApiKey<OtherApi>(FeatureKey("ping"))
        assertNull(takina.apiOrNull(wrongKey))
    }
}
