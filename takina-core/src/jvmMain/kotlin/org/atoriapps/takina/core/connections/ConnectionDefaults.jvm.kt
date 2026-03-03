package org.atoriapps.takina.core.connections

internal actual fun platformDefaultSaslMechanisms(): List<String> = listOf(
    "SCRAM-SHA-256",
    "SCRAM-SHA-1",
    "DIGEST-MD5",
    "PLAIN",
)
