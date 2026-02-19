# Takina（泷奈）

## Other Languages

[English](./README_EN.md): May be outdated, for the latest information, please refer to this Chinese version.

## 项目信息

![Banner](./TAKINA_BANNER.svg)

### 简介

Takina 由 Atori Apps 团队维护，她是一个使用 [Kotlin](https://kotlinlang.org/) 编写的 [XMPP](https://xmpp.org) 客户端库，提供了对 XMPP 核心标准的基础实现以及 XML 处理功能。XEP 支持正在持续扩展中，目前仅有少量能力落地。

**Takina 未来可能会作为 Atori `Xmpp平台能力模块` 的基础，用于支持 XMPP 平台的聊天能力。**

Takina 是一个采用 [Kotlin Multiplatform](https://kotlinlang.org/docs/reference/multiplatform.html) 的项目，旨在为尽可能多的平台提供支持。目前支持以下平台：

* JVM
* JS：计划支持。
* Android：计划支持（正常来说支持JVM就支持Android了，但会不会有什么问题也说不准）。

Takina 目前是开源的，您可以在本存储库访问她的源代码。

### 项目架构

Takina 采用 **KMP 分层 + 组件化** 设计：

* `takina-core/src/commonMain`：平台无关核心能力（协议模型、请求 API、连接状态机、事件模型、组件抽象）。
* `takina-core/src/jvmMain`：JVM 平台实现（Socket/TLS、事件总线并发实现等）。
* `takina-examples`：使用示例与冒烟验证入口。

#### 组件化设计原则（未来开发必须遵守）

Takina 的功能以组件形式组织，避免逻辑耦合、便于扩展与裁剪：

* 核心能力组件（必需组件）：
  * 提供连接生命周期、账号管理、事件分发、基础 stanza 请求等底座能力。
  * 无论是否一键注册全部组件，都必须可用。
* 可选组件（按需加载）：
  * 承载具体 XEP/高级能力（例如 MAM、Carbons、MUC、Jingle 等）。
  * 用户可选择不加载，未加载时不影响核心链路。

当前 `createTakina(registerAllComponents = ...)` 已预留“核心 + 可选组件”加载模型。后续新增协议能力时，默认应优先落在可选组件中；只有跨组件共享的底座逻辑才进入核心能力层。

当前内置可选组件（截至 2026-02-19）：

* `DiscoveryComponent`：XEP-0030（disco#info）与基础软件版本请求封装。
* `CapabilitiesComponent`：XEP-0115 caps 节点构造与 presence 载荷解析。
* `CarbonsComponent`：XEP-0280 Carbons 启用/关闭与转发消息解析。
* `MessageReceiptsComponent`：XEP-0184 回执请求/回执确认载荷构造、消息解析与自动回执策略。
* `StreamManagementComponent`：XEP-0198 基础模型（enable/resume/a/r 构造、帧解析、计数状态跟踪、自动确认请求与重连恢复基础流程）。

## 功能特性

我们的目标是打造 [合规性测试](https://xmpp.org/extensions/xep-0479.html) 中打平甚至超越 [Conversations](https://codeberg.org/iNPUTmice/Conversations) 水准的库和 [客户端](https://github.com/AtoriApps/Atori)。

### Takina 支持以下标准（截至 2026-02-19）：

* 【[RFC 6120：XMPP 核心](https://xmpp.org/rfcs/rfc6120.html)】：**部分实现（JVM）**
  * stream open/close
  * StartTLS 协商
  * SASL PLAIN 认证
  * resource bind
  * message/presence/iq stanza 收发
* 【[RFC 7622：JID 格式](https://xmpp.org/rfcs/rfc7622.html)】：**基础实现**
  * JID 解析、校验、规范化（domain 小写等）
* 【[RFC 6121：IM 与 Presence](https://xmpp.org/rfcs/rfc6121.html)】：**部分实现**
  * 基础 presence/message 收发
  * 暂未实现 roster、订阅流程等完整语义

### XEP 支持矩阵（目标清单）

状态定义：

* `已实现（基础）`：已有可用 API，但仍可能缺少高级语义/优化。
* `部分实现`：只完成子集能力。
* `未实现（待办）`：尚未提供正式实现。

| 协议 | 状态 | 说明                                                                                                                                                                                                                                          |
|---|---|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [XEP-0027 Current Jabber OpenPGP Usage](https://xmpp.org/extensions/xep-0027.html) | 未实现（待办） | 尚无 OpenPGP 处理与相关 stanza 扩展。                                                                                                                                                                                                                 |
| [XEP-0030 Service Discovery](https://xmpp.org/extensions/xep-0030.html) | 部分实现 | 已通过 `DiscoveryComponent` 支持 `disco#info` 请求与 `await result`；未完整覆盖 `disco#items`、能力缓存等。                                                                                                                                                      |
| [XEP-0045 Multi-User Chat](https://xmpp.org/extensions/xep-0045.html) | 未实现（待办） | 尚无 MUC 加入、成员管理、房间事件模型。                                                                                                                                                                                                                      |
| [XEP-0048 Bookmarks](https://xmpp.org/extensions/xep-0048.html) | 未实现（待办） | 尚无书签读写 API。                                                                                                                                                                                                                                 |
| [XEP-0084 User Avatar](https://xmpp.org/extensions/xep-0084.html) | 未实现（待办） | 尚无头像元数据/二进制发布与订阅。                                                                                                                                                                                                                           |
| [XEP-0115 Entity Capabilities](https://xmpp.org/extensions/xep-0115.html) | 部分实现 | 已通过 `CapabilitiesComponent` 支持 `c` 元素构造与 presence 中 caps 解析；未实现完整能力哈希计算与校验流程。                                                                                                                                                               |
| [XEP-0163 Personal Eventing Protocol](https://xmpp.org/extensions/xep-0163.html) | 未实现（待办） | 尚无 PEP 节点管理与事件路由抽象。                                                                                                                                                                                                                         |
| [XEP-0166 Jingle](https://xmpp.org/extensions/xep-0166.html) | 未实现（待办） | 尚无会话协商模型。                                                                                                                                                                                                                                   |
| [XEP-0184 Message Delivery Receipts](https://xmpp.org/extensions/xep-0184.html) | 部分实现 | 已通过 `MessageReceiptsComponent` 提供 request/received 载荷构造、消息 XML 解析、自动回执（默认开启）与手动回执 API（`sendReceived` / `sendReceivedReply`）；尚未支持更细粒度策略（如白名单/会话级开关）。                                                                                         |
| [XEP-0191 Blocking Command](https://xmpp.org/extensions/xep-0191.html) | 未实现（待办） | 尚无阻止名单管理 API。                                                                                                                                                                                                                               |
| [XEP-0198 Stream Management](https://xmpp.org/extensions/xep-0198.html) | 部分实现 | 已通过 `StreamManagementComponent` 提供 enable/resume/a/r 构造、SM 帧解析、计数状态跟踪；在服务端宣告 `urn:xmpp:sm:3` 时自动协商（优先 `resume`，失败回退 `enable`），收到 `<r/>` 自动回 `<a/>`，并支持按出站计数自动发送 `<r/>` 向服务端请求确认，具备未确认消息重放与基础自动重连流程；初步实现协作式会话持久化与跨进程恢复，未来将考虑提供更严格的故障场景一致性保证。 |
| [XEP-0234 Jingle File Transfer](https://xmpp.org/extensions/xep-0234.html) | 未实现（待办） | 依赖 Jingle 基础能力。                                                                                                                                                                                                                             |
| [XEP-0237 Roster Versioning](https://xmpp.org/extensions/xep-0237.html) | 未实现（待办） | 依赖 roster 子系统。                                                                                                                                                                                                                              |
| [XEP-0245 The /me Command](https://xmpp.org/extensions/xep-0245.html) | 未实现（待办） | 尚无 `/me` 专用 builder/语义封装。                                                                                                                                                                                                                   |
| [XEP-0249 Direct MUC Invitations](https://xmpp.org/extensions/xep-0249.html) | 未实现（待办） | 依赖 MUC 能力。                                                                                                                                                                                                                                  |
| [XEP-0260 Jingle SOCKS5 Bytestreams Transport Method](https://xmpp.org/extensions/xep-0260.html) | 未实现（待办） | 依赖 Jingle 文件传输栈。                                                                                                                                                                                                                            |
| [XEP-0261 Jingle In-Band Bytestreams Transport Method](https://xmpp.org/extensions/xep-0261.html) | 未实现（待办） | 依赖 Jingle 文件传输栈。                                                                                                                                                                                                                            |
| [XEP-0280 Message Carbons](https://xmpp.org/extensions/xep-0280.html) | 部分实现 | 已通过 `CarbonsComponent` 提供 enable/disable IQ API（支持 await result）与转发消息封装解析（`sent`/`received` + `forwarded`）；支持连接后自动发送 enable。                                                                                                                |
| [XEP-0313 Message Archive Management](https://xmpp.org/extensions/xep-0313.html) | 未实现（待办） | 尚无查询分页、结果集处理 API。                                                                                                                                                                                                                           |
| [XEP-0333 Chat Markers](https://xmpp.org/extensions/xep-0333.html) | 未实现（待办） | 尚无 marker 发送与状态管理。                                                                                                                                                                                                                          |
| [XEP-0352 Client State Indication](https://xmpp.org/extensions/xep-0352.html) | 未实现（待办） | 尚无 CSI active/inactive 生命周期接口。                                                                                                                                                                                                              |

### 当前库开发状态（截至 2026-02-19）

* 阶段：**基础客户端内核可用（早期开发阶段）**。
* 已具备：多账号连接管理、TLS/PLAIN 基础认证链路、message/presence/iq 基础收发、IQ 请求等待结果（await）、事件总线与连接阶段事件。
* 已验证：可完成本地与公网服务器的基础冒烟流程（登录、presence、disco 请求与接收）。
* 尚不具备：MUC、Roster、MAM、Jingle/文件传输等关键高级能力。
* 稳定性说明：当前 API 仍在迭代期，后续可能有不兼容调整（会在 README/Release Notes 同步）。

Takina 仍在积极开发中，功能列表会不断更新。如果您对她的功能和支持的 XEP 有任何要求、建议或意见，都可以发 `Issue` 告诉我们。

## 快速上手

### API 设计（当前）

Takina 目前的 API 设计目标是：

* 现代 Kotlin DSL
* 多连接（同一实例可管理多个账号）
* common 抽象 + jvm 平台实现分层
* 请求式 API + 事件流并存

核心使用范式如下：

```kotlin
import org.atoriapps.takina.core.components.discovery

val demoUserJid = "alice@example.com".toBareJid()

val takina = createTakina {
  addAccount {
    jid = demoUserJid
    password { "replace-with-real-password" }
    // endpoint/resource 可缺省，默认会自动推导
    endpoint {
      host { "example.com" }
      port { 5222 }
      securityMode { SecurityMode.START_TLS }
    }
  }

  addAccount {
    jid { "bot@example.com".toBareJid() } // 字段也支持懒加载
    password { "replace-with-real-password" }
  }
}

takina.events.on(AllConnectedEvent) {
  println("connected: ${it.connectedCount}/${it.configuredCount}")
}

takina.connect(demoUserJid)
takina.request.message {
  from = demoUserJid
  to = "bob@example.com".toBareJid()
  body = "hello from takina"
}.send()

// IQ await API（挂起等待 result/error）
val disco = takina.discovery().discoInfoAwait(
  from = demoUserJid,
  to = createBareJid(domain = demoUserJid.domain),
  timeoutMillis = 8000
).awaitResult()
println(disco.type)

takina.disconnectAll()
``` 

### 消息回执（自动 / 手动）

`MessageReceiptsComponent` 默认开启自动回执；如需业务侧手动确认，可关闭自动回执并在入站消息事件中自行发送：

```kotlin
import org.atoriapps.takina.core.components.receipts
import org.atoriapps.takina.core.events.StanzaReceivedEvent
import org.atoriapps.takina.core.xmpp.createFullJid

val selfFullJid = createFullJid(
  userName = demoUserJid.userName,
  domain = demoUserJid.domain,
  resource = "takina-smoke",
)

takina.receipts().autoReplyEnabled = false

takina.events.on(StanzaReceivedEvent) { event ->
  if (event.stanzaType != "message") return@on
  val sent = takina.receipts().sendReceivedReply(
    selfJid = selfFullJid,
    inboundMessageXml = event.xml,
  )
  if (!sent) {
    // 不是可回执消息（无 request 或无 id）时不会发送
  }
}
```

### Message Carbons（XEP-0280）

`CarbonsComponent` 支持连接后自动发送 enable（默认开启），也支持手动启用/关闭与 `await result`：

```kotlin
import org.atoriapps.takina.core.components.CarbonsComponent
import org.atoriapps.takina.core.components.carbons
import org.atoriapps.takina.core.events.StanzaReceivedEvent

createTakina(registerAllComponents = false) {
  registerComponent(CarbonsComponent)
  onConfigureComponent(CarbonsComponent) {
    autoEnableOnConnect = true
  }
}

// 手动启用并等待结果
// val result = takina.carbons().enableAwait(from = demoUserJid).awaitResult()

takina.events.on(StanzaReceivedEvent) { event ->
  if (event.stanzaType != "message") return@on
  val carbon = takina.carbons().parseEnvelope(event.xml) ?: return@on
  println("carbons=${carbon.frame} forwarded=${carbon.forwardedMessageXml}")
}
```

### Stream Management 调优（XEP-0198）

可通过组件配置调整流管理行为：

```kotlin
createTakina(registerAllComponents = false) {
  registerComponent(StreamManagementComponent)
  onConfigureComponent(StreamManagementComponent) {
    // 连接中断后是否自动重连（默认 true）
    autoReconnectOnConnectionDropped = true
    // 自动重连最大次数（默认 3）
    autoReconnectMaxAttempts = 3
    // 每累计多少个出站 stanza 自动发送一次 <r/> 请求服务端确认（默认 10，<=0 关闭）
    autoAckRequestInterval = 10
  }
}
```

### 协作式跨进程恢复（由使用方提供存储）

Takina 的 SM 跨进程恢复遵循以下职责划分：

* Takina：负责恢复流程（读取状态后尝试 `resume`、失败回退 `enable`、必要时重放未确认消息）。
* 使用方：负责状态持久化实现（文件、数据库、KeyValue、加密策略、TTL 清理等）。

组件提供 `StreamManagementStateStore` 接口，使用方注入实现并按需开启两个开关：

* `persistStateToStore`：是否在 SM 状态变化时写入 store。
* `restorePersistedStateOnStartup`：是否在新进程启动后优先从 store 恢复状态。

```kotlin
import org.atoriapps.takina.core.components.StreamManagementComponent
import org.atoriapps.takina.core.xmpp.BareJid

class MySmStore : StreamManagementComponent.StreamManagementStateStore {
  private val data = linkedMapOf<String, StreamManagementComponent.PersistedSessionState>()

  override fun load(jid: BareJid): StreamManagementComponent.PersistedSessionState? = data[jid.toString()]

  override fun save(
    jid: BareJid,
    state: StreamManagementComponent.PersistedSessionState,
  ) {
    data[jid.toString()] = state
  }

  override fun clear(jid: BareJid) {
    data.remove(jid.toString())
  }
}

val smStore = MySmStore()

createTakina(registerAllComponents = false) {
  registerComponent(StreamManagementComponent)
  onConfigureComponent(StreamManagementComponent) {
    stateStore = smStore
    persistStateToStore = true
    restorePersistedStateOnStartup = true
  }
}
```

### 其它示例

* `/takina-examples/src/jvmMain/kotlin/org/atoriapps/takina/examples/BasicJvmExample.kt`
* `/takina-examples/src/jvmMain/kotlin/org/atoriapps/takina/examples/SmokeClientExample.kt`

可直接执行：

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

### 文档

文档仍在持续完善中，当前以示例和源码注释为主。

## 编译

```bash
./gradlew :takina-core:allTests :takina-examples:compileKotlinJvm
```

## 更多信息

### 开发计划

请参见我们官方人员发的 `Issue`。

### 贡献

有意见或建议？发一个 `Issue` 告诉我们。

想提交代码？我们暂未出台 `贡献指南`，敬请期待。

想支持我们？我们很乐意接收您的赞助，敬请期待我们官方捐献通道的开通。如果您想立马捐赠，请发一个 `Issue` 告诉我们您打算通过什么平台或渠道支付（譬如 支付宝、微信支付、银行卡支付、PayPal 等）。

## 许可证

版权所有 (c) 2024 - 2026 Atori Apps。

本项目暂且使用 **MIT 许可证**。
