package com.flr.core.env;

/**
 * Flutter/Dart 环境探测接缝（object seam）。
 *
 * 把对 IDE 的 FlutterSdk/DartSdk 的依赖隔离到该接口后面，
 * 使 generate 核心逻辑可在无真实 SDK 的环境（如 headless 测试）下运行——
 * 测试注入固定返回值的替身即可。
 *
 * IDE 实现见 {@link com.flr.infra.IdeFlutterEnvironment}。
 */
public interface FlutterEnvironment {

    /** 当前工程是否是 flutter 工程（IDE 中能探测到 FlutterSdk 即为 true）。 */
    boolean isFlutterProject();

    /** 是否应生成支持 nullsafety 的代码（flutter >= 1.10.15 且 dart >= 2.12.0）。 */
    boolean shouldSupportNullsafety();

    /**
     * 根据 flutter/dart 版本返回匹配的 r_dart_library 依赖版本。
     * 详见 https://github.com/YK-Unit/r_dart_library#dependency-relationship-table
     */
    String getRDartLibraryVersion();
}
