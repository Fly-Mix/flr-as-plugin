package com.flr.testutil;

import com.flr.core.env.FlutterEnvironment;

/**
 * {@link FlutterEnvironment} 的测试替身（test double）：返回固定值，
 * 使 generate 核心逻辑可在无真实 Flutter/Dart SDK 的 headless 环境下运行。
 *
 * 默认对应基准工程 flutter_r_demo 的工具链（flr-core 3.2.0 nullsafety）：
 * isFlutterProject=true、shouldSupportNullsafety=true、r_dart_library=0.4.0-nullsafety.0。
 */
public final class FakeFlutterEnvironment implements FlutterEnvironment {

    private final boolean isFlutterProject;
    private final boolean shouldSupportNullsafety;
    private final String rDartLibraryVersion;

    public FakeFlutterEnvironment() {
        this(true, true, "0.4.0-nullsafety.0");
    }

    public FakeFlutterEnvironment(boolean isFlutterProject,
                                  boolean shouldSupportNullsafety,
                                  String rDartLibraryVersion) {
        this.isFlutterProject = isFlutterProject;
        this.shouldSupportNullsafety = shouldSupportNullsafety;
        this.rDartLibraryVersion = rDartLibraryVersion;
    }

    @Override
    public boolean isFlutterProject() {
        return isFlutterProject;
    }

    @Override
    public boolean shouldSupportNullsafety() {
        return shouldSupportNullsafety;
    }

    @Override
    public String getRDartLibraryVersion() {
        return rDartLibraryVersion;
    }
}
