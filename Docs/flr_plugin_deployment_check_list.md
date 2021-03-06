## Flr Plugin Engine Deployment Check List

1. 确定Deployment的版本号：$version
1. 编辑`build.gradle`，更新与`flr-plugin-engine`发布相关的字段：
   - 更新`pluginEngineVersion`为：$version
   - 更新`changeNotes`中的`Flr Plugin Engine Change Notes`选项
1. 合并到`master`分支，然后打tag，合并到各个`platform-master`分支

## Flr Plugin Deployment Check List

1. 编辑`build.gradle`，更新与`flr-plugin`发布相关的字段：
   - 更新`pluginProductPlatform`为当前`platform-master`分支对应的平台，如`PlatformType.IC_191`
   - 更新`changeNotes`中的`Flr Plugin Change Notes`选项
1. 在项目根目录下运行脚本验证插件：`./gradlew verifyPlugin`
1. 在项目根目录下运行脚本打包插件：`./gradlew buildPlugin`

## Publish Flr Plugin To Marketplace

前往[IntelliJ插件市场](https://plugins.jetbrains.com/)，手动上传Flr Plugin。

