# Takina 协议实现状态

实现了记得更新！最后更新：2026-02-19

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
  * roster、订阅流程等完整语义未完成

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

## 未实现（待办）优先级清单

### P0（优先实现）

1. `RFC 6121：roster/订阅语义补齐`
   * 完整实现 roster 拉取/变更/版本语义、presence 订阅授权流程
   * 注：`XEP-0237` 已过时，优先按 RFC 6121 语义实现
2. `XEP-0198（流管理）：严格化`
   * 完整覆盖失败边界、计数一致性、恢复时序与异常重放策略
3. `XEP-0313（MAM）+XEP-0059（RSM）+XEP-0297（转发的）+XEP-0359`
   * MAM 查询、分页、结果聚合、stanza-id 关联
4. `XEP-0045+XEP-0249+XEP-0402`
   * MUC 最小闭环（进房/离房/群消息/历史）
   * 直接邀请与新书签规范（Bookmarks 2）
5. `XEP-0352（CSI）+XEP-0357（Push）`
   * CSI active/inactive 生命周期
   * Push enable/disable 与发现流程
6. `XEP-0363（Gultsch发的XEP）`
   * HTTP 文件上传（slot 申请、限制处理、错误恢复）
7. `RFC 7590+XEP-0368（直连 TLS）+RFC 7395（WebSocket）+XEP-0156`
   * 传输层现代化：TLS 最佳实践、直连 TLS、WebSocket、备用连接发现

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