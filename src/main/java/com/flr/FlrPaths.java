package com.flr;

/**
 * flutter 工程约定的文件名 / 目录名常量集中处。
 *
 * <p>这些名字原以裸字符串散落在 FlrFileUtil / FlrPubspecFileTree / GenerateUseCase 的路径拼接中。
 * 集中于此后：单一事实源、拼写错误即编译失败。
 *
 * <p>注意：测试代码有意保留独立字面量，以便本类常量写错时能被回归测试捕获。
 */
public final class FlrPaths {

    private FlrPaths() {
    }

    /** flutter 工程配置文件名 */
    public static final String PUBSPEC_YAML = "pubspec.yaml";

    /** flutter 工程 Dart 源码目录名 */
    public static final String LIB_DIR = "lib";

    /** Flr 生成的资源引用文件名（位于 lib/ 下） */
    public static final String R_DART = "r.g.dart";
}
