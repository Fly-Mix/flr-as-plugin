package com.flr

/**
 * pubspec.yaml 的 schema key 常量集中处。
 *
 * 这些 key 是 flr 与 flutter 共用的数据契约，集中于此后：单一事实源、拼写错误即编译失败、schema 变更改一处即可。
 *
 * 注意：测试代码有意保留独立字面量，以便本类常量写错时能被回归测试捕获。
 */
object PubspecKeys {

    /** 顶层 flr 配置节点 */
    const val FLR = "flr"

    /** 顶层 flutter 配置节点 */
    const val FLUTTER = "flutter"

    /** assets 声明 key（flr 与 flutter 节点下同名） */
    const val ASSETS = "assets"

    /** fonts 声明 key（flr、flutter 节点及 font family 配置下同名） */
    const val FONTS = "fonts"
}
