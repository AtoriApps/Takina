Takina（泷奈）
======

[中文](./README)

# What it is

Takina is an [XMPP](https://xmpp.org) client library written in the [Kotlin](https://kotlinlang.org/) programming
language. It provides implementation of the core XMPP standard and processing XML. Additionally, it supports many popular extensions (XEPs).

This library uses the [Kotlin Multiplatform](https://kotlinlang.org/docs/reference/multiplatform.html) feature to provide an XMPP library for as many platforms as possible. Currently, we are focused on:

* JVM and Android
* JavaScript

This repository contains the source files of the library.

**Takina is now maintained by the Atori Apps team. This project may serve as the foundation for Atori—our Kotlin-based cross-platform chat application—to enable XMPP platform capabilities in the future. We aim to gradually support more XEPs and continue its development.**

# Features

Takina implements support for the following standards:

* [RFC 6120: Extensible Messaging and Presence Protocol (XMPP): Core](https://xmpp.org/rfcs/rfc6120.html)
* [RFC 6121: Extensible Messaging and Presence Protocol (XMPP): Instant Messaging and Presence](https://xmpp.org/rfcs/rfc6121.html).

Takina is under active development, so the list of features changes frequently.

# Quickstart

## Simplest client

Here is an example of the simplest client sending a single message:

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

## Code snippets

There is a set of small examples of Takina library usage. You can find them in the [codeSnippets project](./docs/codeSnippets/).

# Compilation

[Gradle](https://gradle.org/) Build Tool is required to compile the library:

    ./gradlew assemble

Jar files will be stored in `./build/libs/`, JavaScript files in `./build/js/`.

# License

Copyright (c) 2024 Atori Apps.

Licensed under the MIT License.