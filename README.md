# Takina

## Other Languages

[English](./README_EN.md): May be outdated, for the latest information, please refer to this Chinese version.

## 项目简介

![泷奈酱](./docs/src/restructured/images/logo.svg)

Takina 是一个使用 [Kotlin](https://kotlinlang.org/) 编写的 [XMPP](https://xmpp.org) 客户端库，提供了对 XMPP 核心标准的实现以及
XML 的处理功能。此外，它还支持许多常用的扩展协议（XEP）。

该库使用 [Kotlin Multiplatform](https://kotlinlang.org/docs/reference/multiplatform.html) 的功能，旨在为尽可能多的平台提供支持。目前支持以下平台：

* JVM 和 安卓
* JavaScript：由于 NPM 要弄一大坨 `node_modules`，所以暂时不想开发，除非 Gradle 允许我们用 PNPM 进行构建。

此代码库暂包含该库的源文件：意思是以后可能会闭源，看我们心情。

**Takina 现由 Atori Apps 团队维护。本项目未来可能作为 Atori（一款基于 Kotlin 的跨平台聊天应用）的基础，用于支持 XMPP
平台能力。我们计划逐步支持更多 XEP 并持续开发。**

## 功能特性

**警告：这些功能特性经我们接手后未经调试，不确定是否可用，除非有明确标明；另外可能有写错或者写漏的功能特性。**

我们的目标是打造 [合规性测试](https://xmpp.org/extensions/xep-0479.html) 中打平甚至超越 [Conversations](https://codeberg.org/iNPUTmice/Conversations) 水准的库和 [客户端](https://github.com/AtoriApps/Atori)。

### Takina 支持以下标准（根据 `takina.doap`）：

* 【[RFC 6120：XMPP核心](https://xmpp.org/rfcs/rfc6120.html)】
* 【[RFC 6121：XMPP即时消息与状态](https://xmpp.org/rfcs/rfc6121.html)】
* 【[RFC 7590](https://xmpp.org/rfcs/rfc7590.html)】：我不知道这是什么，链接遁入虚无了，该死的彼~~样~~得·圣安德烈。
* 【[RFC 7622](https://xmpp.org/rfcs/rfc7622.html)】：我不知道这是什么，链接遁入虚无了，该死的彼~~样~~得·圣安德烈。

### Takina 支持以下 XEP（根据lib.takina.core.xmpp.modules下的包以及 `takina.doap`）：

* 【[XEP 0004：数据表单](https://xmpp.org/extensions/xep-0004.html)】
* 【[XEP 0030：服务发现](https://xmpp.org/extensions/xep-0030.html)】
* 【[XEP 0045：多用户聊天](https://xmpp.org/extensions/xep-0045.html)】
* 【[XEP 0059：结果集管理](https://xmpp.org/extensions/xep-0059.html)】
* 【[XEP 0060：发布-订阅](https://xmpp.org/extensions/xep-0060.html)】
* 【[XEP 0077：带内注册](https://xmpp.org/extensions/xep-0077.html)】
* 【[XEP 0082：Xmpp日期和时间](https://xmpp.org/extensions/xep-0082.html)】
* 【[XEP 0084：用户头像](https://xmpp.org/extensions/xep-0084.html)】
* 【[XEP 0085：聊天状态通知](https://xmpp.org/extensions/xep-0085.html)】
* 【[XEP 0115：实体权能](https://xmpp.org/extensions/xep-0115.html)】
* 【[XEP 0156：替代性的“XMPP发现”连接方法](https://xmpp.org/extensions/xep-0156.html)】
* 【[XEP 0184：消息送达回执](https://xmpp.org/extensions/xep-0184.html)】
* 【[XEP 0191：拉黑命令](https://xmpp.org/extensions/xep-0191.html)】
* 【[XEP 0198：流管理](https://xmpp.org/extensions/xep-0198.html)】
* 【[XEP 0199：XMPP拨弄](https://xmpp.org/extensions/xep-0199.html)】
* 【[XEP 0203：延迟送达](https://xmpp.org/extensions/xep-0203.html)】
* 【[XEP 0215：外部服务发现](https://xmpp.org/extensions/xep-0215.html)】
* 【[XEP 0237：花名册版本控制](https://xmpp.org/extensions/xep-0237.html)】
* 【[XEP 0249：直接多用户聊天邀请](https://xmpp.org/extensions/xep-0249.html)】
* 【[XEP 0280：消息碳](https://xmpp.org/extensions/xep-0280.html)】
* 【[XEP 0313：消息存档管理](https://xmpp.org/extensions/xep-0313.html)】
* 【[XEP 0333：可视标记](https://xmpp.org/extensions/xep-0333.html)】
* 【[XEP 0334：消息处理提示](https://xmpp.org/extensions/xep-0334.html)】
* 【[XEP 0357：推送通知](https://xmpp.org/extensions/xep-0357.html)】
* 【[XEP 0359：独特且稳定的节Id](https://xmpp.org/extensions/xep-0359.html)】
* 【[XEP 0363：HTTP文件上传](https://xmpp.org/extensions/xep-0363.html)】
* 【[XEP 0369：中介信息交互](https://xmpp.org/extensions/xep-0369.html)】
* 【[XEP 0372：引用](https://xmpp.org/extensions/xep-0372.html)】
* 【[XEP 0384：OMEMO 加密](https://xmpp.org/extensions/xep-0384.html)】
* 【[XEP 0386：绑定 2](https://xmpp.org/extensions/xep-0386.html)】
* 【[XEP 0392：一致性颜色生成](https://xmpp.org/extensions/xep-0392.html)】
* 【[XEP 0440：SASL 通道绑定类型能力](https://xmpp.org/extensions/xep-0440.html)】
* 【[XEP 0454：OMEMO 媒体分享](https://xmpp.org/extensions/xep-0454.html)】
* 【[XEP 0483：HTTP在线会议](https://xmpp.org/extensions/xep-0483.html)】：`Meet`，不确定是不是这个XEP。
* 【~~Auth~~】：暂不知道具体是什么XEP。
* 【~~Commands~~】：暂不知道具体是什么XEP。
* 【~~Jingle~~】：暂不知道具体是什么XEP。
* 【~~Message~~】：这就是消息罢，不是甚么XEP。
* 【~~Presence~~】：这就是状态罢，不是甚么XEP。
* 【~~Pubsub~~】：暂不知道具体是什么XEP。
* 【~~Roster~~】：暂不知道具体是什么XEP。
* 【~~Rsm~~】：大抵不是甚么XEP，可能是原作者欠操了。
* 【~~Service Finder~~】：暂不知道具体是什么XEP。
* 【~~Stream Error~~】：大抵不是甚么XEP，可能是原作者欠操了。
* 【~~Stream Features~~】：大抵不是甚么XEP，可能是原作者欠操了。
* 【~~Tick~~】：暂不知道具体是什么XEP，可能不是XEP。
* 【~~vCard~~】：暂不知道具体是什么XEP。

Takina 仍在积极开发中，功能列表会不断更新。

## 快速上手

### 最简单的客户端示例

以下是一个发送单条消息的简单客户端示例：

```kotlin
val takina = createTakina {
    auth {
        userJID = "client@atoriapps.net".toBareJID()
        password { "secret" }
    }
}
takina.connectAndWait()

takina.request.message {
    to = "romeo@example.net".toJID()
    "body" {
        +"Art thou not Romeo, and a Montague?"
    }
}.send()

takina.disconnect()
``` 

### 示例代码

我们提供了一些 Takina 库用法的小示例，可以在 [代码片段](./docs/codeSnippets/README.md)中找到。

## 编译

敬请期待：我们暂不提供编译指南，因为我们暂未理顺编译模式。

## 许可证

版权所有 (c) 2024 Atori Apps。

本项目使用 MIT 许可证。