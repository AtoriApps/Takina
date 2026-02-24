# Takina API 参考

> 文档目标：面向库使用者，汇总 Takina 当前可用的核心 API 与组件 API（基于当前仓库源码）
> 
> 更新时间：2026-02-24
> 
> 适用范围：`takina-core` 当前 `commonMain` + `jvmMain` 可见 API

## 1. 核心本体 API

### 1.1 入口与构建

```kotlin
fun createTakina(
  registerAllComponents: Boolean = true,
  init: TakinaConfiguration.() -> Unit,
): Takina
```

- `registerAllComponents=true`：自动注册当前全部内置可选组件
- `registerAllComponents=false`：仅核心能力，不自动注册可选组件

`TakinaConfiguration` 关键 API：

- `addAccount { ... }`
- `registerComponent(provider)`
- `onConfigureComponent(provider) { ... }`

`addAccount` DSL 字段/函数：

- 字段：`jid`、`password`、`resource`、`connectTimeoutMillis`、`streamLanguage`
- Provider 形式：`jid { ... }`、`password { ... }`、`resource { ... }`、`connectTimeoutMillis { ... }`、`streamLanguage { ... }`
- 端点配置：
  - `endpoint(host, port?, securityMode)`
  - `endpoint { host/port/securityMode }`

`SecurityMode`：

- `DIRECT_TLS`（默认端口 5223）
- `START_TLS`（默认端口 5222）
- `PLAIN`（默认端口 5222）

### 1.2 Takina 运行时对象

`Takina`（`AbstractTakina` 的平台实现）公开能力：

- 属性：
  - `events: TakinaEventBus`
  - `request: TakinaRequest`
  - `configuredAccounts: List<BareJid>`
  - `activeConnections: List<TakinaConnection>`
- 连接管理：
  - `connectAll()`
  - `disconnectAll()`
  - `connect(jid: Jid)`
  - `disconnect(jid: Jid)`
  - `shutdown()`
- 组件访问（通过 `TakinaContext`，或者是组件对Takina的扩展属性）：
  - `findComponent(type/provider)`
  - `requireComponent(type/provider)`
  - `inline findComponent<T>()`
  - `inline requireComponent<T>()`

多账号出站规则：

- 显式 `from` 时按 `from` 账号发送
- 未指定 `from` 且只有一个已连接账号时自动路由
- 未指定 `from` 且多账号会抛 `AmbiguousAccountException`

### 1.3 请求 DSL（`takina.request`）

- `message { ... } -> PendingMessageRequest`
- `presence { ... } -> PendingStanzaRequest`
- `iq { ... } -> PendingStanzaRequest`
- `iqAwait(timeoutMillis) { ... } -> PendingIqAwaitRequest`

`Pending*`：

- `PendingMessageRequest.send()` / `toXml()`
- `PendingStanzaRequest.send()` / `toXml()`
- `PendingIqAwaitRequest.awaitResult()` / `send()` / `toXml()`

消息加密 DSL：

- `message { encryption { ... } }`
- `MessageEncryptionProvider` 当前实现：`OmemoEncryptionProvider`
- 辅助函数：`omemoProvider(preferVersion = AUTO)`

### 1.4 事件总线（`takina.events`）

基类能力（common）：

- `on(eventType/describer, handler)`
- `removeOn(...)`
- `flow(...)`
- `emit(...)`
- `enableEventLog: Boolean`

JVM 额外能力：

- `TakinaEventBus.mode`
- `Mode.Inline`
- `Mode.AsyncPerEvent`
- `Mode.AsyncPerHandler`

内置事件类型：

- `ConnectionConnectedEvent`
- `ConnectionFailedEvent`
- `AllConnectedEvent`
- `AllDisconnectedEvent`
- `StanzaReceivedEvent`
- `FrameOutboundEvent`
- `FrameInboundEvent`
- `ConnectionClosedEvent`
- `ConnectionStageChangedEvent`

连接阶段枚举：

- `ConnectionLifecycleStage.DISCONNECTED`
- `TCP_CONNECTED`
- `STREAM_OPENED`
- `TLS_NEGOTIATED`
- `AUTHENTICATED`
- `RESOURCE_BOUND`
- `ONLINE`
- `DISCONNECTING`

### 1.5 常用模型与异常

- JID：`Jid` / `BareJid` / `FullJid`
- 解析与构造：`toJid()/toBareJid()/toFullJid()`、`createJid/createBareJid/createFullJid`
- Stanza：`MessageStanza` / `PresenceStanza` / `IqStanza`
- `IqResult(id, type, xml)`

异常体系：

- `TakinaException`
- `TakinaConnectionException(kind, detail)`
- `AccountNotFoundException`
- `AmbiguousAccountException`
- `InvalidRequestException`
- `NotConnectedException`
- `JidFormatException`

---

## 2. 组件 API 总览

### 2.1 `DiscoveryComponent`（XEP-0030 / 软件版本查询）

访问器：`takina.discovery`

配置项：

- `captureInboundIqResults: Boolean`

主要 API：

- `discoInfo(to?, from?, node?)`
- `softwareVersion(to?, from?)`
- `getAwait(to?, from?, node?, timeoutMillis)`
- `softwareVersionAwait(to?, from?, timeoutMillis)`
- `latestDiscoInfoResult(from)`
- `latestSoftwareVersionResult(from)`
- `capturedResultsSnapshot()`
- `clearCapturedResults(jid?)`

数据模型：

- `CapturedIqResult(namespace, id, from, to, xml)`

### 2.2 `CapabilitiesComponent`（XEP-0115）

访问器：`takina.capabilities`

配置项：

- `autoAppendToOutboundPresence: Boolean`
- `cacheInboundPresenceCapabilities: Boolean`
- `defaultOutboundCapabilities: EntityCapabilities?`

主要 API：

- `build(node, ver, hash = "sha-1")`
- `toXmlElement(capabilities)`
- `appendToPresence(presence, capabilities)`
- `parseFromPresenceXml(xml)`
- `cachedInboundCapabilities(jid)`
- `cachedInboundCapabilitiesSnapshot()`
- `clearCachedInboundCapabilities(jid?)`

数据模型：

- `EntityCapabilities(node, ver, hash)`
- 扩展：`EntityCapabilities.toCapsXmlElement()`

### 2.3 `CarbonsComponent`（XEP-0280）

访问器：`takina.carbons`

配置项：

- `autoEnableOnConnect: Boolean`

主要 API：

- `enable(from?)`
- `disable(from?)`
- `enableAwait(from?, timeoutMillis)`
- `disableAwait(from?, timeoutMillis)`
- `parseEnvelope(messageXml)`
- `parseFromMessageXml(messageXml)`

数据模型：

- `CarbonFrame.Sent / Received`
- `CarbonEnvelope(frame, from, to, messageId, type, forwardedMessageXml)`

### 2.4 `ConnectionDiscoveryComponent`（XEP-0156 / 0368）

访问器：`takina.connectionDiscovery`

主要 API：

- `parseDiscoFeatures(discoInfoResultXml)`
- `parseHostMetaLinks(hostMetaXml)`
- `parseHostMetaJsonLinks(hostMetaJson)`
- `choosePreferredEndpoint(endpoints, preferWebSocket = true, allowBoshFallback = true)`

数据模型：

- `ConnectionDiscoveryResult(supportsDirectTls, supportsWebSocket, supportsBosh, rawFeatures)`
- `EndpointType.WEBSOCKET / BOSH`
- `AlternativeEndpoint(type, url, securityMode)`

### 2.5 `CsiPushComponent`（XEP-0352 / XEP-0357）

访问器：`takina.csiPush`

主要 API：

- `activeElement()` / `inactiveElement()`
- `sendActive(from?)` / `sendInactive(from?)`
- `enablePushAwait(pushServiceJid, node, secret?, from?, to?, timeoutMillis)`
- `disablePushAwait(pushServiceJid, node?, from?, to?, timeoutMillis)`
- `parseDiscoFeatures(discoInfoResultXml)`

数据模型：

- `PushDiscovery(supportsCsi, supportsPush, rawFeatures)`

### 2.6 `HttpUploadComponent`（XEP-0363）

访问器：`takina.httpUpload`

配置项：

- `allowInsecureHttpSlotUrls: Boolean`

主要 API：

- `requestSlotAwait(filename, size, contentType?, from?, to?, timeoutMillis)`
- `parseSlotResult(iqResultXml)`
- `parseUploadError(iqErrorXml)`

数据模型：

- `Slot(putUrl, getUrl, putHeaders, putHeadersOrdered)`
- `UploadError(type, condition, text, maxFileSize, retryable)`

### 2.7 `MamComponent`（XEP-0313 + RSM）

访问器：`takina.mam`

主要 API：

- `queryArchiveAwait(from?, to?, with?, startIso8601?, endIso8601?, pageAfter?, pageBefore?, pageMax?, queryId?, timeoutMillis?)`
- `parseResultEnvelope(xml)`
- `parseFin(xml)`
- `parseStanzaIds(messageXml)`
- `createAggregator(queryId?)`
- `nextPageAfter(fin)` / `nextPageBefore(fin)`

聚合器 API：

- `MamResultAggregator.ingest(xml)`
- `MamResultAggregator.snapshot()`
- `MamResultAggregator.clear()`

数据模型：

- `MamResultEnvelope(queryId, resultId, delayStamp, forwardedMessageXml, stanzaIds)`
- `AggregatedMamResult(results, fin, nextPageAfter, nextPageBefore, isComplete)`
- `StanzaId(id, by)`
- `MamFin(queryId, complete, stable, rsm)`
- `RsmSet(first, last, count)`

### 2.8 `MessageReceiptsComponent`（XEP-0184）

访问器：`takina.receipts`

配置项：

- `autoReplyEnabled: Boolean`

主要 API：

- `requestElement()`
- `receivedElement(messageId)`
- `appendRequest(stanza)`
- `appendReceived(stanza, messageId)`
- `parseEnvelope(xml)`
- `parseFromMessageXml(xml)`
- `buildAutoReply(selfJid, inboundMessageXml)`
- `buildReceivedReply(selfJid, inboundMessageXml)`
- `sendReceived(from, to, messageId, type = CHAT)`
- `sendReceivedReply(selfJid, inboundMessageXml): Boolean`

数据模型：

- `ReceiptFrame.Request`
- `ReceiptFrame.Received(id)`
- `ParsedReceiptEnvelope(frame, from, to, messageId, type)`

### 2.9 `MucComponent`（XEP-0045 / Bookmarks2 / Direct Invite）

访问器：`takina.muc`

主要 API：

- `joinRoom(roomJid, nick, from?, password?, historyMaxStanzas?)`
- `leaveRoom(roomFullJid, from?)`
- `groupMessage(roomJid, body, from?, subject?, thread?)`
- `directInvite(invitee, roomJid, from?, reason?, continueThread?)`
- `publishBookmarks2Await(bookmarks, from?, to?, includePublishOptions = true, publishOptionsAccessModel = "whitelist", timeoutMillis)`
- `retractBookmarkAwait(roomJid, from?, to?, notify = true, timeoutMillis)`
- `getBookmarks2Await(from?, to?, timeoutMillis)`
- `parseDirectInvite(xml)`
- `parseBookmarks2Result(xml)`

数据模型：

- `BookmarkRoom(jid, name, autoJoin, nick?)`
- `DirectInvite(roomJid, reason, continueThread)`

### 2.10 `OmemoComponent`（XEP-0384 / XEP-0420）

访问器：`takina.omemo`

配置项：

- `store: OmemoStateStore`
- `localDeviceIdProvider: () -> Int`
- `preKeyCount: Int`
- `defaultProtocolPreference: OmemoProtocolPreference`
- `autoFallbackVersion: OmemoProtocolVersion`
- `strictBundleSignatureValidation: Boolean`

协议枚举：

- `OmemoProtocolVersion.V1 / V2`
- `OmemoProtocolPreference.AUTO / V1 / V2`

设备与材料 API：

- `bootstrapLocalDevice(account)`
- `publishOwnDeviceList(from, preference)`
- `publishOwnBundle(from, preference)`
- `suspend publishOwnMaterial(from, preference)`
- `fetchDeviceListAwait(owner, from?, timeoutMillis, version)`
- `fetchBundleAwait(owner, deviceId, from?, timeoutMillis, version)`
- `publishDeviceList(deviceIds, from?, version)`
- `publishBundle(deviceId, bundle, from?, version)`
- `parseDeviceListResult(xml)`
- `parseBundleResult(xml)`
- `cacheDeviceList(account, parsed)`
- `cacheBundle(account, parsed)`
- `suspend syncContactMaterial(from, contact, timeoutMillis, preference)`

加解密 API：

- `encryptMessage(from, to, plaintext, includeSenderDevice = true, messageType = CHAT, preference)`
- `sendEncryptedMessage(...)`（源码注释标注为不建议直接使用）
- `decryptMessage(self, messageXml)`
- `saveRemoteOmemoSupport(account, contact, support)`

数据模型：

- `ParsedDeviceList(owner, devices, version)`
- `ParsedBundle(owner, deviceId, bundle, version)`
- `PublicBundle(...)`
- `RemotePreKey(id, publicKey)`
- `LocalPreKey(id, publicKey, privateKey, record)`
- `LocalDeviceState(...)`
- `DecryptedOmemoMessage(from, senderDeviceId, plaintext, version)`
- `SyncResult(version, devices, fetchedBundles)`
- `RemoteOmemoSupport(supportsV2, supportsV1)`

存储接口：

- `OmemoStateStore`（本地设备、远端设备列表、bundle、session、support 的读写）
- 默认实现：`InMemoryOmemoStateStore`

### 2.11 `RosterComponent`（RFC 6121 roster）

访问器：`takina.roster`

配置项：

- `captureInboundRosterPush: Boolean`

主要 API：

- `getAwait(from?, to?, version?, requestVersioning = false, timeoutMillis)`
- `rosterSetItem(jid, name?, groups, from?, to?)`
- `rosterRemoveItem(jid, from?, to?)`
- `requestSubscription(to, from?)`
- `approveSubscription(to, from?)`
- `rejectSubscription(to, from?)`
- `unsubscribe(to, from?)`
- `parseRosterResult(xml)`
- `parseSubscriptionEvent(xml)`
- `snapshot(jid)`

数据模型：

- `ParsedRosterResult(type, id, from, version, items)`
- `RosterItem(jid, name, subscription, ask, groups)`
- `RosterSnapshot(items, version)`
- `SubscriptionAction.REQUEST / APPROVED / UNSUBSCRIBE / REJECTED`
- `SubscriptionEvent(action, from, to, id)`

### 2.12 `StreamManagementComponent`（XEP-0198）

访问器：`takina.streamManagement`

配置项：

- `autoReconnectOnConnectionDropped`
- `autoReconnectMaxAttempts`
- `autoReconnectDelayMillis`
- `autoAckRequestInterval`
- `persistStateToStore`
- `restorePersistedStateOnStartup`
- `stateStore: StreamManagementStateStore?`
- `nowMillisProvider`

控制帧构造/发送 API：

- `enable(allowResume = true, maxResumeSeconds?)`
- `resume(previousId, handledByClient)`
- `ackRequest()`
- `ack(handledByClient)`
- `sendEnable(from?, allowResume, maxResumeSeconds?)`
- `sendResume(from?, previousId, handledByClient)`
- `sendAckRequest(from?)`
- `sendAck(from?, handledByClient)`

状态管理/解析 API（高级）：

- `stateFor(jid)`
- `clearState(jid)`
- `parseInboundFrame(xml)`
- `onInboundFrame(jid, xml)`
- `applyInboundFrame(state, frame)`
- `onOutboundStanzaSent(state, xml)`
- `onInboundStanzaHandled(state)`
- `onServerAcknowledged(state, handledByServer)`
- `snapshotUnackedForResume(state)`
- `scheduleResumeReplay(state, stanzas)`
- `consumePendingReplayAfterEnable(state)`
- `prependPendingReplayAfterEnable(state, stanzas)`
- `markEnableRequested(state)`
- `markResumeRequested(state, previousId)`
- `shouldSendAckRequest(state)`
- `markAckRequestSent(state)`
- `resetReconnectAttempts(state)`
- `shouldAutoReconnect(state)`
- `markReconnectAttempt(state)`

数据模型：

- `InboundFrame`：`Enabled` / `Resumed` / `Acknowledged` / `AckRequest` / `Failed`
- `SessionState`
- `PersistedSessionState`
- `StreamManagementStateStore`

---

## 3. 组件访问方式与缺省装载

当前内置扩展访问器（`TakinaContext` 扩展属性）：

- `capabilities`
- `carbons`
- `connectionDiscovery`
- `csiPush`
- `discovery`
- `httpUpload`
- `mam`
- `receipts`
- `muc`
- `omemo`
- `roster`
- `streamManagement`

注意：这些访问器均为 `requireComponent(...)` 语义，未注册会抛异常

---

## 4. 已知行为约束与使用建议

- 连接 API 当前是阻塞式函数（非 `suspend`）：避免在 UI 主线程直接调用
- 失败模型当前以异常为主：建议在调用层统一封装 `runCatching`
- 事件默认是“离散事件 + raw XML”模式：复杂业务建议自己建立二次 typed 事件层
- `StreamManagementComponent` 暴露了较多高级状态 API：通常仅库作者或高级接入场景需要直接调用
- OMEMO 在 `AUTO` 下默认兼容优先，会结合缓存能力/Disco 能力与 `autoFallbackVersion` 做版本选择

---

## 5. 未来可能变更（请接入方提前预留）

以下方向已在项目状态中明确为改进重点，未来可能引入 API 调整或新增并逐步替代旧用法：

1. 连接 API 协程化：可能会给 `connect/disconnect/connectAll/disconnectAll` 增加或迁移到 `suspend` 版本
2. 统一结果模型：从“异常驱动”补齐 sealed result 风格返回
3. 连接状态快照流：提供 `StateFlow/Flow` 聚合态，而非仅离散事件
4. 请求上下文统一化：traceId / cancellation / timeout policy
5. 事件类型化增强：在保留 raw XML 的同时提供 typed inbound 事件
6. 高层业务事件补齐：如 `RosterUpdated` / `MamPageLoaded` / `OmemoSessionChanged` 等
7. 事件订阅 ergonomics：可能补充 `Disposable` / `once` 机制
8. 组件软依赖访问器：可能新增 `xxxOrNull` 类入口
9. DSL 编译期约束增强：减少运行时必填校验异常
10. 阻塞逻辑替换：如重连等待从 `Thread.sleep` 迁移为协程调度

如果你的业务要长期稳定依赖，建议在你侧封装一层 Facade，隔离上述潜在变更点