# Takina

[中文](./README.md)：中文版是最勤更新的

## Introduction

Takina is a [Kotlin](https://kotlinlang.org/)-based [XMPP](https://xmpp.org) client library maintained by the Atori Apps team. It provides a foundational implementation of XMPP core standards and XML processing capabilities

Support for XEP extensions is currently being rolled out. In the future, Takina will serve as the underlying `Xmpp Platform Capability Module` for Atori, powering cross-platform chat and communication features

As an open-source project utilizing the [Kotlin Multiplatform](https://kotlinlang.org/docs/reference/multiplatform.html) stack, Takina aims to cover as many platforms as possible:

* **JVM**
* **JS** (Planned)
* **Android** (Planned; usually compatible via JVM implementation)

## Project Architecture

Takina utilizes a **KMP Layered + Componentized** design. Features are organized as components to avoid logic coupling, making the library easy to extend or prune

### Engineering Layers:

* `takina-core/src/common**`: Platform-agnostic core (protocol models, Request APIs, connection state machines, event models, component abstractions) and test suites
* `takina-core/src/jvm**`: JVM-specific implementations (Socket/TLS, EventBus concurrency, etc.) and test suites
* `takina-examples/src/jvmMain`: Usage examples and smoke test entry points

### Componentization Principles:

* **Core Capabilities (Required)**: Provides the foundation for connection lifecycles, account management, event dispatching, and basic stanza requests. To maintain efficiency, these are not componentized
* **Optional Components (On-demand)**: Handles specific XEPs and advanced features (e.g., MAM, MUC). These can be registered as needed; if not registered, they will not affect the core pipeline

> **Note**: Future protocol features should be developed as optional components unless they contain foundational logic shared across multiple components

### Current Built-in Optional Components:**

`DiscoveryComponent`, `CapabilitiesComponent`, `CarbonsComponent`, `MessageReceiptsComponent`, `StreamManagementComponent`, `RosterComponent`, `MamComponent`, `MucComponent`, `CsiPushComponent`, `HttpUploadComponent`, `ConnectionDiscoveryComponent`, `OmemoComponent`.

## Feature Progress

Our goal is to meet or exceed the [Conversations](https://codeberg.org/iNPUTmice/Conversations) standard in [Compliance Suites](https://xmpp.org/extensions/xep-0479.html)

For protocol implementation progress, capability details, and TODO priorities, please refer to the [Implementation Status](https://www.google.com/search?q=./IMPLEMENTATION_STATUS.md)

If you have suggestions for features or specific RFCs/XEPs that need support, please open an **Issue** to let us know

## Quick Start

Takina’s API is designed with a modern Kotlin DSL. It supports managing multiple account connections within a single instance and allows for a mix of suspending "request-style" APIs and asynchronous event streams

### Basic Connection and Messaging

```kotlin
val demoUserJid = "alice@example.com".toBareJid()

val takina = createTakina {
  // Add an account
  addAccount {
    jid = demoUserJid
    password = "replace-with-real-password" 
      
    // Note: You can also use provider patterns for fields, e.g.: password { "pwd" }
      
    // endpoint is optional; the library will derive it automatically
    endpoint {
      host = "example.com"
      port = 5222
      securityMode = SecurityMode.START_TLS // Default setting
    }
  }
}

// Listen for connection events
takina.events.on(AllConnectedEvent) {
  println("connected: ${it.connectedCount}/${it.configuredCount}")
}

takina.connect(demoUserJid)

// Send a message
takina.request.message {
  from = demoUserJid // Optional if only one account is added
  to = "bob@example.com".toBareJid()
  body = "hello from takina"
}.send()

takina.disconnectAll()
```

### Advanced Usage

For detailed usage of advanced components such as **Message Receipts**, **Stream Management (XEP-0198)**, **Message Carbons (XEP-0280)**, and **OMEMO End-to-End Encryption (XEP-0384)**, please refer to the **[Detailed Usage Guide](./GUIDE.md)**

For the full API reference, please see **[this document](./API_REFERENCE.md)**

## Build and Test

To build the core library and run tests:

```bash
./gradlew :takina-core:allTests :takina-examples:compileKotlinJvm
```

**Running Smoke Tests**:
Supports environment variables to enable specific capability verification (check source code for more parameters):

```bash
TAKINA_JID='alice@example.com' \
TAKINA_PASSWORD='secret' \
./gradlew :takina-examples:runSmokeClientExample
```

More real-world code examples and tests can be found under the `takina-examples` directory

## Open Source & Support

* **Contributing**: If you have improvements or suggestions, please open an Issue for discussion first. Contribution guidelines are currently being drafted
* **Sponsorship**: Official public donation channels are being planned. If you wish to support our development immediately, please let us know via an Issue regarding your preferred sponsorship method (e.g., WeChat, Alipay, PayPal, etc.)

## License

Copyright (c) 2024 - 2026 Atori Apps

This project is currently licensed under the **MIT License**