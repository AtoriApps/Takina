# Takina v1 草案

> Status: Draft and actively refined
> Target: v1
> Purpose: freeze semantics first and implement later

---

## 文档定位

本文档用于定义 Takina v1 的规范语义
本文档不是实现计划
本文档优先解决边界与一致性问题

---

## 设计目标

1. 稳定内核
连接 认证 收发 状态 错误语义保持一致

2. 统一扩展
组件与插件合并为统一抽象

3. 协作式边界
库负责协议正确性与运行时一致性
用户负责存储与产品语义

4. 开箱即用与强定制并存
默认预设即可运行
高级用户可按作用域精细控制

---

## 设计原则

### 轻量

API简明实用，库开箱即用

### 稳固

协程化 API
类型化错误
可观测节点执行

### 协作式

库只负责XMPP领域，不参与任何产品语义（如具体的消息存储、会话管理等）
库提供必要的功能接口给库用户交互（如流管理的跨进程恢复协作式接口）

### 语义先行

先冻结规则再写实现
禁止先写功能后补语义

---

## 术语

- Feature: 统一扩展单元
- Capability: Feature 的能力开关语义
- Node: Pipeline 执行节点
- Control Plane: 能力与开关求值层
- Execution Plane: 节点执行层
- Scope: 配置作用域
- Owner: 会话户主账号
- Business Outbound: 业务出站消息
- Control Outbound: 协议控制出站帧

---

## 已冻结规范

本节全部为 Normative

### 创建入口与预设

`preset` 放在构造参数层
DSL 内不再提供 `installPreset` 同义入口

建议签名

```kotlin
fun createTakina(
  preset: FeaturePreset = FeaturePreset.Recommended,
  init: TakinaConfiguration.() -> Unit,
): Takina
```

允许在 DSL 内做增量覆盖
不允许出现双入口冲突

### 统一扩展抽象

统一用 `Feature` 表达扩展
一个 Feature 可同时提供以下能力

- api surface
- lifecycle hooks
- inbound nodes
- outbound nodes
- optional projection

参考接口

```kotlin
interface TakinaFeature {
  val key: FeatureKey
  val supportedScopes: Set<ScopeKind>
  val applyMode: ApplyMode

  fun onInstall(context: FeatureContext) {}
  fun onShutdown(context: FeatureContext) {}

  fun api(): FeatureApi? = null
    
  fun lifecycleHooks(): List<ConnectionLifecycleHook> = emptyList()
    
  fun inboundNodes(): List<InboundNode> = emptyList()
  fun outboundNodes(): List<OutboundNode> = emptyList()
}
```

术语统一使用 `安装`，不混用 注册 与 安装

### 两层控制面

先 Control Plane 再 Execution Plane
优先级固定为

`能力开关 > 节点开关 > 节点排序`

### 作用域与优先级

作用域从高到低

1. message
2. conversation
3. account
4. global
5. preset

同级冲突规则

- 显式用户配置优先于 preset
- 同级冲突时 disable 优先
- 排序冲突必须可见

### 确定性求值顺序

每次请求或每帧处理都按固定顺序

1. Feature 是否已安装
2. Feature 在当前 scope 是否启用
3. Node 在当前 scope 是否启用
4. 对启用节点做排序并执行

### 配置生效时机

配置变更只影响未来事件
不追溯历史

运行时生效类型固定为以下三种

- `IMMEDIATE`
- `NEXT_ITEM`
- `NEXT_CONNECTION`

另有构建期不可变类型

- `BUILD_TIME_IMMUTABLE`（变更必须拒绝）

#### v1 生效矩阵（冻结）

| 配置项 | 生效时机 |
|---|---|
| `connection.host` `connection.port` `securityMode` | `NEXT_CONNECTION` |
| TLS 信任链与证书钉扎配置 | `NEXT_CONNECTION` |
| SASL 机制与认证参数 | `NEXT_CONNECTION` |
| 资源绑定策略（resource） | `NEXT_CONNECTION` |
| 压缩协商开关 | `NEXT_CONNECTION` |
| `StreamManagementFeature` 启停与恢复策略 | `NEXT_CONNECTION` |
| 握手阶段超时参数 | `NEXT_CONNECTION` |
| 业务入站/出站节点启停 | `NEXT_ITEM` |
| 业务入站/出站节点排序 | `NEXT_ITEM` |
| 默认消息加密策略（global/account/conversation） | `NEXT_ITEM` |
| 默认请求超时与重试参数 | `NEXT_ITEM` |
| 未知帧处理策略 | `NEXT_ITEM` |
| 自动重连开关 | `IMMEDIATE` |
| 重连退避参数（delay/factor/jitter/maxAttempts） | `IMMEDIATE` |
| 观测采样与告警阈值 | `IMMEDIATE` |
| 事件订阅过滤器 | `IMMEDIATE` |
| 不涉及协商的纯 API 能力开关 | `IMMEDIATE` |
| 涉及协商的能力开关 | `NEXT_CONNECTION` |
| `features { install(...) }` 安装集 | `BUILD_TIME_IMMUTABLE` |

### 动态开关

普通节点允许热切换
协议关键能力可限制为重连生效
所有配置变更必须发运行时变更事件

### 行为矩阵

| 状态 | API 可见性 | Hook 执行 | Node 执行 | 备注 |
|---|---|---|---|---|
| Feature 未安装 | 不可见或返回 NotInstalled | 否 | 否 | 不参与运行时 |
| Feature 已安装 作用域禁用 | 可见 调用返回 FeatureDisabled | 否 | 否 | 能力存在但不生效 |
| Feature 已安装并启用 无节点 | 可见 | 是 | 不适用 | 纯 API 或纯 Hook 能力 |
| Feature 已安装并启用 节点禁用 | 可见 | 是 | 否 | 仅节点不生效 |
| Feature 已安装并启用 节点启用 | 可见 | 是 | 是 | 全量生效 |

### 事件模型（v1 精简刚需）

v1 只保留事实级事件
步骤级细粒度事件不进入 core

#### Core 事件（内置）

- `TakinaStartedEvent`
- `TakinaShutdownCompletedEvent`
- `AccountAddedEvent`
- `AccountRemovedEvent`
- `GlobalConfigChangedEvent`
- `AccountConfigChangedEvent`
- `FeatureStateChangedEvent`
- `ConfigAppliedEvent`
- `ConfigApplyDeferredEvent`
- `ConfigRejectedEvent`
- `ConnectionStateChangedEvent`
- `UnexpectedDisconnectedEvent`
- `ReconnectScheduledEvent`
- `ReconnectExhaustedEvent`
- `SessionReadyEvent`
- `FrameInboundParseFailedEvent`
- `UnknownFrameInboundEvent`
- `MessageReceivedEvent`
- `PresenceReceivedEvent`
- `IqReceivedEvent`
- `MessageSentEvent`
- `MessageSendFailedEvent`
- `RequestFailedEvent`
- `RequestTimeoutEvent`
- `EncryptionFailedEvent`
- `DecryptionFailedEvent`
- `StoreOperationFailedEvent`

#### Feature 专属事件（按安装启用）

拿 `StreamManagementFeature` 举例子，它可能有：

- `StreamManagementResumedEvent`
- `StreamManagementResumeFailedEvent`
- `StreamManagementGapDetectedEvent`

更多能力的事件暂不列出，实现时按我们的习惯类推即可

### 可解释性与观测

必须提供 introspection

- `describeActiveFeatures(scope)`
- `describeActivePipeline(direction, scope)`
- `explainWhyEnabled(target, scope)`

节点执行必须暴露基础指标

- duration
- fail count
- drop count
- bypass count

---

## Pipeline 规范

### 非所有 frame 都是 stanza

必须先分类再分流

建议入站分类

- `STANZA_MESSAGE`
- `STANZA_PRESENCE`
- `STANZA_IQ`
- `CONTROL_SM`
- `STREAM_META`
- `STREAM_END`
- `UNKNOWN`

### 入站分流

```text
Inbound Raw Frame
 -> Classify
    -> Stanza Inbound Pipeline
    -> Control Inbound Pipeline
    -> Unknown Inbound Pipeline
```

控制帧默认不进入业务 stanza 事件流
控制帧可进入 raw 事件流与控制事件流

### 出站分流

必须区分业务出站与控制出站

```text
Outbound Command
 -> Classify
    -> Business Outbound Pipeline
    -> Control Outbound Pipeline
```

默认仅对 Business Outbound 开放用户自定义节点
Control Outbound 默认只开放观测与有限改写点

### 建议阶段

Stanza inbound

`Parse -> Decrypt -> Normalize -> Project -> Emit`

Business outbound

`Build -> Validate -> Enrich -> Encrypt -> Serialize -> Send -> MapResult`

### 加密策略层级

保留单消息配置。目前API设计：
消息DSL必须在每次发消息前拉取最新提供者，确保加密配置正确

```kotlin
takina/* or specific handle */.request.message {
  encryption { /*...*/ }
}
```

同时支持默认策略

- global default
- account default
- conversation default

优先级

`message > conversation > account > global`

### 自动 OMEMO 解密

自动解密作为可插拔节点
解密失败不吞消息
必须发失败事件并带 raw 与原因

---

## 领域句柄规范

### AccountHandle

`takina.forAccount(jid): AccountHandle`

作用

- 带户主：预绑定 from
- 承载账号粒度策略
- 提供账号粒度监听与能力访问

### 会话句柄

- `DirectChatHandle(owner, peer)`
- `MucHandle(owner, room)`

同一 room/peer 在不同 owner 下是不同上下文

### 句柄边界

句柄是 facade 与 context
不是独立状态机
所有动作最终进入统一 request 与 pipeline runtime

### 句柄配置生效

执行 DSL 完成后按 applyMode 生效
能即时就即时
不能即时则下条或下次连接生效

---

## API 命名规范

（以下为规范化的必有的，但不代表最后仅有它们，如果要有更多合理的，未来实现并添加）

顶层固定入口与动作

字段：

- `takina.events`
- `takina.runtime`
- `takina.request`

方法：

- `takina.capability { ... }`
- `takina.config { ... }`
- `takina.addAccount { ... }`
- `takina.removeAccount(jid)`
- `takina.forAccount(jid): AccountHandle`
- `suspend takina.connect(jid)` / `takina.connect(accountHandle)` 与对应的断连方法
- `suspend takina.connectAll()` 与对应的断连方法
- `takina.shutdown()`

.runtime：用于承载状态与运行信息
- 如 `accountStates` `connectionStates` `activePipeline` `health`

.events 下的方法与属性：

- `.events.on(Event){}`
- `.events.once(Event){}`
- `.events.removeOn(Event){}`
- `.events.flow(Event): Flow<Event>`
- `.events.enableEventLog: Boolean`（这样设置虽然直观，但会不会略显粗糙，而且只有全局粒度。是否变为在config或者是capability里设置更好？）

.request 下的方法：

- `.request.message { ... } : PendingMessageRequest`
- `.request.presence { ... } : PendingPresenceRequest`
- `.request.iq { ... } : PendingIqRequest`

账号句柄的方法：

- `accountHandle.request`
- `accountHandle.capability { ... }`
- `accountHandle.config { ... }`
- `suspend accountHandle.connect()`
- `suspend accountHandle.disconnect()`

获取功能的API：

- `takina.[FEARURE_NAME] : FeatureApi`：如 `takina.omemo`、`takina.streamManagement`

说明：

- 顶层与账号级配置都允许运行时变更
- 生效时机由对应配置项 `applyMode` 决定

---

## 连接与状态恢复规范

### 状态模型（冻结）

`AccountState`

- `REGISTERED`
- `CONNECTING`
- `ONLINE`
- `DEGRADED`
- `RECONNECTING`
- `OFFLINE`
- `FAILED`
- `REMOVED`

`ConnectionState`

- `IDLE`
- `TCP_CONNECTING`
- `TLS_HANDSHAKING`
- `STREAM_OPENING`
- `AUTHENTICATING`
- `BINDING_RESOURCE`
- `ESTABLISHED`
- `INTERRUPTED`
- `RESUMING_SM`
- `RECONNECT_WAIT`
- `CLOSED`

状态变化必须发 `ConnectionStateChangedEvent`

### 断线与重连策略（冻结）

非用户主动断开时

- 默认启用自动重连（`FeaturePreset.Recommended`）
- 启用流管理时先尝试 `RESUMING_SM`
- 流管理恢复失败则降级为完整重连
- 未启用流管理则直接完整重连
- 认证硬错误（如凭据错误）进入 `FAILED`，不进行无限重试

重连配置入口

- `takina.config { reconnect { ... } }`（全局默认）
- `takina.forAccount(jid).config { reconnect { ... } }`（账号覆盖）

---

## 协作式边界

### 库负责

- 协议正确性
- 状态机与恢复
- 统一消息处理链路
- 事实事件输出

### 用户负责

- 持久化模型与实现（即各协作式接口）
- 产品语义（UI、交互、数据管理等）与排序规则

### 注入接口策略

只提供必要的、形态确定的协作式接口

- `OmemoStateStore`
- `StreamManagementStateStore`

### 另

不提供会话能力，用户须自行管理任何会话内容
用户可以创建并安装自己的 feature，以实现自定义能力

---

## 稳定性与兼容策略

### 注解层级

- `@TakinaStableApi`
- `@TakinaExperimentalApi`
- `@TakinaInternalApi`

### 版本语义

`0.x`（目前）：快速演进中
`1.0`起：遵循 SemVer

### 迁移策略

旧 API 先 warning deprecate
至少保留一个 minor 周期
提供迁移说明与示例

---

## 示例图景

```kotlin
// 基本所有的dsl的字段设置，都有Provider模式，如：password { secret }
val takina = createTakina(preset = FeaturePreset.Recommended) {
  addAccount {
    jid = "alice@example.com".toBareJid()
    password = secret

    connection {
      // host、port、securityMode
    }

    defaults {
      messageEncryption = EncryptionPolicy.None
    }

    // capability、pipeline
  }

  defaults {
    messageEncryption = EncryptionPolicy.omemoAuto(fallbackBody = "Encrypted")
    // 连接重试等策略
  }

  features { // 这个不可动态再搞
    configure(StreamManagementFeature) { // 有安装才能config
      persistStateToStore = true
      restorePersistedStateOnStartup = true
    }
  }

  capability {
    enable(OmemoFeature) // scope = Scope.Global
    disable(HttpUploadFeature, scope = Scope.account("alice@example.com".toBareJid()))
  }

  pipeline {
    inbound {
      about(OmemoDecryptNode) {
        enabled = true
        order(before = MessageNormalizeNode)
      }
    }
  }
}

val alice = takina.forAccount("alice@example.com".toBareJid())
val room = alice.muc("a@conference.example.com".toBareJid())

takina./* suspend fun */connectAll()/* :TakinaResult<...> */.getOrThrow()

alice.events.on<MessageEvents.Received> { event ->
  println(event.message.body)
}

room.join(nick = "alice").send()

alice.request.message {
  to = "bob@example.com".toBareJid()
  body = "hello"
  encryption { provider = omemoProvider() }
}.send()

// HACK：runtime的xxxStates是不是支持`.value`
takina.runtime.connectionStates.collect { states ->
  println(states)
}

alice.capability/* or defaults, etc */ {} // 管理配置，对未来生效

// -- 使用能力 --

// 访问能力API
takina.omemo.bootstrapLocalDevice { user = "bob@example.com".toBareJid() }
// OR
alice.omemo.bootstrapLocalDevice()

// 能力的包的构建，与能力对`.request`的扩展（便捷入口）
takina.httpUpload.requestSlotAwait{ /*DSL*/ }.send()
// OR
takina.request.requestHttpUploadSlot{ /*DSL*/ }.send()
```

---

## 待明确

1. 事件 payload 字段定义（每个事件类的最小字段集）
2. 错误码总表与 retryable 判定规则
3. 全配置项 `applyMode` 明细表（逐字段）
4. `FeaturePreset.Minimal/Recommended/Full` 的 feature 与节点排序清单
5. Feature 依赖/互斥规则与拓扑报错格式
6. 请求超时、取消、重试、幂等语义
7. 重连与流管理下的队列重放边界（重发/丢弃规则）
8. introspection 返回结构（`describe*`）与稳定字段
9. 状态机迁移图（含异常路径）
10. Experimental 能力边界与注解策略（如注册能力）
11. `TakinaResult` 具体定义
12. 带内注册（Registration）能力语义：在dsl（takina.registration.newSession(host,port){}）里面完成注册表单填写、验证流程，功能将在独立轻量连接中完成注册；用户可在注册成功后`升级`连接为正式账号连接

---

## 补充构想

暂无

---

## 结论

Takina v1 的核心是先冻结语义边界
在此基础上再推进实现细节（避免后续重构爆炸）
