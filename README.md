# Takina（泷奈）

## Other Languages

[English](./README_EN.md): Outdated, for the latest information, please refer to this Chinese version.

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

当前内置可选组件（截至 2026-02-20）：

* `DiscoveryComponent`：XEP-0030（disco#info）与基础软件版本请求封装。
* `CapabilitiesComponent`：XEP-0115 caps 节点构造与 presence 载荷解析。
* `CarbonsComponent`：XEP-0280 Carbons 启用/关闭与转发消息解析。
* `MessageReceiptsComponent`：XEP-0184 回执请求/回执确认载荷构造、消息解析与自动回执策略。
* `StreamManagementComponent`：XEP-0198 基础模型（enable/resume/a/r 构造、帧解析、计数状态跟踪、自动确认请求与重连恢复基础流程）。
* `RosterComponent`：RFC 6121 roster 拉取/增删改与订阅流程（subscribe/subscribed/unsubscribe/unsubscribed）封装。
* `MamComponent`：XEP-0313 + XEP-0059 查询构造、分页参数、MAM result/fin 与 stanza-id 解析。
* `MucComponent`：XEP-0045 进房/离房/群消息，XEP-0249 直接邀请，XEP-0402 Bookmarks 2 请求封装。
* `CsiPushComponent`：XEP-0352 active/inactive + XEP-0357 push enable/disable 与能力发现解析。
* `HttpUploadComponent`：XEP-0363 slot 申请与 put/get URL 解析。
* `ConnectionDiscoveryComponent`：XEP-0156 host-meta 备用连接解析 + 直连 TLS/WebSocket/BOSH 特性检测。

## 功能特性

我们的目标是打造 [合规性测试](https://xmpp.org/extensions/xep-0479.html) 中打平甚至超越 [Conversations](https://codeberg.org/iNPUTmice/Conversations) 水准的库和 [客户端](https://github.com/AtoriApps/Atori)。

协议实现进度、已实现能力与未实现待办优先级清单，统一维护在：

* [IMPLEMENTATION_STATUS.md](./IMPLEMENTATION_STATUS.md)

Takina 仍在积极开发中。如果您对她的功能和支持的 RFC/XEP 有任何要求、建议或意见，都可以发 `Issue` 告诉我们。

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
  from = demoUserJid // 多连接时则必须指定 from
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

### P0 冒烟测试（JVM）

`takina-examples` 下的 `SmokeClientExample` 已支持按环境变量启用分项测试：

* `TAKINA_SMOKE_ROSTER=1`：执行 roster get。
* `TAKINA_SMOKE_MAM=1`：执行 MAM 查询（pageMax=1）。
* `TAKINA_SMOKE_PUSH=1` + `TAKINA_SMOKE_PUSH_SERVICE`：执行 push enable/disable（可选 `TAKINA_SMOKE_PUSH_NODE`）。
* `TAKINA_SMOKE_UPLOAD=1` + `TAKINA_SMOKE_UPLOAD_SERVICE`：执行 HTTP Upload slot 申请（可选 `TAKINA_SMOKE_UPLOAD_FILE`、`TAKINA_SMOKE_UPLOAD_SIZE`）。
* `TAKINA_SMOKE_ALT_LINKS_XML` / `TAKINA_SMOKE_ALT_LINKS_JSON`：离线验证 XEP-0156 解析逻辑。

运行示例：

```bash
./gradlew :takina-examples:runSmokeClientExample
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
