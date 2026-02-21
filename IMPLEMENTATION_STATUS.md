# Takina 协议实现状态

实现了记得更新！最后更新：2026-02-21

> 本文档用于统一维护 Takina 的 RFC/XEP 实现进度与待办优先级。

## 状态定义

* `已实现（基本上）`：已有可用 API，但仍可能缺少高级语义/优化。
* `部分实现`：只完成子集能力。
* `未实现（待办）`：尚未提供正式实现。

## 当前已实现情况（基于当前代码）

### RFC

* [RFC 6120：XMPP Core](https://xmpp.org/rfcs/rfc6120.html)：`部分实现`
  * stream open/close
  * StartTLS 协商
  * SASL PLAIN 认证
  * resource bind
  * message / presence / iq 基础收发
* [RFC 7622：JID Format](https://xmpp.org/rfcs/rfc7622.html)：`已实现（基本上）`
  * JID 解析、校验、规范化（如 domain 小写）
* [RFC 6121：IM and Presence](https://xmpp.org/rfcs/rfc6121.html)：`部分实现`
  * 基础 message / presence 收发
  * `RosterComponent`：roster 拉取/变更、group 解析、subscription 基础流程、push 来源校验、versioning 请求参数

### XEP（当前内置组件）

* [XEP-0030 Service Discovery](https://xmpp.org/extensions/xep-0030.html)：`部分实现`
  * `DiscoveryComponent`：`disco#info` + await result；`disco#items` 等未完成
* [XEP-0115 Entity Capabilities](https://xmpp.org/extensions/xep-0115.html)：`部分实现`
  * `CapabilitiesComponent`：caps 元素构造与 presence 解析；完整能力哈希/校验未完成
* [XEP-0184 Message Delivery Receipts](https://xmpp.org/extensions/xep-0184.html)：`部分实现`
  * `MessageReceiptsComponent`：request/received 构造、解析、自动回执与手动回执 API
* [XEP-0198 Stream Management](https://xmpp.org/extensions/xep-0198.html)：`部分实现`
  * `StreamManagementComponent`：enable/resume/a/r、计数状态、自动 ack request、重连恢复与未确认重放基础流程
* [XEP-0280 Message Carbons](https://xmpp.org/extensions/xep-0280.html)：`部分实现`
  * `CarbonsComponent`：enable/disable、await result、转发消息封装解析、连接后自动 enable
* [XEP-0313 Message Archive Management](https://xmpp.org/extensions/xep-0313.html)：`部分实现`
  * `MamComponent`：MAM 查询、RSM 分页参数、result/fin 解析、结果聚合器与跨页游标辅助
* [XEP-0059 Result Set Management](https://xmpp.org/extensions/xep-0059.html)：`部分实现`
  * `MamComponent`：分页参数构造（after/before/max）与 first/last/count 解析
* [XEP-0297 Stanza Forwarding](https://xmpp.org/extensions/xep-0297.html)：`部分实现`
  * `MamComponent`：forwarded 消息包裹解析
* [XEP-0359 Unique and Stable Stanza IDs](https://xmpp.org/extensions/xep-0359.html)：`部分实现`
  * `MamComponent`：stanza-id 提取与关联
* [XEP-0045 Multi-User Chat](https://xmpp.org/extensions/xep-0045.html)：`部分实现`
  * `MucComponent`：进房/离房/群消息/历史请求参数
* [XEP-0249 Direct MUC Invitations](https://xmpp.org/extensions/xep-0249.html)：`部分实现`
  * `MucComponent`：直接邀请构造与解析
* [XEP-0402 PEP Native Bookmarks](https://xmpp.org/extensions/xep-0402.html)：`部分实现`
  * `MucComponent`：书签读取/发布请求封装（item id=room jid）、publish-options 与 retract 封装
* [XEP-0352 Client State Indication](https://xmpp.org/extensions/xep-0352.html)：`部分实现`
  * `CsiPushComponent`：active/inactive 帧构造与发送
* [XEP-0357 Push Notifications](https://xmpp.org/extensions/xep-0357.html)：`部分实现`
  * `CsiPushComponent`：push enable/disable + disco feature 解析（disable 支持无 node 关闭）
* [XEP-0363 HTTP File Upload](https://xmpp.org/extensions/xep-0363.html)：`部分实现`
  * `HttpUploadComponent`：slot 申请、put/get URL 解析（默认仅 https）、header 白名单、错误帧与 max-file-size 解析
* [XEP-0156 Discovering Alternative XMPP Connection Methods](https://xmpp.org/extensions/xep-0156.html)：`部分实现`
  * `ConnectionDiscoveryComponent`：host-meta XML/JSON Link 解析（仅接收 wss/https 端点）+ 首选端点选择策略
* [RFC 7395 An Extensible Messaging and Presence Protocol (XMPP) Subprotocol for WebSocket](https://www.rfc-editor.org/rfc/rfc7395)：`部分实现`
  * `ConnectionDiscoveryComponent`：WebSocket 能力与备用连接入口解析
* [XEP-0368 SRV records for XMPP over TLS](https://xmpp.org/extensions/xep-0368.html)：`部分实现`
  * `ConnectionDiscoveryComponent`：直连 TLS 能力识别（disco feature 维度）

## 未实现（待办）优先级清单

### P0（优先实现）

1. `RFC 6121：roster/订阅语义补齐`
   * 当前进度：`部分实现`（已覆盖拉取/变更、group、push 来源校验、subscription 基础流程）
   * 待补齐：更完整的 version 协商细节与复杂并发 push 一致性边界
   * 注：`XEP-0237` 已过时，优先按 RFC 6121 语义实现
2. `XEP-0198（流管理）：严格化`
   * 当前进度：`部分实现`（已有 enable/resume/a/r、状态持久化、未确认重放与自动重连）
   * 待补齐：更严格的失败分支覆盖与复杂网络异常回放策略
3. `XEP-0313（MAM）+XEP-0059（RSM）+XEP-0297（转发的）+XEP-0359`
   * 当前进度：`部分实现`（已支持查询构造、分页参数、result/fin、stanza-id、聚合器与游标辅助）
   * 待补齐：更多过滤条件与服务端差异下的聚合容错策略
4. `XEP-0045+XEP-0249+XEP-0402`
   * 当前进度：`部分实现`（已支持进房/离房/群消息/历史参数、直接邀请、Bookmarks2 发布/读取/retract/publish-options）
   * 待补齐：房间成员状态语义、错误码分支与书签同步回放细节
5. `XEP-0352（CSI）+XEP-0357（Push）`
   * 当前进度：`部分实现`（`CsiPushComponent` 已支持 active/inactive 与 push enable/disable + disco 解析）
   * 待补齐：生命周期自动切换策略与 push publish-options 全量语义
6. `XEP-0363（Gultsch发的XEP）`
   * 当前进度：`部分实现`（已支持 slot 申请、响应解析、限制错误解析）
   * 待补齐：自动失败重试与完整恢复策略
7. `RFC 7590+XEP-0368（直连 TLS）+RFC 7395（WebSocket）+XEP-0156`
   * 当前进度：`部分实现`（已支持发现信息解析与首选端点选择）
   * 待补齐：自动连接策略编排、直连 TLS 握手策略与 WebSocket 传输实现闭环

### P1（高优先）

1. `XEP-0384+XEP-0420`
   * OMEMO（设备列表、bundle、会话加密与消息封装）
2. `XEP-0030/XEP-0115/XEP-0184/XEP-0280的完整语义`
   * 补齐能力缓存、哈希计算、回执策略细粒度配置、多端一致性边界
3. `XEP-0191`
   * 黑名单管理
4. `XEP-0084 + XEP-0163`
   * 头像与 PEP 事件能力

### P2（中优先）

1. `XEP-0333`
   * 聊天标记
2. `XEP-0085`
   * 聊天状态通知
3. `XEP-0308`
   * 修改最后一条消息
4. `XEP-0245`
   * `/me` 语义封装
5. `Jingle 栈`
   * [XEP-0166](https://xmpp.org/extensions/xep-0166.html)、[XEP-0234](https://xmpp.org/extensions/xep-0234.html)、[XEP-0260](https://xmpp.org/extensions/xep-0260.html)、[XEP-0261](https://xmpp.org/extensions/xep-0261.html)

## 备注

* 本清单优先级用于指导“次时代 XMPP 客户端库”能力落地，不等同于发布承诺。
* 若某协议状态发生变更（Draft/Stable/Deferred/Obsolete），应在更新实现状态时同步备注。
