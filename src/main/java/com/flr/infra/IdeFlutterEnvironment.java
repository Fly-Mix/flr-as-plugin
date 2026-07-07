package com.flr.infra;

import com.flr.command.util.FlrUtil;
import com.flr.core.env.FlutterEnvironment;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.lang.dart.sdk.DartSdk;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkVersion;

/**
 * {@link FlutterEnvironment} 的 IDE 实现：基于 IDE 的 FlutterSdk/DartSdk 探测环境。
 *
 * 逻辑整段迁移自原 FlrCommand.initFlagEnvIfNeed 与 getRDartLibraryVersion，行为不变。
 * 环境探测结果按需惰性初始化并缓存（与原 didInitFlutterEnv 语义一致）。
 */
public final class IdeFlutterEnvironment implements FlutterEnvironment {

    private final Project project;

    private boolean didInitFlutterEnv = false;
    private boolean isFlutterProject = false;
    private boolean shouldSupportNullsafety = false;

    public IdeFlutterEnvironment(Project project) {
        this.project = project;
    }

    private void initFlagEnvIfNeed() {
        if (this.didInitFlutterEnv) {
            return;
        }

        this.didInitFlutterEnv = true;

        this.shouldSupportNullsafety = false;

        FlutterSdk flutterSdk = FlutterSdk.getFlutterSdk(project);
        if (flutterSdk == null) {
            this.isFlutterProject = false;
            return;
        }

        this.isFlutterProject = true;
        VirtualFile flutterSdkHome = flutterSdk.getHome();
        FlutterSdkVersion flutterSdkVersion = FlutterSdkVersion.readFromSdk(flutterSdkHome);
        String flutterVersionWithoutHotfixStr = flutterSdkVersion.toString();

        DartSdk dartSdk = DartSdk.getDartSdk(project);
        String dartSdkVersionStr = (dartSdk == null) ? "0.0.0" : dartSdk.getVersion();

        int compareResult = FlrUtil.versionCompare(flutterVersionWithoutHotfixStr, "1.10.15");
        if (compareResult == 0 || compareResult == 1) {
            compareResult = FlrUtil.versionCompare(dartSdkVersionStr, "2.12.0");
            if (compareResult == 0 || compareResult == 1) {
                this.shouldSupportNullsafety = true;
            }
        }
    }

    @Override
    public boolean isFlutterProject() {
        initFlagEnvIfNeed();
        return this.isFlutterProject;
    }

    @Override
    public boolean shouldSupportNullsafety() {
        initFlagEnvIfNeed();
        return this.shouldSupportNullsafety;
    }

    @Override
    public String getRDartLibraryVersion() {
        String rDartLibraryVersion = "0.1.1";

        VirtualFile flutterSdkHome = FlutterSdk.getFlutterSdk(project).getHome();
        FlutterSdkVersion flutterSdkVersion = FlutterSdkVersion.readFromSdk(flutterSdkHome);
        String flutterVersionWithoutHotfixStr = flutterSdkVersion.toString();

        DartSdk dartSdk = DartSdk.getDartSdk(project);
        String dartSdkVersionStr = (dartSdk == null) ? "0.0.0" : dartSdk.getVersion();

        int compareResult = FlrUtil.versionCompare(flutterVersionWithoutHotfixStr, "1.10.15");
        if (compareResult == 0 || compareResult == 1) {
            rDartLibraryVersion = "0.2.1";

            compareResult = FlrUtil.versionCompare(dartSdkVersionStr, "2.12.0");
            if (compareResult == 0 || compareResult == 1) {
                rDartLibraryVersion = "0.4.0-nullsafety.0";
            }
        }

        return rDartLibraryVersion;
    }
}
