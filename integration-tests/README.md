## 集成测试

要运行集成测试，您需要在 `integration-tests` 项目文件夹中创建一个名为 `local.properties` 的文件。

该文件必须包含用户凭证（帐户必须存在）。例如：

```properties
userJID=account@xmppserver.com
password=******
```

要运行测试，请从根项目目录执行 `:integration-tests:check` 任务：

```shell
./gradlew :integration-tests:clean :integration-tests:check
```
