package com.flr.infra

import com.flr.command.util.FlrUtil
import com.flr.core.env.FlutterEnvironment
import com.intellij.openapi.project.Project
import com.jetbrains.lang.dart.sdk.DartSdk
import io.flutter.sdk.FlutterSdk
import io.flutter.sdk.FlutterSdkVersion

/**
 * [FlutterEnvironment] 的 IDE 实现：基于 IDE 的 FlutterSdk/DartSdk 探测环境。
 *
 * 逻辑整段迁移自原 FlrCommand.initFlagEnvIfNeed 与 getRDartLibraryVersion，行为不变。
 * 环境探测结果按需惰性初始化并缓存（与原 didInitFlutterEnv 语义一致）。
 */
class IdeFlutterEnvironment(private val project: Project) : FlutterEnvironment {

    private var didInitFlutterEnv = false
    private var isFlutterProjectFlag = false
    private var shouldSupportNullsafetyFlag = false

    private fun initFlagEnvIfNeed() {
        if (didInitFlutterEnv) {
            return
        }
        didInitFlutterEnv = true
        shouldSupportNullsafetyFlag = false

        val flutterSdk = FlutterSdk.getFlutterSdk(project)
        if (flutterSdk == null) {
            isFlutterProjectFlag = false
            return
        }

        isFlutterProjectFlag = true
        val flutterSdkHome = flutterSdk.home
        val flutterSdkVersion = FlutterSdkVersion.readFromSdk(flutterSdkHome)
        val flutterVersionWithoutHotfixStr = flutterSdkVersion.toString()

        val dartSdk = DartSdk.getDartSdk(project)
        val dartSdkVersionStr = dartSdk?.version ?: "0.0.0"

        var compareResult = FlrUtil.versionCompare(flutterVersionWithoutHotfixStr, "1.10.15")
        if (compareResult == 0 || compareResult == 1) {
            compareResult = FlrUtil.versionCompare(dartSdkVersionStr, "2.12.0")
            if (compareResult == 0 || compareResult == 1) {
                shouldSupportNullsafetyFlag = true
            }
        }
    }

    override fun isFlutterProject(): Boolean {
        initFlagEnvIfNeed()
        return isFlutterProjectFlag
    }

    override fun shouldSupportNullsafety(): Boolean {
        initFlagEnvIfNeed()
        return shouldSupportNullsafetyFlag
    }

    override fun getRDartLibraryVersion(): String {
        var rDartLibraryVersion = "0.1.1"

        val flutterSdkHome = FlutterSdk.getFlutterSdk(project)!!.home
        val flutterSdkVersion = FlutterSdkVersion.readFromSdk(flutterSdkHome)
        val flutterVersionWithoutHotfixStr = flutterSdkVersion.toString()

        val dartSdk = DartSdk.getDartSdk(project)
        val dartSdkVersionStr = dartSdk?.version ?: "0.0.0"

        var compareResult = FlrUtil.versionCompare(flutterVersionWithoutHotfixStr, "1.10.15")
        if (compareResult == 0 || compareResult == 1) {
            rDartLibraryVersion = "0.2.1"

            compareResult = FlrUtil.versionCompare(dartSdkVersionStr, "2.12.0")
            if (compareResult == 0 || compareResult == 1) {
                rDartLibraryVersion = "0.4.0-nullsafety.0"
            }
        }

        return rDartLibraryVersion
    }
}
