# Takina (泷奈)

[English](./README_EN.md): Outdated, for the latest information, please refer to this Chinese version

![Banner](./TAKINA_BANNER.svg)

## 简介

Takina 是由 Atori Apps 团队维护的 [Kotlin](https://kotlinlang.org/) [XMPP](https://xmpp.org) 客户端库，提供了对 XMPP 核心标准的基础实现以及 XML 处理功能
目前 XEP 扩展支持正在持续落地中，未来她将作为 Atori 的 `Xmpp 平台能力模块` 底座，用于支撑全平台的聊天通讯能力

作为采用 [Kotlin Multiplatform](https://kotlinlang.org/docs/reference/multiplatform.html) 技术栈的开源项目，Takina 旨在覆盖尽可能多的平台：
* JVM
* JS（计划支持）
* Android（计划支持，通常兼容 JVM 即可直接兼容 Android）

## 项目架构

Takina 采用 **KMP 分层 + 组件化** 设计，功能以组件形式组织，避免逻辑耦合，便于扩展与裁剪

**工程分层**：
* `takina-core/src/common**`：平台无关的核心（协议模型、请求 API、连接状态机、事件模型、组件抽象）和测试集
* `takina-core/src/jvm**`：JVM 平台的实现（Socket/TLS、事件总线并发实现等）和测试集
* `takina-examples/src/jvmMain`：使用示例与冒烟验证入口

**组件化原则**：
* **核心能力（必需）**：提供连接生命周期、账号管理、事件分发、基础 stanza 请求等底座能力，为保证效率，不打算组件化
* **可选组件（按需）**：承载具体 XEP 与高级能力（如 MAM、MUC 等），可按需注册，未注册不会影响核心链路
> 注：后续新增协议能力应优先作为可选组件开发，仅当属于跨组件共享的底座逻辑时才进入核心层

当前内置可选组件（截至 2026-02-20）：
* `DiscoveryComponent`：XEP-0030 (disco#info) 与基础软件版本请求封装
* `CapabilitiesComponent`：XEP-0115 caps 节点构造与 presence 载荷解析
* `CarbonsComponent`：XEP-0280 Carbons 启用/关闭与转发消息解析
* `MessageReceiptsComponent`：XEP-0184 回执请求/确认载荷构造、消息解析与自动回执策略
* `StreamManagementComponent`：XEP-0198 基础模型（帧解析、计数跟踪、自动确认、重连恢复、恢复握手窗口暂缓普通 stanza、SASL 后先 resume 失败再 bind+enable）
* `RosterComponent`：RFC 6121 roster 拉取与增删改、group 解析、订阅流程与 push 来源校验
* `MamComponent`：XEP-0313 + XEP-0059 查询构造、分页参数、结果聚合与游标辅助
* `MucComponent`：XEP-0045 群聊进出与消息，XEP-0249 邀请，XEP-0402 Bookmarks 2 操作
* `CsiPushComponent`：XEP-0352 活跃状态切换 + XEP-0357 push 开关与发现解析
* `HttpUploadComponent`：XEP-0363 slot 申请、URL 解析与上传错误处理
* `ConnectionDiscoveryComponent`：XEP-0156 host-meta 备用连接解析与首选端点选择
* `OmemoComponent`：XEP-0384 端到端加密（支持 `urn:xmpp:omemo:2` 与 `eu.siacs.conversations.axolotl`，默认 `AUTO` 协商，失败回退 `V1`）

## 功能进度

我们的目标是在 [合规性测试](https://xmpp.org/extensions/xep-0479.html) 中达到甚至超越 [Conversations](https://codeberg.org/iNPUTmice/Conversations) 水准

协议实现进度、各项能力明细与待办优先级，请查阅以下清单：
* [IMPLEMENTATION_STATUS.md](./IMPLEMENTATION_STATUS.md)

如果您对功能或需要支持的 RFC/XEP 有任何建议，欢迎提交 Issue 告诉我们

## 快速上手

Takina 的 API 设计采用现代 Kotlin DSL，支持单一实例管理多个连接账号，并允许挂起等待（请求式 API）与事件流混合并发

### 1. 基础建立与收发消息

```kotlin
import org.atoriapps.takina.core.components.discovery

val demoUserJid = "alice@example.com".toBareJid()

val takina = createTakina {
  // 添加第一个账号
  addAccount {
    jid = demoUserJid
    password = "replace-with-real-password" // 支持字段直接赋值
    
    // endpoint 可缺省，库将自动推导
    endpoint {
      host = "example.com"
      port = 5222
      securityMode = SecurityMode.START_TLS // 这是默认设置
    }
  }

  // 添加第二个账号（提示：全部字段都支持使用 Lambda 懒加载）
  addAccount { 
    jid { "bot@example.com".toBareJid() }
    password { "replace-with-real-password" }
  }
}

// 连接端点参数（host / port / securityMode）统一通过 endpoint 配置

// 监听连接事件
takina.events.on(AllConnectedEvent) {
  println("connected: ${it.connectedCount}/${it.configuredCount}")
}

takina.connect(demoUserJid)

// 发送消息
takina.request.message {
  from = demoUserJid // 存在多个连接时，必须通过 from 指定目标发送方账号
  to = "bob@example.com".toBareJid()
  body = "hello from takina"
}.send()

// 挂起等待 IQ 结果
val disco = takina.discovery().discoInfoAwait(
  from = demoUserJid,
  to = createBareJid(domain = demoUserJid.domain),
  timeoutMillis = 8000
).awaitResult()
println(disco.type)

takina.disconnectAll()
``` 

### 2. 消息回执（自动/手动）

`MessageReceiptsComponent` 默认开启自动回执；如需业务侧接管，可将其关闭并在入站事件中自行触发

```kotlin
import org.atoriapps.takina.core.components.receipts
import org.atoriapps.takina.core.events.StanzaReceivedEvent
import org.atoriapps.takina.core.xmpp.createFullJid

// 关闭自动回执（需保证组件已被注册）
takina.receipts.autoReplyEnabled = false

val selfFullJid = createFullJid(
  userName = demoUserJid.userName,
  domain = demoUserJid.domain,
  resource = "takina-smoke",
)

takina.events.on(StanzaReceivedEvent) { event ->
  if (event.stanzaType != "message") return@on
  val sent = takina.receipts.sendReceivedReply(
    selfJid = selfFullJid,
    inboundMessageXml = event.xml,
  )

  if (!sent) {
    // 消息不可回执（无 request 或无 id）
  }
}
```

### 3. Message Carbons（XEP-0280）

在注册组件时进行接收器配置：

```kotlin
import org.atoriapps.takina.core.components.CarbonsComponent
import org.atoriapps.takina.core.components.carbons
import org.atoriapps.takina.core.events.StanzaReceivedEvent

createTakina(registerAllComponents = false) {
  registerComponent(CarbonsComponent)

  onConfigureComponent(CarbonsComponent) {
    autoEnableOnConnect = true // 连接后自动发送 enable 支持
  }
}

// 解析多端同步消息
takina.events.on(StanzaReceivedEvent) { event ->
  if (event.stanzaType != "message") return@on
  val carbon = takina.carbons.parseEnvelope(event.xml) ?: return@on
  println("carbons=${carbon.frame} forwarded=${carbon.forwardedMessageXml}")
}
```

### 4. 流管理与跨进程恢复（XEP-0198）

您可以通过组件属性调优流管理的确认频率及重连机制：

```kotlin
createTakina(registerAllComponents = false) {
  registerComponent(StreamManagementComponent)
  onConfigureComponent(StreamManagementComponent) {
    autoReconnectOnConnectionDropped = true // 断开后自动重连
    autoReconnectMaxAttempts = 3 // 重试次数
    autoAckRequestInterval = 10 // 每出站多少个 stanza 自动向服务端请求一次确认
  }
}
```

注：`StreamManagementComponent` 在发送 `<resume/>` 后，会暂缓普通 `message/presence/iq` 出站，直到收到 `<resumed/>` 或失败后新会话 `<enabled/>`，再按顺序补发，避免在恢复窗口提前发送“状态重建类 stanza”（如 `carbons enable`）导致恢复失败。重连时会在 `SASL 成功 + 新 stream features` 后优先尝试 `<resume/>`，只有收到 `<failed/>` 或恢复超时时才回退到资源绑定与新会话 enable
实现上通过核心的“预绑定协商扩展点”接入该流程：未注册流管理组件时将直接走传统 `bind -> online`，不会隐式启用恢复语义

**协作式持久化恢复**：
因数据敏感，Takina 负责恢复流程控制，使用方负责状态的落地存储（DB、KV、加密等）
实现 `StreamManagementStateStore` 接口并按需开启开关即可：

```kotlin
class MySmStore : StreamManagementComponent.StreamManagementStateStore {
  override fun load(jid: BareJid) = /* 业务读取逻辑 */ null
  override fun save(jid: BareJid, state: StreamManagementComponent.PersistedSessionState) { /* 业务保存逻辑 */ }
  override fun clear(jid: BareJid) { /* 业务清理逻辑 */ }
}

val smStore = MySmStore()

createTakina(registerAllComponents = false) {
  registerComponent(StreamManagementComponent)
  onConfigureComponent(StreamManagementComponent) {
    stateStore = smStore
    persistStateToStore = true // 状态变化时同步写入 Store
    restorePersistedStateOnStartup = true // 新进程启动时优先从 Store 读取并尝试快速恢复
  }
}
```

### 5. OMEMO 私聊收发（XEP-0384）

`OmemoComponent` 提供了设备材料发布、联系人材料同步、消息加密与解密能力。当前实现采用真实密码学链路（Signal key transport + AES-GCM payload），支持 OMEMO v2 与 v1 双协议收发
发送侧支持统一消息 DSL：不配置 `encryption {}` 时保持明文；配置后通过 `provider` 指定加密实现。OMEMO provider 默认 `AUTO` 协商，失败会回退 `V1`
`AUTO` 现为兼容优先策略：若检测到对端支持 `V1` 则优先使用 `V1`（覆盖更多现有客户端），否则使用 `V2`
`syncContactMaterial` 在 `AUTO` 下会尝试同步 `V2 + V1` 两代材料并缓存；`publishOwnMaterial` 会合并服务器已有设备列表并缓存自身设备材料，发送时除对端设备外也会覆盖本账号其它已知设备
已按 XEP-0384 现行结构对齐 v2 bundle 节点：使用 `urn:xmpp:omemo:2:bundles` + `item id=<deviceId>`，并兼容解析历史 `bundles:<deviceId>` 形式
为便于排障，已补齐 bundle 拉取/解析/发送阶段中文日志（含 `version/deviceId`）；当只拿到 device list 但缺少 bundle 时会明确记录
`strictBundleSignatureValidation` 默认 `false`（兼容模式）：当远端 bundle 签名校验失败时默认记录告警并继续缓存；若需要严格模式可切换为 `true`
v1/v2 发送均采用 Signal 协议密钥传输（`<key/>`）+ 头部 `<iv/>` + `payload` 密文分离格式
OMEMO 会话状态支持协作式持久化：使用方可自定义 `OmemoStateStore` 落地 `load/save/clearRemoteSession(account, owner, version, deviceId)`，实现跨进程恢复；未自定义时默认内存态

```kotlin
import kotlinx.coroutines.runBlocking
import org.atoriapps.takina.core.components.OmemoComponent
import org.atoriapps.takina.core.components.omemo

runBlocking {
  val alice = "alice@example.com".toBareJid()
  val bob = "bob@example.com".toBareJid()

  val takina = createTakina(registerAllComponents = false) {
    registerComponent(OmemoComponent)
    // addAccount ... (alice)
  }

  takina.connectAll()
  takina.omemo.bootstrapLocalDevice(alice)
  takina.omemo.publishOwnMaterial(alice)
  takina.omemo.syncContactMaterial(from = alice, contact = bob)
  takina.request.message {
    from = alice
    to = bob
    body = "hello omemo"
    encryption {
      provider = omemoProvider(preferVersion = OmemoProtocolPreference.AUTO)
      fallbackBody = "信息已加密，请使用支持 OMEMO 的客户端查看"
      // required 默认为 true
    }
  }.send()
}
```

**跨进程恢复建议**：
实现自定义 `OmemoStateStore` 时，建议至少持久化以下数据：
* `LocalDeviceState`（identity/signedPreKey/preKeys）
* 远端 `deviceIds` 与 `bundle`
* 远端 `session`（按 `account + owner + version + deviceId` 索引）

## 编译与测试

编译核心库并运行测试：

```bash
./gradlew :takina-core:allTests :takina-examples:compileKotlinJvm
```

**运行冒烟测试**：
支持环境变量组合启用单一能力测试验证
（更多测试参数请查看源码）

```bash
TAKINA_JID='alice@example.com' \
TAKINA_PASSWORD='secret' \
./gradlew :takina-examples:runSmokeClientExample
```

**运行 OMEMO 双账号私聊冒烟**：

```bash
TAKINA_A_JID='alice@example.com' \
TAKINA_A_PASSWORD='secretA' \
TAKINA_B_JID='bob@example.com' \
TAKINA_B_PASSWORD='secretB' \
TAKINA_OMEMO_STORE_FILE='./build/omemo-dm-smoke.store' \
./gradlew :takina-examples:runOmemoPrivateSmokeExample
```

说明：`OmemoDmSmoke` 已内置一个 JVM 文件版 `OmemoStateStore`，默认会把 OMEMO 本地设备、远端材料与会话状态存到 `./build/omemo-dm-smoke.store`；下次冒烟会自动复用

更多基础示例可查阅 `takina-examples` 下的代码

## 开源与支持

* **开发计划**：最新动向请参见由官方维护的 Issue 列表
* **代码贡献**：如果您有改进或建议，欢迎先开 Issue 进行讨论；贡献指南文档正在编写中，敬请期待
* **赞助我们**：官方公共收款通道规划中；如果您愿意马上支持我们的开发进度，可以通过 Issue 告知我们您期望的赞助方式（如微信、支付宝、PayPal 等）

## 许可证

版权所有 (c) 2024 - 2026 Atori Apps
本项目目前采用 **MIT 许可证**
