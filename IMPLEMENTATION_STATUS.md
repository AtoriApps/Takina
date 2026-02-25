# Takina 协议实现状态

> **最后更新**：2026-02-24
> **文档说明**：本文档用于统一维护 Takina 的 RFC/XEP 核心标准与扩展协议实现进度及待办优先级

## 📊 状态定义

* 🟢 **基本上已实现**：核心链路可用，提供正式 API（少数边缘语义或高级优化持续调优中）
* 🟡 **部分实现**：已完成该协议的部分子集能力，尚在完善主要链路
* ⚪ **待办**：列于计划表中，尚未提供正式实现

---

## 🚀 当前实现进度

### 1. 核心规范 (XMPP Core & IM)

* 🟡 **[RFC 6120] XMPP Core**：涵盖 Stream I/O、StartTLS 协商、SASL PLAIN 认证与基础资源绑定
* 🟢 **[RFC 7622] JID Format**：涵盖 JID 的解析、校验与规范化（如 domain 自动小写）
* 🟢 **[RFC 6121] IM and Presence**：基础收发、Roster 拉取/变更/分组、Push 来源校验与订阅管理
  * **进行中**：复杂并发 Push 的一致性边界调优

### 2. P0 基础与高优扩展

* 🟢 **[XEP-0198] 流管理 (Stream Management)**：支持 Enable/Resume、状态跟踪、自动请求 ACK 与防丢重放
  * **进行中**：跨服务端兼容性回归测试（不同失败码分支、长时离线恢复边界）
* 🟢 **[XEP-0184] 消息回执 (Delivery Receipts)**：支持请求/确认结构的构造解析，提供自动与手动回执 API
* 🟢 **[XEP-0280 / XEP-0313] 消息漫游与历史记录 (Carbons & MAM)**：支持多端同步转发，整合 MAM + RSM (分页) + Stanza ID，支持历史聚合与游标
  * **进行中**：补充更多过滤条件与跨服务端容错策略
* 🟢 **[XEP-0045 / 0249 / 0402] 综合群聊体系 (MUC & Bookmarks)**：打通进出房间、群消息、邀请逻辑，支持 Bookmarks 2 的发布/读取/撤回
  * **进行中**：房间成员状态语义的细节对齐
* 🟢 **[XEP-0156 / 0368 / RFC 7395] 连接发现与高阶传输**：支持 host-meta 解析、直连 TLS 发现与 WebSocket 入口识别
  * **进行中**：自动连接策略编排与 WebSocket 底层传输闭环
* 🟢 **[XEP-0030 / 0115] 服务发现与能力 (Disco & Caps)**：支持 disco#info 挂起等待、caps 节点构造与 presence 解析
  * **进行中**：disco#items 补充与完整能力哈希校验闭环
* 🟢 **[XEP-0352 / 0357] 移动端保活 (CSI & Push)**：打通 Active/Inactive 状态帧及 Push 开关流程
  * **进行中**：生命周期自动切换策略与 publish-options 全量语义
* 🟢 **[XEP-0363] HTTP 文件上传**：支持 Slot 申请、URL 解析隔离、尺寸超限拦截
  * **进行中**：自动失败重试与传输恢复策略？不过这个组件更多是建包和解析包吧，真正上传是用户负责

### 3. P1 进阶能力

* 🟢 **[XEP-0384 / 0420] OMEMO 端到端加密**：支持 V1/V2 双协议收发与 Signal 协议密钥传输。已实现多设备材料拉取/发布与缓存、加解密统一 DSL（自动降级兼容）、以及跨进程的持久化会话恢复机制（支持自定义 Store 落地）
  * **进行中**：PreKey 轮换策略？

---

## 📅 后续待办清单：功能性

### 🚧 优先级：高 (P1)
- [ ] **严格语义补齐**：完善 XEP-0030/0115/0184/0280 的哈希验证与多端一致性边界
- [ ] **[XEP-0191] 隐私与管控**：黑名单拉取与管理
- [ ] **[XEP-0084 / 0163] 用户资料**：用户头像获取与基础 PEP 事件能力

### ⏳ 优先级：中 (P2)
- [ ] **[XEP-0333] 聊天标记 (Chat Markers)**
- [ ] **[XEP-0085] 聊天状态通知 (Chat States)**：输入中、暂停等
- [ ] **[XEP-0308] 消息编辑 (Last Message Correction)**
- [ ] **[XEP-0245] 特殊动作指令**：`/me` 语义解析
- [ ] **Jingle 栈音视频基础**：涵盖 XEP-0166 / 0234 / 0260 / 0261 等

## 🧱 后续待办清单：设计改进（工程可用性）

- [ ] **登录等的重试策略**：看怎么设计，以使得譬如 `connectAll` 能够不一次未成功就放弃，也让用户可以配置策略
- [ ] **Java写法替换**：将公共部分还残留的Java写法，迁移为平台无关的（比如用协程的 `delay + structured concurrency` 代替 `Thread.sleep`）
- [ ] **统一结果模型**：引入 sealed result（Success/Timeout/Disconnected/AuthFailed/...）；同时了考虑加强一些Unit方法，变成能有Result的
- [ ] **错误码标准化**：可能也是enum或者sealed错因，就像是目前的连接错因
- [ ] **连接 API 协程化（挂起化）**：补齐 `suspend connect/disconnect/connectAll/disconnectAll`
- [ ] **一些东西的Flow化**：如连接状态，Roster等。看怎么做
- [ ] **组件的API DSL化**：譬如要发Muc申请进群，也应该搞一个扩展到takina.request.muc.join { }（我暂时这样设想）
- [ ] **高层业务事件**：补齐 MessageDelivered / RosterUpdated / MamPageLoaded / OmemoSessionChanged 等 domain 事件
- [ ] **Typed Stanza处理拦截/转换插件**：能提供如收到OMEMO消息自动解密的能力
- [ ] **订阅人体工学**：补充 `on(...) -> Disposable` 与 `once(...)`，降低 removeOn 维护成本
- [ ] **DSL 编译期约束增强**：逐步从运行时校验转向更强类型约束（必填字段分阶段构建）
- [ ] **组件能力软依赖入口（这个我觉得不一定需要，因为用户一般知道自己配置了什么组件）**：为 `takina.xxx` 增加 `xxxOrNull` 风格，减少未注册组件硬异常
- [ ] **请求级上下文能力（得再想想，这玩意有什么场景）**：增加 traceId / cancellation / timeout policy 的统一请求上下文
- [ ] **事件线程模型可配置化**：支持在配置阶段注入 dispatcher/scope/错误处理策略。这个又怎么说？？我得想想
- [ ] **模块领域对象**：譬如账号对象、私聊对象、群聊对象，针对性地收发消息等？有必要吗
- [ ] **给API打标**：可能如 @TakinaStableApi / @TakinaExperimentalApi / @TakinaInternalApi

---

## 📝 备注

* 优先级划分主要用于内部排期指导，不等同于立刻发布的版本承诺
* 若 XSF (XMPP 标准基金会) 对某协议状态做出破坏性变更（如突然降级或从 Draft 乱切 Stable），我们将在辱骂 XSF 后，于更新实现状态时同步修正
