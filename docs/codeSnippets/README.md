代码片段
=============

该项目包含一系列小的可运行代码示例，展示了如何使用特定模块或解决常见问题。

由于这些代码片段尝试连接到服务器，您需要将现有用户的凭据存储在 `codeSnippets` 项目目录下的 `local.properties` 文件中。例如：

```properties
userJID=account@xmppserver.com
password=******
```

要运行示例，您可以使用 Gradle 的 `run` 任务来运行感兴趣的项目。例如，如果您想运行 `simple-client`，只需执行：

```shell
./gradlew :simple-client:run
```