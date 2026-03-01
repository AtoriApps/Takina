# Takina 1 实现状态

> **最后更新**：2026-03-01

## 状态定义

- **DONE**：基本全面完成（容许保留待未来完善或优化的小细节）
- **WIP**：部分完成，正在推进中

---

## 当前实现进度

- **1. 创建核心架构（同时写单元测试）**：**WIP（核心主干已成型）**
  - **API 与 DSL**：`createTakina(...)`、`takina.events/runtime/request`、账号句柄、会话句柄、typed `FeatureApi` 访问已实现
  - **Feature 抽象与拓扑**：统一 `TakinaFeature`、依赖/互斥/环检测、安装期拓扑校验已实现
  - **Control Plane**：能力开关、节点开关、节点排序、`applyMode`、`Scope` fallback 与解释性接口已实现
  - **Execution Plane / Pipeline**：入站分类与分流、出站 business/control 分流、节点度量、解释与可见性冲突标记已实现
  - **XML Core**：新增 `core/xml`（节点模型、DSL、序列化、解析器），核心请求层与 JVM 传输层已切换到统一 XML 包
  - **连接与传输**：JVM 真实 TCP/TLS/XMPP 流程（开流、SASL、资源绑定、收发）已实现；状态机/重连编排已接入
  - **事件与错误模型**：v1 草案核心事件集、`TAKINA-<DOMAIN>-<NNN>` 错误码、`retryable` 标签语义已实现
  - **测试**：
    - `takina-core/commonTest`：语义单测（控制面、状态机、拓扑、请求DSL、Feature API、事件总线、重连等）
    - `takina-tests/jvmTest`：真实账号/真实服务集成冒烟测试（通过环境变量注入，未配置时自动 skip）
  - 配置项改为按 `Scope` 存储/求值（含 fallback + `applyMode` 边界）
  - `message/presence/iq` 全部走 business outbound pipeline
  - `capability` 支持 `enable/disable(TakinaFeatureProvider)` 入口
  - 集成测试从 `takina-core` 迁移到 `takina-tests`

## 待办清单

1. 创建核心架构（同时写单元测试）- **WIP**（剩余：继续补齐细部语义与更高强度覆盖）
2. 实现[首批功能]（同时写单元测试）- **TODO**
3. 写登录真实账号的集成和冒烟测试 - **WIP**（测试框架已落地到 `takina-tests`）
4. 实现[第二批功能]（同时写单元测试）- **TODO**
5. 写用上了第二批功能的更多集成和冒烟测试 - **TODO**
6. 实现[最后一批功能]（同时写单元测试）- **TODO**
