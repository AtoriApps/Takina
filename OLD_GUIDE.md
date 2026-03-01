# 详细使用指南

本文档详细介绍了 Takina 各个核心扩展组件（XEPs）的配置与使用方法。如果您还没有完成基础的连接与登录，请先阅读 [README.md](OLD_README.md) 中的快速上手部分

## 目录
1. [消息回执 (Message Receipts)](#1-消息回执-message-receipts)
2. [消息漫游与同步 (Message Carbons)](#2-消息漫游与同步-message-carbons)
3. [流管理与跨进程恢复 (Stream Management)](#3-流管理与跨进程恢复-stream-management)
4. [OMEMO 私聊收发 (端到端加密)](#4-omemo-私聊收发-端到端加密)

---

### 1. 消息回执 (Message Receipts)

`MessageReceiptsComponent` (XEP-0184) 默认开启自动回执；如需业务侧接管，可将其关闭并在入站事件中自行触发

```kotlin
val takina = createTakina {
    // 省去添加账号部分
    
    onConfigureComponent(MessageReceiptsComponent) {
        autoReplyEnabled = false // 连接后默认关闭自动回执
    }
}

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

### 2. 消息漫游与同步 (Message Carbons)

要支持多端消息同步 (XEP-0280)，需要在注册组件时进行接收器配置：

```kotlin
val takina = createTakina {
  onConfigureComponent(CarbonsComponent) {
    autoEnableOnConnect = true // 连接后自动发送 enable 支持。本字段默认为 true
  }
}

// 解析多端同步消息
takina.events.on(StanzaReceivedEvent) { event ->
  if (event.stanzaType != "message") return@on
  val carbon = takina.carbons.parseEnvelope(event.xml) ?: return@on
  println("carbons=${carbon.frame} forwarded=${carbon.forwardedMessageXml}")
}

```

### 3. 流管理与跨进程恢复 (Stream Management)

您可以通过 `StreamManagementComponent` (XEP-0198) 属性调优流管理的确认频率及重连机制：

```kotlin
createTakina {
  onConfigureComponent(StreamManagementComponent) {
    autoReconnectOnConnectionDropped = true // 断开后自动重连
    autoReconnectMaxAttempts = 3 // 重试次数
    autoAckRequestInterval = 10 // 每出站多少个 stanza 自动向服务端请求一次确认
  }
}

```

> **恢复机制说明**：发送 `<resume/>` 后，组件会暂缓普通 `message/presence/iq` 出站，直到收到 `<resumed/>` 或失败后新会话 `<enabled/>` 再按顺序补发，避免状态重建类 stanza 过早发送导致恢复失败。只有收到 `<failed/>` 或超时才会回退到资源绑定。未注册该组件时将走传统 `bind -> online`

#### 协作式持久化恢复

因数据敏感，Takina 负责恢复流程控制，使用方负责状态的落地存储（DB、KV、加密等）。实现 `StreamManagementStateStore` 接口并按需开启：

```kotlin
class MySmStore : StreamManagementComponent.StreamManagementStateStore {
  override fun load(jid: BareJid) = /* 业务读取逻辑 */ null
  override fun save(jid: BareJid, state: StreamManagementComponent.PersistedSessionState) { /* 保存逻辑 */ }
  override fun clear(jid: BareJid) { /* 清理逻辑 */ }
}

val smStore = MySmStore()

createTakina {
  onConfigureComponent(StreamManagementComponent) {
    stateStore = smStore
    persistStateToStore = true // 状态变化时同步写入 Store
    restorePersistedStateOnStartup = true // 新进程启动时优先尝试快速恢复
  }
}
```

### 4. OMEMO 私聊收发 (端到端加密)

`OmemoComponent` (XEP-0384) 提供了设备材料发布、联系人材料同步、消息加解密能力。当前实现采用真实密码学链路（Signal key transport + AES-GCM payload），支持 OMEMO v2 与 v1 双协议收发

```kotlin
runBlocking {
  val alice = "alice@example.com".toBareJid()
  val bob = "bob@example.com".toBareJid()

  val takina = createTakina { 
    // 添加 alice 的账号
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
    }
  }.send()
}

```

#### 兼容性与协作式持久化策略

* **版本协商**：`AUTO` 为兼容优先策略。检测到对端支持 `V1` 优先使用 `V1`，否则 `V2`。`syncContactMaterial` 会同步并缓存两代材料。
* **签名校验**：`strictBundleSignatureValidation` 默认为 `false`（兼容模式），校验失败仅记录告警；若需严格模式可设为 `true`。
* **跨进程恢复**：使用方可自定义 `OmemoStateStore` 落地状态。建议至少持久化：
* `LocalDeviceState`（identity / signedPreKey / preKeys）
* 远端 `deviceIds` 与 `bundle`
* 远端 `session`（按 `account + owner + version + deviceId` 索引）

未自定义时，OMEMO 会话状态默认存在于内存中。如需查看 OMEMO 双账号冒烟测试的持久化文件示例，可运行 `runOmemoPrivateSmokeExample`。