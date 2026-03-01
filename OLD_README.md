# Takina

[English](OLD_README_EN.md): Outdated, for the latest information, please refer to this Chinese version

![Banner](./TAKINA_BANNER.svg)

## 简介

Takina 是由 Atori Apps 团队维护的 [Kotlin](https://kotlinlang.org/) [XMPP](https://xmpp.org) 客户端库，提供了对 XMPP 核心标准的基础实现以及 XML 处理功能。 目前 XEP 扩展支持正在持续落地中，未来她将作为 Atori 的 `Xmpp 平台能力模块` 底座，用于支撑全平台的聊天通讯能力

作为采用 [Kotlin Multiplatform](https://kotlinlang.org/docs/reference/multiplatform.html) 技术栈的开源项目，Takina 旨在覆盖尽可能多的平台：
* JVM
* JS（计划支持）
* Android（计划支持，通常兼容 JVM 即可直接兼容 Android）

## 警告

本项目正在进行一波好！大！重构，目前正处于[方案草拟](./V1_DRAFT.md)阶段，一旦重构，以下的内容将基本不奏效

## 项目架构

Takina 采用 **KMP 分层 + 组件化** 设计，功能以组件形式组织，避免逻辑耦合，便于扩展与裁剪

### 工程分层：
* `takina-core/src/common**`：平台无关的核心（协议模型、请求 API、连接状态机、事件模型、组件抽象）和测试集
* `takina-core/src/jvm**`：JVM 平台的实现（Socket/TLS、事件总线并发实现等）和测试集
* `takina-examples/src/jvmMain`：使用示例与冒烟验证入口

### 组件化原则：

* **核心能力（必需）**：提供连接生命周期、账号管理、事件分发、基础 stanza 请求等底座能力，为保证效率，不打算组件化
* **可选组件（按需）**：承载具体 XEP 与高级能力（如 MAM、MUC 等），可按需注册，未注册不会影响核心链路

> 注：后续新增协议能力应优先作为可选组件开发，仅当属于跨组件共享的底座逻辑时才进入核心层

### 当前内置可选组件：
`DiscoveryComponent`、`CapabilitiesComponent`、`CarbonsComponent`、`MessageReceiptsComponent`、`StreamManagementComponent`、`RosterComponent`、`MamComponent`、`MucComponent`、`CsiPushComponent`、`HttpUploadComponent`、`ConnectionDiscoveryComponent`、`OmemoComponent`

## 功能进度

我们的目标是在 [合规性测试](https://xmpp.org/extensions/xep-0479.html) 中达到甚至超越 [Conversations](https://codeberg.org/iNPUTmice/Conversations) 水准

协议实现进度、各项能力明细与待办优先级，请查阅[实现状况](./IMPLEMENTATION_STATUS.md)

如果您对功能或需要支持的 RFC/XEP 有任何建议，欢迎提交 Issue 告诉我们

## 快速上手

Takina 的 API 设计采用现代 Kotlin DSL，支持单一实例管理多个连接账号，并允许挂起等待（请求式 API）与事件流混合并发

### 基础建立与收发消息

```kotlin
val demoUserJid = "alice@example.com".toBareJid()

val takina = createTakina {
  // 添加账号
  addAccount {
    jid = demoUserJid
    password = "replace-with-real-password" 
      
    // 注：也可以使用提供者模式来设置各字段，如：password { "pwd" }
      
    // endpoint 可缺省，库将自动推导
    endpoint {
      host = "example.com"
      port = 5222
      securityMode = SecurityMode.START_TLS // 这是默认设置
    }
  }
}

// 监听连接事件
takina.events.on(AllConnectedEvent) {
  println("connected: ${it.connectedCount}/${it.configuredCount}")
}

takina.connect(demoUserJid)

// 发送消息
takina.request.message {
  from = demoUserJid // 只有添加了一个账号时，本字段可缺省
  to = "bob@example.com".toBareJid()
  body = "hello from takina"
}.send()

takina.disconnectAll()
```

### 进阶使用

关于 **消息回执**、**流管理 (XEP-0198)**、**消息漫游 (XEP-0280)** 以及 **OMEMO 端到端加密 (XEP-0384)** 等高级组件的详细用法，请参阅**[详细使用指南](OLD_GUIDE.md)**

全部的API参考，请查看[本文档](OLD_API_REFERENCE.md)

## 编译与测试

编译核心库并运行测试：

```bash
./gradlew :takina-core:allTests :takina-examples:compileKotlinJvm
```

**运行冒烟测试**：
支持环境变量组合启用单一能力测试验证（更多测试参数请查看源码）：

```bash
TAKINA_JID='alice@example.com' \
TAKINA_PASSWORD='secret' \
./gradlew :takina-examples:runSmokeClientExample
```

更多真实代码示例与测试可查阅 `takina-examples` 下的代码

## 开源与支持

* **代码贡献**：如果您有改进或建议，欢迎先开 Issue 进行讨论；贡献指南文档正在编写中，敬请期待

* **赞助我们**：官方公共收款通道规划中；如果您愿意马上支持我们的开发进度，可以通过 Issue 告知我们您期望的赞助方式（如微信、支付宝、PayPal 等）

## 许可证

版权所有 (c) 2024 - 2026 Atori Apps
本项目目前采用 **MIT 许可证**