package org.atoriapps.takina.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.atoriapps.takina.core.controlling.ApplyMode
import org.atoriapps.takina.core.features.ApiProvidingFeature
import org.atoriapps.takina.core.features.FeatureApi
import org.atoriapps.takina.core.features.TakinaFeatureProvider
import org.atoriapps.takina.core.models.ScopeKind
import org.atoriapps.takina.core.models.toBareJid

class FeatureApiTypingTest {
    interface PingApi : FeatureApi {
        fun ping(): String
    }

    interface OtherApi : FeatureApi

    private class PingFeature : ApiProvidingFeature<PingApi> {
        override val supportedScopes: Set<ScopeKind> = setOf(ScopeKind.GLOBAL)
        override val applyMode: ApplyMode = ApplyMode.IMMEDIATE
        override fun api(): PingApi = object : PingApi {
            override fun ping(): String = "pong"
        }

        companion object : TakinaFeatureProvider<PingFeature> {
            override val id: String = "ping"
            override val featureType = PingFeature::class
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

        val pingApi = takina.api(PingFeature)
        assertEquals("pong", pingApi.ping())

        val notInstalled = object : TakinaFeatureProvider<FakeFeature> {
            override val id: String = "fake"
            override val featureType = FakeFeature::class
            override fun create(): FakeFeature = FakeFeature()
        }
        assertNull(takina.apiOrNull(notInstalled))
    }

    private class FakeFeature : ApiProvidingFeature<OtherApi> {
        override val supportedScopes: Set<ScopeKind> = setOf(ScopeKind.GLOBAL)
        override val applyMode: ApplyMode = ApplyMode.IMMEDIATE
        override fun api(): OtherApi = object : OtherApi {}
    }
}
