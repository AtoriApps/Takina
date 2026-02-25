# 旧 Takina v1 草案（架构与 API 设计）

> 状态：Draft（持续打磨中）
> 
> 面向版本：v1（当前库处于内部初步开发阶段）
> 
> 目的：统一 v1 设计语义，先定边界与模型，再推进实现

---

## 1. 背景与目标

Takina 当前已有较完整的协议能力集合（连接、MUC、MAM、Carbons、OMEMO、SM、Roster 等），但对外模型仍偏“能力堆叠”，缺少统一的运行时语义与协作边界

v1 目标：

1. **稳定内核**：连接/收发/状态/错误语义一致，可预期
2. **统一扩展模型**：组件与处理节点统一抽象，避免双系统割裂
3. **协作式边界明确**：库与业务职责清晰，默认轻量，可选增强

---

## 2. 设计原则

### 2.1 轻量

- 不强制数据库、ORM、Repo 框架
- 不把 UI 与产品策略塞进协议库
- 默认配置可开箱即用

### 2.2 强大与稳固

- API 协程化、错误类型化、事件层次化
- 保留 raw 能力（frame/xml）用于高级接入和调试
- 可观测、可裁剪、可演进

### 2.3 协作式

- 库负责协议正确性与状态机
- 用户负责持久化、产品语义、UI
- 通过可选注入/投影协作，而非强制框架绑定

### 2.4 语义先行

- 先定义术语、求值顺序、冲突规则，再实现
- 禁止“先写后补语义”

---

## 3. 关键术语

- **Feature**：统一扩展单元（组件/插件合体抽象）
- **Capability**：Feature 对外能力（可注册、可启用）
- **Pipeline Node**：处理节点（入站/出站处理步骤）
- **Control Plane**：配置与开关决策层
- **Execution Plane**：节点执行层
- **Scope**：配置作用域（global/account/conversation/message）
- **Owner**：会话户主账号（同一 room 在不同 owner 下视为不同会话上下文）

---

## 4. 总体架构（v1 视角）

```text
Takina Kernel
  ├─ Connection Runtime
  ├─ Request Runtime
  ├─ Event Runtime (raw + typed + domain)
  ├─ Feature Runtime (统一扩展模型)
  └─ Pipeline Runtime (inbound/outbound)

Feature
  ├─ API Surface（仅提供 API）
  ├─ Lifecycle Hook（连接阶段）
  ├─ Pipeline Nodes（入/出站）
  └─ Projection（可选领域事件投影）
```

核心要求：**不再并存“组件系统 + 插件系统”两套平行语义**

---

## 5. Feature 统一模型

## 5.1 目标

一个 Feature 可以：

- 只提供 API（不参与链路）
- 参与连接生命周期
- 提供入站/出站处理节点
- 可选提供领域投影

## 5.2 参考接口（草案）

```kotlin
interface TakinaFeature {
  val key: FeatureKey
  fun onInstall(context: FeatureContext) {}
  fun onShutdown(context: FeatureContext) {}
  fun api(): FeatureApi? = null
  fun lifecycleHooks(): List<ConnectionLifecycleHook> = emptyList()
  fun inboundNodes(): List<InboundNode> = emptyList()
  fun outboundNodes(): List<OutboundNode> = emptyList()
}
```

## 5.3 注册预设

- `coreOnly`
- `recommended`（开箱默认）
- `all`
- `custom`

---

## 6. 能力开关与作用域语义（Control Plane）

这是 v1 的硬语义，必须先定死

## 6.1 两层控制面

1. **能力层（Capability）**
   - 先判断 Feature 是否安装、是否启用（对于作用域而言）
   - 若能力未启用，其 API/hook/node 都不可用（不生效）
2. **执行层（Pipeline）**
   - 仅在能力已启用前提下，才讨论节点启用与排序

结论：**能力开关 > 节点开关 > 节点排序**

## 6.2 作用域层级

建议统一优先级（高 -> 低）：

1. message override
2. conversation
3. account
4. global
5. preset

## 6.3 冲突规则

- 显式用户配置优先于 preset
- 同级冲突时，`disable` 优先于 `enable`（安全优先）
- 排序冲突必须可见（报错或告警），禁止静默重排

## 6.4 求值顺序（确定性）

每次处理请求/入站帧时，固定按以下顺序求值：

1. Feature 是否安装
2. Feature 在当前 scope 是否启用
3. Node 在当前 scope 是否启用
4. 对已启用节点排序并执行

## 6.5 动态开关规则

- 普通节点可热切换
- 协议关键能力（如 SM）允许声明“需重连后生效”
- 配置变更应发运行时事件，便于审计与调试

## 6.6 观测与可解释性

提供 introspection：

- `describeActiveFeatures(scope)`
- `describeActivePipeline(direction, scope)`
- `explainWhyEnabled(node, scope)`

---

## 7. 消息处理管线（Execution Plane）

## 7.1 非所有 frame 都是 stanza

必须先分类，再分流。不能把所有 frame 丢进 stanza 处理

建议分类：

- `STANZA_MESSAGE`
- `STANZA_PRESENCE`
- `STANZA_IQ`
- `CONTROL_SM`
- `STREAM_META`
- `STREAM_END`
- `UNKNOWN`

## 7.2 入站分流模型

```text
RawFrame
 -> FrameClassify
    -> StanzaPipeline(message/presence/iq)
    -> ControlPipeline(sm/stream/...)
    -> UnknownPipeline
```

`CONTROL_SM` 等控制帧通常不进入业务 stanza 事件流

## 7.3 入站建议阶段（stanza 分支）

```text
FrameParse -> StanzaParse -> Security/Decrypt -> Normalize -> Project -> Emit Typed/Domain Events
```

## 7.4 出站建议阶段

```text
Build -> Validate -> Enrich -> Security/Encrypt -> Serialize -> Send -> Ack/Result Mapping
```

## 7.5 加密策略分层

消息加密是**单消息粒度**，因此：

- 保留 `request.message { encryption { ... } }`
- 允许默认策略（global/account/conversation）
- 单消息配置优先于默认策略。

## 7.6 自动 OMEMO 解密

- 作为可插拔入站节点（如 `OmemoDecryptNode`）
- 解密失败不应吞消息，需输出失败事件（含原因与 raw）

---

## 8. 领域句柄（Domain Handles）

目标：提升便捷性，但不改变内核权责

## 8.1 AccountHandle

```kotlin
val alice = takina.forAccount("alice@example.com".toBareJid())
```

用途：

- 预绑定 `from`，减少重复参数
- 配置账号粒度默认策略（如默认 OMEMO、回执处理、超时策略）
- 进入账号下属领域句柄（dm/muc/roster/...）

## 8.2 会话句柄（Owner-aware）

- `DirectChatHandle(owner, peer)`
- `MucHandle(owner, room)`

注意：同一 `roomJid` 在不同 owner 下是不同上下文

## 8.3 边界

Handle 是 facade/context，不是新状态机。最终仍走统一 request + pipeline runtime

---

## 9. API 形态（v1 草案）

## 9.1 连接

- `suspend connect/connectAll/disconnect/disconnectAll`
- 返回 `TakinaResult<T>`
- `connectionStates: StateFlow<Map<BareJid, ConnectionState>>`

## 9.2 请求

- 保留 DSL
- `send()` 返回类型化结果（不再只有 `Unit`）
- `iq await` 与其他请求共享统一结果模型

## 9.3 事件分层

- `rawFrames`
- `typedStanzas`
- `domainEvents`

增强订阅 ergonomics：

- `on(...) -> Disposable`
- `once(...)`

## 9.4 错误模型

```kotlin
sealed interface TakinaResult<out T>
sealed interface TakinaError {
  val code: TakinaErrorCode
  val retryable: Boolean
  val cause: Throwable?
}
```

---

## 10. 协作式边界（核心）

## 10.1 库负责

1. 协议正确性（连接、认证、路由、XEP 编排）
2. 状态机与恢复（SM/重连/ack 语义）
3. 消息处理链路（解析、加解密、标准化）
4. 事件事实输出（raw/typed/domain）

## 10.2 用户负责

1. 存储模型（Repo/DAO/DB）
2. 产品策略（排序、置顶、免打扰、通知）
3. UI 状态与交互
4. 业务语义（如已读定义）

## 10.3 可选注入接口

仅在“实现形态高度统一”的状态上建议注入：

- `OmemoStateStore`（已有）
- `StreamManagementStateStore`（已有）

`MessageStore/ConversationStore` 不做，用户自己做

---

## 11. 会话能力策略

v1 提供“会话事件 + 可选投影”，不强制会话存储

## 11.1 库内提供

- `ConversationEvent`（upsert/remove/unread-changed 等）
- `ConversationProjector`（可选内存实现）

## 11.2 业务侧自由

- 持久化 schema
- 会话排序/筛选
- 草稿/置顶/标签等产品字段

---

## 12. 兼容性与稳定性策略

## 12.1 API 稳定级别

- `@TakinaStableApi`
- `@TakinaExperimentalApi`
- `@TakinaInternalApi`

## 12.2 版本语义

- `0.x` 快速演进
- `1.0` 起遵循 SemVer

## 12.3 迁移策略

- 旧 API 先 `@Deprecated(WARNING)`
- 至少保留一个 minor 周期
- 提供迁移示例与说明

---

## 13. v1 的设想使用图景（暂定）

```kotlin
// 基本所有的dsl的字段设置，都有Provider模式，如：password { secret }
val takina = createTakina (featureInstallation = FeatureInstallation.Recommended /*更多参数*/) {
  addAccount {
    jid = "alice@example.com".toBareJid()
    password = secret
    
    connection {
        // host、port、securityMode
    }
    
    defaults { // 账号粒度配置
      messageEncryption = EncryptionPolicy.None
    }

    features { /* 账号粒度？ */ }

    capability { /* 账号粒度 */ }
  }

  defaults { // 全局粒度配置
    messageEncryption = EncryptionPolicy.OmemoAuto(fallbackBody = "Encrypted message")
    // 连接重试等策略
  }

  features { // 这个不可动态再搞
    configure(StreamManagementFeature) { // 安装了才能config
      persistStateToStore = true
      restorePersistedStateOnStartup = true
    }
  }

  // Control Plane
  capability {
    enable(OmemoFeature/*, scope = Scope.global()*/) // 如果默认disable，enable会覆写，则实际开启
    disable(HttpUploadFeature, scope = Scope.account("alice@example.com".toBareJid())) // scope也可以在这里指定，非常灵活
  }

  // 管线要开箱即用（用户不在下面配置也有默认安排）；这样的管线配置合不合理？管线就全局吧，还是也能作用域？
  pipeline {
    inbound {
      enable(OmemoDecryptNode)
      order(OmemoDecryptNode, before = MessageNormalizeNode)
      
      // or

      about(OmemoDecryptNode) {
          enable = true
          order(before = MessageNormalizeNode)
      }
       
      // 这两种API哪个更好？
    }
  }
}

val alice = takina.forAccount("alice@example.com".toBareJid())
val room = alice.muc("a@conference.example.com".toBareJid())

takina./* suspend fun */connectAll()/* :TakinaResult<...> */.getOrThrow()

JobScoped { // 假设用户在Job里面拨弄

  takina.events.on<MessageEvents.Received> { event -> // 本lambda的this指针是context
    println("Received message: ${event.message.body}")
  }

  takina.states/*或者其他属性名*/.connectionStates.collect {} // 想设计这样的

  alice.on<MessageEvents.Received> {} // 账号粒度监听
}

room.join(nick = "alice").send()

alice.request.message { // takina.request.message 的 预绑定版本
  to = "bob@example.com".toBareJid()
  body = "hello"
  encryption { provider = omemoProvider() }
}.send()

alice.capability /* or defaults etc */ {} // 对，又可以管理配置

// -- 使用能力 --

// 访问能力API
takina.omemo.bootstrapLocalDevice("bob@example.com".toBareJid())
// OR
alice.omemo.bootstrapLocalDevice()

// 能力的包的构建，与能力对`.request`的扩展（便捷入口）
takina.httpUpload.requestSlotAwait{/*DSL*/}.send()
// OR
takina.request.requestHttpUploadSlot{/*DSL*/}.send()
```

---

## 14. 风险与注意事项

1. Feature 不能沦为万能钩子集合，必须受限于明确生命周期与线程语义
2. 管线节点必须可观测（耗时/失败/drop），避免黑盒
3. 自动行为（自动解密、自动回执、自动重放）必须可显式开关
4. 关键语义（错误码、作用域优先级、冲突规则）必须先冻结后实现

---

## 15. 待决策问题（下一轮草案）

1. `TakinaResult` 是否复用 Kotlin `Result`，还是自定义结构承载错误码语义
2. Feature/Node 排序冲突的失败策略（启动失败 vs 运行时警告）
3. `recommended` 预设默认启用的 Feature/Node 清单
4. 动态热切换能力的白名单（哪些必须重连生效）
5. 确定：我们具体都有什么Event？（核心Event与功能自己的Event）。以及 Domain Event 的最小必选集合

---

## 16. 结论

Takina v1 应聚焦：**稳定内核 + 统一扩展 + 协作边界清晰**

- 能力开关与作用域语义先行，避免后续重构爆炸
- Feature 与 Pipeline 统一后，扩展能力与维护成本会显著改善
- 领域句柄用于提升易用性，但不侵入协议内核与业务存储边界