.. _header-setting-up-client:

设置客户端
===================

支持的平台
-------------------

Takina 库可以在不同的平台上使用：

* JVM 和 安卓
* JavaScript：暂不想开发，理由见项目 `Readme`。

添加客户端依赖
--------------------------

要在您的项目中使用 Takina 库，您需要配置仓库并添加库依赖。所有版本的库都不确定是否可以在 Maven 仓库中找到：

正式版
   .. code:: kotlin

      repositories {
          maven("https://maven-repo.takina.org/repository/release/")
      }

快照版
   .. code:: kotlin

      repositories {
          maven("https://maven-repo.takina.org/repository/snapshot/")
      }

最后，您需要添加对 `lib.takina:takina-core` 构件的依赖：

.. code:: kotlin

    implementation("lib.takina:takina-core:$takina_version")

其中 `$takina_version` 是所需的 Takina 版本。