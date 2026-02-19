# Takina

## Other Languages

[简体中文](./README.md): This English version may be outdated; for the latest information, please refer to the Chinese version.

## Project Information

![Banner](./TAKINA_BANNER.svg)

### Introduction

Maintained by the Atori Apps team, Takina is an [XMPP](https://xmpp.org) client library written in [Kotlin](https://kotlinlang.org/). It provides a foundational implementation of XMPP core standards and XML processing capabilities. Support for XEPs (XMPP Extension Protocols) is continuously expanding, with a limited set of capabilities currently implemented.

**In the future, Takina may serve as the foundation for Atori's `XMPP Platform Capability Module`, supporting chat features across XMPP platforms.**

Takina is a [Kotlin Multiplatform](https://kotlinlang.org/docs/reference/multiplatform.html) project designed to support as many platforms as possible. Currently, the following platforms are supported:

* **JVM**
* **JS**: Support planned.
* **Android**: Support planned (generally, JVM support implies Android support, but specific issues may arise).

Takina is currently open-source, and you can access the source code in this repository.

### Project Architecture

Takina utilizes a **KMP Layered + Component-based** design:

* `takina-core/src/commonMain`: Platform-independent core capabilities (protocol models, request APIs, connection state machines, event models, component abstractions).
* `takina-core/src/jvmMain`: JVM platform implementation (Socket/TLS, concurrent event bus implementation, etc.).
* `takina-examples`: Usage examples and smoke test entries.

#### Component Design Principles (Mandatory for future development)

Takina's features are organized as components to avoid logic coupling and facilitate expansion or trimming:

* **Core Components (Required)**:
    * Provide the foundation for connection lifecycle, account management, event dispatching, and basic stanza requests.
    * Must be available regardless of whether optional components are registered.
* **Optional Components (On-demand)**:
    * Handle specific XEPs or advanced capabilities (e.g., MAM, Carbons, MUC, Jingle, etc.).
    * Users can choose not to load these without affecting the core link.

The current `createTakina(registerAllComponents = ...)` method reserves a "Core + Optional Components" loading model. New protocol capabilities should default to optional components; only base logic shared across components should enter the core layer.

## Features

Our goal is to build a library and [client](https://github.com/AtoriApps/Atori) that rivals or exceeds [Conversations](https://codeberg.org/iNPUTmice/Conversations) in [Compliance Suites](https://xmpp.org/extensions/xep-0479.html) testing.

### Standards Supported (As of 2026-02-19):

* **[RFC 6120: XMPP Core](https://xmpp.org/rfcs/rfc6120.html)**: **Partial Implementation (JVM)**
    * Stream open/close
    * StartTLS negotiation
    * SASL PLAIN authentication
    * Resource binding
    * Message/Presence/IQ stanza transceiver
* **[RFC 7622: JID Format](https://xmpp.org/rfcs/rfc7622.html)**: **Basic Implementation**
    * JID parsing, validation, and normalization (lowercase domains, etc.)
* **[RFC 6121: IM and Presence](https://xmpp.org/rfcs/rfc6121.html)**: **Partial Implementation**
    * Basic presence/message transceiver
    * Full semantics for roster and subscription workflows are not yet implemented.

### XEP Support Matrix (Roadmap)

**Status Definitions:**
* `Implemented (Basic)`: API available, but may lack advanced semantics/optimization.
* `Partial`: Only a subset of capabilities completed.
* `Planned`: No formal implementation provided yet.

| Protocol | Status | Description |
|---|---|---|
| [XEP-0027 Current Jabber OpenPGP Usage](https://xmpp.org/extensions/xep-0027.html) | Planned | No OpenPGP processing or stanza extensions. |
| [XEP-0030 Service Discovery](https://xmpp.org/extensions/xep-0030.html) | Partial | Supports `disco#info` requests and `await result` via `DiscoveryComponent`; lacks full `disco#items` and caps caching. |
| [XEP-0045 Multi-User Chat](https://xmpp.org/extensions/xep-0045.html) | Planned | No MUC joining, member management, or room event models. |
| [XEP-0048 Bookmarks](https://xmpp.org/extensions/xep-0048.html) | Planned | No bookmark read/write APIs. |
| [XEP-0084 User Avatar](https://xmpp.org/extensions/xep-0084.html) | Planned | No avatar metadata/binary publishing or subscription. |
| [XEP-0115 Entity Capabilities](https://xmpp.org/extensions/xep-0115.html) | Partial | Supports `c` element construction; lacks full hash calculation and validation. |
| [XEP-0163 Personal Eventing Protocol](https://xmpp.org/extensions/xep-0163.html) | Planned | No PEP node management or event routing abstraction. |
| [XEP-0166 Jingle](https://xmpp.org/extensions/xep-0166.html) | Planned | No session negotiation model. |
| [XEP-0184 Message Delivery Receipts](https://xmpp.org/extensions/xep-0184.html) | Planned | No receipt requests or handling. |
| [XEP-0191 Blocking Command](https://xmpp.org/extensions/xep-0191.html) | Planned | No blocklist management API. |
| [XEP-0198 Stream Management](https://xmpp.org/extensions/xep-0198.html) | Planned | No stanza ack, session resumption, or auto-reconnect semantics. |
| [XEP-0234 Jingle File Transfer](https://xmpp.org/extensions/xep-0234.html) | Planned | Depends on Jingle base. |
| [XEP-0237 Roster Versioning](https://xmpp.org/extensions/xep-0237.html) | Planned | Depends on Roster subsystem. |
| [XEP-0245 The /me Command](https://xmpp.org/extensions/xep-0245.html) | Planned | No `/me` specific builder/wrapper. |
| [XEP-0249 Direct MUC Invitations](https://xmpp.org/extensions/xep-0249.html) | Planned | Depends on MUC capabilities. |
| [XEP-0260 Jingle SOCKS5 Bytestreams](https://xmpp.org/extensions/xep-0260.html) | Planned | Depends on Jingle file transfer stack. |
| [XEP-0261 Jingle In-Band Bytestreams](https://xmpp.org/extensions/xep-0261.html) | Planned | Depends on Jingle file transfer stack. |
| [XEP-0280 Message Carbons](https://xmpp.org/extensions/xep-0280.html) | Planned | No enable/disable or multi-device sync wrappers. |
| [XEP-0313 Message Archive Management](https://xmpp.org/extensions/xep-0313.html) | Planned | No query pagination or result set handling APIs. |
| [XEP-0333 Chat Markers](https://xmpp.org/extensions/xep-0333.html) | Planned | No marker sending or state management. |
| [XEP-0352 Client State Indication](https://xmpp.org/extensions/xep-0352.html) | Planned | No CSI active/inactive lifecycle interface. |

### Current Development Status (As of 2026-02-19)

* **Phase**: Basic client kernel available (Early Development).
* **Available**: Multi-account connection management, TLS/PLAIN authentication, basic message/presence/iq transceiver, IQ await (suspending results), event bus, and connection phase events.
* **Verified**: Successfully completes smoke tests (login, presence, disco requests) on local and public servers.
* **Missing**: Key advanced features like MUC, Roster, MAM, Carbons, Stream Management (reconnection), and Jingle/File Transfer.
* **Stability**: APIs are in an iterative phase; breaking changes may occur (synced in README/Release Notes).

Takina is under active development. If you have requirements, suggestions, or feedback regarding features or XEP support, please let us know via `Issues`.

## Quick Start

### API Design

Takina’s current API design goals are:
* Modern Kotlin DSL.
* Multi-connection (one instance manages multiple accounts).
* Layered common abstraction + JVM implementation.
* Coexistence of Request-style APIs + Event streams.

**Core usage pattern:**

```kotlin
import org.atoriapps.takina.core.components.discovery

val demoUserJid = "alice@example.com".toBareJid()

val takina = createTakina {
  addAccount {
    jid = demoUserJid
    password { "replace-with-real-password" }
    // endpoint/resource are optional; they will be inferred automatically
    endpoint {
      host { "example.com" }
      port { 5222 }
      securityMode { SecurityMode.START_TLS }
    }
  }

  addAccount {
    jid { "bot@example.com".toBareJid() } // Fields support lazy loading
    password { "replace-with-real-password" }
  }
}

takina.events.on(AllConnectedEvent) {
  println("Connected: ${it.connectedCount}/${it.configuredCount}")
}

takina.connect(demoUserJid)
takina.request.message {
  from = demoUserJid
  to = "bob@example.com".toBareJid()
  body = "hello from takina"
}.send()

// IQ await API (suspends until result/error)
val disco = takina.discovery().discoInfoAwait(
  from = demoUserJid,
  to = createBareJid(domain = demoUserJid.domain),
  timeoutMillis = 8000
).awaitResult()
println(disco.type)

takina.disconnectAll()
```

### Other Examples

* `/takina-examples/src/jvmMain/kotlin/org/atoriapps/takina/examples/BasicJvmExample.kt`
* `/takina-examples/src/jvmMain/kotlin/org/atoriapps/takina/examples/SmokeClientExample.kt`

Run via Gradle:

```bash
./gradlew :takina-examples:runBasicJvmExample
```

```bash
TAKINA_JID='alice@example.com' \
TAKINA_PASSWORD='secret' \
TAKINA_HOST='example.com' \
TAKINA_PORT='5222' \
TAKINA_SECURITY='START_TLS' \
./gradlew :takina-examples:runSmokeClientExample
```

### Documentation

Documentation is a work in progress. Currently, please refer to the examples and source code comments.

## Compilation

```bash
./gradlew :takina-core:allTests :takina-examples:compileKotlinJvm
```

## More Information

### Roadmap

Please refer to the `Issues` posted by official maintainers.

### Contributing

Have suggestions or feedback? Open an `Issue`.

Want to contribute code? A `Contribution Guide` is coming soon.

Want to support us? We are happy to accept sponsorships. Official donation channels are coming soon. If you wish to donate immediately, please open an `Issue` and let us know your preferred platform (e.g., Alipay, WeChat Pay, Bank Transfer, PayPal, etc.).

## License

Copyright (c) 2024 - 2026 Atori Apps.

This project is currently licensed under the **MIT License**.