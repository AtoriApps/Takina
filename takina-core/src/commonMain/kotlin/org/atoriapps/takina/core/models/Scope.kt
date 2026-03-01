package org.atoriapps.takina.core.models

enum class ScopeKind {
    PRESET,
    GLOBAL,
    ACCOUNT,
    CONVERSATION,
    MESSAGE,
}

sealed interface Scope {
    val kind: ScopeKind

    data object Preset : Scope {
        override val kind: ScopeKind = ScopeKind.PRESET
    }

    data object Global : Scope {
        override val kind: ScopeKind = ScopeKind.GLOBAL
    }

    data class Account(val owner: BareJid) : Scope {
        override val kind: ScopeKind = ScopeKind.ACCOUNT
    }

    data class Conversation(
        val owner: BareJid,
        val peer: BareJid,
    ) : Scope {
        override val kind: ScopeKind = ScopeKind.CONVERSATION
    }

    data class Message(
        val owner: BareJid,
        val peer: BareJid,
        val messageId: String,
    ) : Scope {
        override val kind: ScopeKind = ScopeKind.MESSAGE
    }
}

fun Scope.priority(): Int = when (this.kind) {
    ScopeKind.MESSAGE -> 5
    ScopeKind.CONVERSATION -> 4
    ScopeKind.ACCOUNT -> 3
    ScopeKind.GLOBAL -> 2
    ScopeKind.PRESET -> 1
}

fun Scope.fallbackChain(): List<Scope> = when (this) {
    Scope.Preset -> listOf(Scope.Preset)
    Scope.Global -> listOf(Scope.Global, Scope.Preset)
    is Scope.Account -> listOf(this, Scope.Global, Scope.Preset)
    is Scope.Conversation -> listOf(this, Scope.Account(owner), Scope.Global, Scope.Preset)
    is Scope.Message -> listOf(this, Scope.Conversation(owner, peer), Scope.Account(owner), Scope.Global, Scope.Preset)
}
