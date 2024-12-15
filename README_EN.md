# Takina

## 其它语言

[中文](./README.md)：更新及时，建议查看。

## Project Overview

![Takina Logo](./docs/src/restructured/images/logo.svg)

Takina is an [XMPP](https://xmpp.org) client library written in [Kotlin](https://kotlinlang.org/), implementing XMPP core standards and providing XML processing capabilities. It also supports many commonly used extension protocols (XEPs).

The library leverages [Kotlin Multiplatform](https://kotlinlang.org/docs/reference/multiplatform.html) features, aiming to support as many platforms as possible. Currently, the following platforms are supported:

* JVM and Android
* JavaScript: Due to the need to manage a large `node_modules` directory with NPM, we are postponing development until Gradle supports PNPM for building.

This repository currently includes only the source files for the library, meaning it may become closed source in the future, depending on our discretion.

**Takina is maintained by the Atori Apps team. This project may serve as the foundation for Atori (a Kotlin-based cross-platform chat application) in the future, enabling support for XMPP platform capabilities. We plan to incrementally support more XEPs and continue development.**

## Features

**Warning: These features have not been debugged since we took over the project, and their functionality is uncertain unless explicitly stated. Additionally, some features may be incorrectly documented or omitted.**

Our goal is to create a library and [client](https://github.com/AtoriApps/Atori) that perform at the same level as or better than [Conversations](https://codeberg.org/iNPUTmice/Conversations) in [compliance testing](https://xmpp.org/extensions/xep-0479.html).

### Standards Supported by Takina (based on `takina.doap`):

* **[RFC 6120: XMPP Core](https://xmpp.org/rfcs/rfc6120.html)**
* **[RFC 6121: XMPP Instant Messaging and Presence](https://xmpp.org/rfcs/rfc6121.html)**
* **[RFC 7590](https://xmpp.org/rfcs/rfc7590.html):** Unknown purpose; link is inactive.
* **[RFC 7622](https://xmpp.org/rfcs/rfc7622.html):** Unknown purpose; link is inactive.

### Supported XEPs (based on `lib.takina.core.xmpp.modules` and `takina.doap`):

* **[XEP 0004: Data Forms](https://xmpp.org/extensions/xep-0004.html)**
* **[XEP 0030: Service Discovery](https://xmpp.org/extensions/xep-0030.html)**
* **[XEP 0045: Multi-User Chat](https://xmpp.org/extensions/xep-0045.html)**
* **[XEP 0059: Result Set Management](https://xmpp.org/extensions/xep-0059.html)**
* **[XEP 0060: Publish-Subscribe](https://xmpp.org/extensions/xep-0060.html)**
* **[XEP 0077: In-Band Registration](https://xmpp.org/extensions/xep-0077.html)**
* **[XEP 0082: XMPP Date and Time Profiles](https://xmpp.org/extensions/xep-0082.html)**
* **[XEP 0084: User Avatars](https://xmpp.org/extensions/xep-0084.html)**
* **[XEP 0085: Chat State Notifications](https://xmpp.org/extensions/xep-0085.html)**
* **[XEP 0115: Entity Capabilities](https://xmpp.org/extensions/xep-0115.html)**
* **[XEP 0156: Discovering Alternative XMPP Connection Methods](https://xmpp.org/extensions/xep-0156.html)**
* **[XEP 0184: Message Delivery Receipts](https://xmpp.org/extensions/xep-0184.html)**
* **[XEP 0191: Blocking Command](https://xmpp.org/extensions/xep-0191.html)**
* **[XEP 0198: Stream Management](https://xmpp.org/extensions/xep-0198.html)**
* **[XEP 0199: XMPP Ping](https://xmpp.org/extensions/xep-0199.html)**
* **[XEP 0203: Delayed Delivery](https://xmpp.org/extensions/xep-0203.html)**
* **[XEP 0215: External Service Discovery](https://xmpp.org/extensions/xep-0215.html)**
* **[XEP 0237: Roster Versioning](https://xmpp.org/extensions/xep-0237.html)**
* **[XEP 0249: Direct MUC Invitations](https://xmpp.org/extensions/xep-0249.html)**
* **[XEP 0280: Message Carbons](https://xmpp.org/extensions/xep-0280.html)**
* **[XEP 0313: Message Archive Management](https://xmpp.org/extensions/xep-0313.html)**
* **[XEP 0333: Chat Markers](https://xmpp.org/extensions/xep-0333.html)**
* **[XEP 0334: Message Processing Hints](https://xmpp.org/extensions/xep-0334.html)**
* **[XEP 0357: Push Notifications](https://xmpp.org/extensions/xep-0357.html)**
* **[XEP 0359: Unique and Stable Stanza IDs](https://xmpp.org/extensions/xep-0359.html)**
* **[XEP 0363: HTTP File Upload](https://xmpp.org/extensions/xep-0363.html)**
* **[XEP 0369: Mediated Information Exchange](https://xmpp.org/extensions/xep-0369.html)**
* **[XEP 0372: References](https://xmpp.org/extensions/xep-0372.html)**
* **[XEP 0384: OMEMO Encryption](https://xmpp.org/extensions/xep-0384.html)**
* **[XEP 0386: Bind 2](https://xmpp.org/extensions/xep-0386.html)**
* **[XEP 0392: Consistent Color Generation](https://xmpp.org/extensions/xep-0392.html)**
* **[XEP 0440: SASL Channel-Binding Type Capability](https://xmpp.org/extensions/xep-0440.html)**
* **[XEP 0454: OMEMO Media Sharing](https://xmpp.org/extensions/xep-0454.html)**
* **[XEP 0483: HTTP Conference](https://xmpp.org/extensions/xep-0483.html):** Tentative; uncertain if this is the correct XEP.

Other modules and features (e.g., `Auth`, `Commands`, `Jingle`, `vCard`) are either unconfirmed or not clearly mapped to specific XEPs.

Takina is actively under development, and this feature list will be updated regularly.

## Quick Start

### Simplest Client Example

Here is a basic example of a client sending a single message:

```kotlin
val takina = createTakina {
    auth {
        userJID = "client@atoriapps.net".toBareJID()
        password { "secret" }
    }
}
takina.connectAndWait()

takina.request.message {
    to = "romeo@example.net".toJID()
    "body" {
        +"Art thou not Romeo, and a Montague?"
    }
}.send()

takina.disconnect()
```

### Example Code

We provide additional examples demonstrating how to use the Takina library. These can be found in the [Code Snippets](./docs/codeSnippets/README.md).

## Building

Coming Soon: We currently do not provide a build guide, as our build configuration is still under development.

## License

Copyright (c) 2024 Atori Apps.

This project is licensed under the MIT License.