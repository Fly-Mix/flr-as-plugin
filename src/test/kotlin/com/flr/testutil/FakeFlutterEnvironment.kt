package com.flr.testutil

import com.flr.core.env.FlutterEnvironment

/**
 * [FlutterEnvironment] 的测试替身（test double）：返回固定值，
 * 使 generate 核心逻辑可在无真实 Flutter/Dart SDK 的 headless 环境下运行。
 *
 * 默认对应基准工程 flutter_r_demo 的工具链（flr-core 3.2.0 nullsafety）：
 * isFlutterProject=true、shouldSupportNullsafety=true、r_dart_library=0.4.0-nullsafety.0。
 */
class FakeFlutterEnvironment(
    private val flutterProject: Boolean = true,
    private val nullsafety: Boolean = true,
    private val rDartLibVersion: String = "0.4.0-nullsafety.0"
) : FlutterEnvironment {

    override fun isFlutterProject(): Boolean = flutterProject

    override fun shouldSupportNullsafety(): Boolean = nullsafety

    override fun getRDartLibraryVersion(): String = rDartLibVersion
}
