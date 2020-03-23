# Flr Android Studio Plugin Workflow

由于`flr-as-plugin`依赖了[flutter插件](https://plugins.jetbrains.com/plugin/9212-flutter)和[dart插件](https://plugins.jetbrains.com/plugin/6351-dart)，`flr-as-plugin`需要为`Android Studio`的不同版本做适配和生产发布。

为了保证`flr-as-plugin`的具备良好的生产流程，所以为其定制了一个工作流，以指导如何对仓库分支进行管理以及如何发布生产版本。

> 关于插件与IntelliJ平台产品的兼容性的细节，可参考[《Plugin Compatibility with IntelliJ Platform Products》](https://www.jetbrains.org/intellij/sdk/docs/basics/getting_started/plugin_compatibility.html)。

## 工作流

生产`flr-as-plugin`的工作流整体如下：

![flr-plugin-workflow-model.pdf](assets/flr-plugin-workflow-model.pdf)

下面将会做进一步详细介绍。

#### 主分支类型

从产品建立开始，代码仓库就持有一个具备无限生命周期特性的主分支：`master`。`master`分支主要用于实现`Flr-Core-Logic`（Flr核心逻辑）的代码以及修复相关bug，在该分支提交的commit都应该是与平台兼容性无关的。

而当Google发布大版本更新的`Android Studio`（大版本更新的定义是：`Android Studio`升级了使用的`IntelliJ IDEA Community Edition`）后，代码仓库会基于`master`分支创建一个具备无限生命周期特性的主分支：`platform-master`。

所以在代码仓库中主要有2种主分支：

- master
- platform-master

下面将会详细介绍`platform-master`：

- 分支作用：主要用于集成与平台兼容的commit和用于进行版本发布，以使`flr-as-plugin`能兼容新版本的`Android Studio`。

- 命名规则：`#{IC_Branch_Number}/master`。其中`#{IC_Branch_Number}`为当前`Android Studio`版本使用的`IntelliJ IDEA Community Edition`版本的分支号。

  那么如何获取这个分支号呢？可以从`Android Studio`的版本信息获取。比如，`Android Studio v3.6`的版本信息是：
  
  ```
  Android Studio 3.6
  Build #AI-192.7142.36.36.6200805, built on February 12, 2020
  ```
  其中`#AI-`后跟随的第一个数字`192`就是`Android Studio v3.6`所使用的`IntelliJ IDEA Community Edition`版本的分支号。
  
  所以，为`Android Studio v3.6`创建的`platform-master`的分支名为：`192/master`。
  
  > 关于`Branch_Number`的更多细节，可参考[《Build Number Ranges》](https://www.jetbrains.org/intellij/sdk/docs/basics/getting_started/build_number_ranges.html)。
#### 版本开发和发布流程

当需要开发新版本时（功能升级或者兼容新版`Android Studio`），按照以下工作流进行代码开发和提交：

- 使用[Git-Flow](https://nvie.com/posts/a-successful-git-branching-model/)或者[Github-Flow](https://guides.github.com/introduction/flow/)的工作流，在`master`分支上进行需求开发，直到可进行打tag并“发布”给`platform-master`使用
- 使用`non-fast-forward`的方式合并`master`的新发布版本到各个`platform-master`分支
- 在各个`platform-master`分支上，进行平台适配开发，然后打tag发布插件
- 各个`platform-master`分支发布的插件的版本号命名如下：`#{IC_Branch_Number}.#{master_tag_verison}`，如当前`master`分支打tag发布的版本号为：`1.1.0`，在`192/master`分支打tag发布的版本号为：`192.1.1.0`




