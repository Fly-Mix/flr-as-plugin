package com.flr

/**
 * flutter 工程约定的文件名 / 目录名常量集中处。
 *
 * 这些名字原以裸字符串散落在 FlrFileUtil / FlrPubspecFileTree / GenerateUseCase 的路径拼接中。
 * 集中于此后：单一事实源、拼写错误即编译失败。
 *
 * 注意：测试代码有意保留独立字面量，以便本类常量写错时能被回归测试捕获。
 *
 * `const val` 在 Kotlin `object` 中编译为静态字段，Java 端仍以 `FlrPaths.PUBSPEC_YAML` 访问（无 `.INSTANCE`）。
 */
object FlrPaths {

    /** flutter 工程配置文件名 */
    const val PUBSPEC_YAML = "pubspec.yaml"

    /** flutter 工程 Dart 源码目录名 */
    const val LIB_DIR = "lib"

    /** Flr 生成的资源引用文件名（位于 lib/ 下） */
    const val R_DART = "r.g.dart"

    /** flr.assets 特殊资源目录名（位于 lib/ 下） */
    const val FLR_ASSETS = "flr.assets"
}
