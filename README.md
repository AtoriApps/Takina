# Takina（泷奈）

## Other Languages

[English](./README_EN.md): May be outdated, for the latest information, please refer to this Chinese version.

## 项目信息

![Banner](./TAKINA_BANNER.svg)

### 简介

Takina 由 Atori Apps 团队维护，她是一个使用 [Kotlin](https://kotlinlang.org/) 编写的 [XMPP](https://xmpp.org) 客户端库，提供了对
XMPP 核心标准的实现以及 XML 的处理功能。此外，她还支持许多常用的扩展协议（XEP）。

**Takina 未来可能会作为 Atori（一款跨平台模块化聊天应用）`Xmpp平台能力模块` 的基础，用于支持 XMPP
平台的聊天能力。我们计划逐步支持更多 XEP 并持续开发。**

Takina 是一个采用 [Kotlin Multiplatform](https://kotlinlang.org/docs/reference/multiplatform.html) 项目，旨在为尽可能多的平台提供支持。目前支持以下平台：

* JVM
* JS：计划支持。
* Android：计划支持（正常来说支持JVM就支持Android了，但会不会有什么问题也说不准）。

Takina 目前是开源的，您可以在本存储库访问她的源代码。

### 项目架构

**警告：以下架构图仅供参考，或会随着项目的发展而更改，且部分模块尚未实现。请您多来留意库架构的更改。**

* Core
  * Xml
  * Xmpp

* JVM

* JS

如果您对这个架构有任何意见或建议，都可以发 `Issue` 告诉我们。

## 功能特性

我们的目标是打造 [合规性测试](https://xmpp.org/extensions/xep-0479.html)
中打平甚至超越 [Conversations](https://codeberg.org/iNPUTmice/Conversations)
水准的库和 [客户端](https://github.com/AtoriApps/Atori)。

### Takina 支持以下标准：

* 【[RFC 6120：XMPP核心](https://xmpp.org/rfcs/rfc6120.html)】：暂未实现。
* 【[RFC 6121：XMPP即时消息与状态](https://xmpp.org/rfcs/rfc6121.html)】：暂未实现。

### Takina 支持以下 XEP：

`* 【[XEP 0000：名称](https://xmpp.org/extensions/xep-0000.html)】：格式如此。`

Takina 仍在积极开发中，功能列表会不断更新。如果您对库的功能和支持的 XEP 有任何要求、建议或意见，都可以发 `Issue` 告诉我们。

## 快速上手

### 示例范式

以下将是我们库未来的使用范式：

```kotlin
val demoUserJID = "韩松@king.dprk".toBareJID()

// 使用DSL来配置Takina实例
val takina = createTakina {
  addAccount {
    jid = demoUserJID
    password { "114514" } // 传递一个提供者函数，以便懒加载密码
  }
  
  addAccount {
    jid = "主爱@king.dprk".toBareJID()
    password { "1919810" }
  }

  onConfigureComponent<XxxComponent> {
    // 在这里配置组件
  }
}

// 简单地注册事件监听器
takina.events.on<AllConnectedEvent> {
  LogUtils.info(TAG, "所有账号已连接")
}

// 连接所有未连接的账号
takina.connectAll()

// 简单地发送消息
takina.request.message {
  from = demoUserJID // 指定用来发消息的账户，如果有且仅有一个账号，可以不用填写本字段
  to = "将军@king.dprk".toJID()
  body = "你的盐我的醋，三叔我带着五十万美援来看你了"
}.send()

takina.disconnectAll()
``` 

如果您对这个范式有任何意见或建议，都可以发 `Issue` 告诉我们。

### 其它示例

敬请期待：我们将提供一些 Takina 库用法的小示例。

### 文档

敬请期待：我们将写一套文档。

## 编译

敬请期待：我们暂不提供编译指南，因为我们暂未理顺编译模式。

## 更多信息

### 开发计划

请参见我们官方人员发的 `Issue`。

### 贡献

有意见或建议？发一个 `Issue` 告诉我们。

想提交代码？我们暂未出台 `贡献指南`，敬请期待。

想支持我们？我们很乐意接收您的赞助，敬请期待捐献通道。

## 许可证

版权所有 (c) 2024 Atori Apps。

本项目使用 MIT 许可证。