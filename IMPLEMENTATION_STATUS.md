# Takina 协议实现状态

> 最后更新：2026-02-22
> 本文档用于统一维护 Takina 的 RFC/XEP 实现进度与待办优先级

## 状态定义

* `已实现（基本上）`：已有可用 API 并覆盖核心链路，但部分复杂边缘语义或极少数高级优化仍在调优中（当前所有 P0 能力均处于此状态）
* `部分实现`：仅完成了该协议的部分子集能力，尚在完善中
* `未实现（待办）`：尚未提供正式实现，列于后续计划表中

---

## 🚀 已实现情况

### 1. XMPP 核心规范（Core & IM）

* **[RFC 6120：XMPP Core](https://xmpp.org/rfcs/rfc6120.html)**：`部分实现`
    * 涵盖 stream open/close、StartTLS 协商、SASL PLAIN 认证、resource bind 及基础 stanza 收发
* **[RFC 7622：JID Format](https://xmpp.org/rfcs/rfc7622.html)**：`已实现（基本上）`
    * 涵盖 JID 解析、校验、规范化（如 domain 自动小写等）
* **[RFC 6121：IM and Presence](https://xmpp.org/rfcs/rfc6121.html)**：`已实现（基本上）`
    * 涵盖基础收发、roster 拉取与变更、group 解析、push 来源校验、订阅基础流程与 versioning 参数
    * *剩余工作*：复杂并发 push 的一致性边界调优（注：XEP-0237 已过时，优先按此 RFC 语义落地）

### 2. P0 基础与高优扩展（已基本实现）

当前版本的重点 P0 能力已经进入基本可用阶段，由相应的内置组件承载对应功能：

**【连接发现与高阶传输】**
* **XEP-0156 备用连接发现**：支持 host-meta XML/JSON 解析与首选端点选择过滤
* **RFC 7395 WebSocket**：识别与解析 WebSocket 备用连接入口
* **XEP-0368 直连 TLS**：支持识别基于 disco feature 的直连 TLS 能力
    * *剩余工作*：自动连接策略编排、直连握手策略与 WebSocket 底层传输闭环

**【流管理与可靠性投递】**
* **XEP-0198 Stream Management**：`StreamManagementComponent` 支持 enable/resume、状态跟踪、自动请求 ack、重连恢复与未确认消息防丢重放
    * *补充说明*：已增加“恢复握手窗口普通 stanza 暂缓与握手后补发”策略，降低恢复窗口内并发业务 stanza 干扰导致的 resume 失败；并修正为 `SASL 成功后优先 resume`，仅在 `<failed/>`/超时后才回退 `bind + enable`；恢复链路通过核心“预绑定协商扩展点”接入，未注册组件时不生效
    * *剩余工作*：跨服务端兼容性回归（含 location 提示、不同失败码分支与长时间离线恢复边界）
* **XEP-0184 Delivery Receipts**：`MessageReceiptsComponent` 支持回执请求/确认结构的构造解析，以及自动/手动回执 API

**【群聊与书签】**
* **XEP-0045 / 0249 / 0402 综合群聊体系**：`MucComponent` 已打通进出房间、群消息历史、直接邀请，并支持 Bookmarks 2 的发布/读取/撤回与 publish-options 操作
    * *剩余工作*：房间成员状态语义的细节对齐与错误码分支处理

**【消息漫游与多端同步】**
* **XEP-0280 Message Carbons**：`CarbonsComponent` 支持连接后自动开启或手动等待结果，并封装解析了多端转发消息
* **XEP-0313 MAM 等历史查询组合**：`MamComponent` 串联了 XEP-0313 (MAM)、XEP-0059 (RSM 分页)、XEP-0297 (转发包裹) 与 XEP-0359 (Stanza ID)，支持历史聚合与游标辅助
    * *剩余工作*：补充更多过滤条件与跨服务端差异化的容错策略

**【服务发现与实体能力】**
* **XEP-0030 / 0115 能力发现机制**：已支持 disco#info 挂起等待、caps 节点构造与 presence 解析
    * *剩余工作*：disco#items 补充、完整能力哈希计算与校验闭环

**【移动端保活与推送】**
* **XEP-0352 CSI / XEP-0357 Push**：`CsiPushComponent` 已打通 active/inactive 状态帧及无节点 push disable/enable 流程
    * *剩余工作*：生命周期自动切换策略与 publish-options 全量语义

**【文件传输】**
* **XEP-0363 HTTP File Upload**：`HttpUploadComponent` 已支持 slot 申请、URL 解析隔离、HTTP 头白名单限制及尺寸超限错误解析
    * *剩余工作*：自动失败重试机制与传输恢复策略

---

## 📅 待办清单

### 🚧 P1 进阶能力

1. **OMEMO 端到端加密 (XEP-0384 + XEP-0420)**
    * 设备列表、bundle 获取、会话加密与消息载荷封装
2. **P0 协议族的严格语义补齐**
    * 基于 XEP-0030/0115/0184/0280 补齐能力缓存、哈希严格验证、回执细粒度配置与多端一致性边界
3. **隐私与管控 (XEP-0191)**
    * 黑名单拉取与管理
4. **用户资料 (XEP-0084 + XEP-0163)**
    * 用户头像与基础 PEP 事件能力获取

### ⏳ P2 体验增强

1. **聊天标记 (XEP-0333)**
2. **聊天状态通知 (XEP-0085)**：输入中、暂停输入等状态
3. **消息编辑 (XEP-0308)**：修改最后一条消息语义
4. **特殊动作指令 (XEP-0245)**：`/me` 语义封装解析
5. **Jingle 栈音视频基础**：涵盖 XEP-0166 / 0234 / 0260 / 0261 等

---

## 📝 备注

* 优先级划分主要用于内部排期指导，不等同于立刻发布的版本承诺
* 若 XSF 对某协议状态做出变更（如降级为 Deferred/Obsolete 或从 Draft 转为 Stable），将在辱骂 XSF 后更新实现状态时同步修正