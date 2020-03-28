## Flr Plugin Engine Deployment Check List

1. 确定Deployment的版本号：$version
1. 编辑`build.gradle`，更新与`plugin-engine`发布相关的字段：
   - 更新`pluginEngineVersion`为：$version
   - 更新`changeNotes`中的`PluginEngine Change Notes`选项（注意：最新版本号的标题样式为h3，其他版本号的标题样式h4）
1. 打tag，合并到各个`platform-master`分支

## Flr Plugin Product Deployment Check List

1. 编辑`build.gradle`，更新与`plugin-product`发布相关的字段：
   - 更新`pluginProductPlatform`为当前`platform-master`分支对应的平台，如`PlatformType.IC_191`
   - 更新`changeNotes`中的`Flr Change Notes`选项（注意：最新版本号的标题样式为h3，其他版本号的标题样式h4）
1. 在项目根目录下运行脚本验证插件：`./gradlew verifyPlugin`
1. 在项目根目录下运行脚本打包插件：`./gradlew buildPlugin`

## Publish Flr Plugin Product

前往[IntelliJ插件市场](https://plugins.jetbrains.com/)，手动上传Flr Plugin Product。