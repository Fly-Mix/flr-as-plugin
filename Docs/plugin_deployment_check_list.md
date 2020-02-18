## Flr Plugin Deployment Check List

1. 确定Deployment的版本号：$version
1.  编辑`build.gradle`，更新与插件发布相关的字段

	主要修改`build.gradle`中的`patchPluginXml`代码块中的内容：

	-  更新version为：$version
	- 更新changeNotes（注意：最新版本号的标题样式改为h2，其他版本号的标题样式改为h3）

1. 在项目根目录下运行脚本验证插件：`./gradlew verifyPlugin`
1. 在项目根目录下运行脚本打包插件：`./gradlew buildPlugin`

## Publish Flr Plugin

前往[IntelliJ插件市场](https://plugins.jetbrains.com/)，手动上传Flr Plugin。