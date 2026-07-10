package com.flr.settings

import com.flr.core.report.FlrReporter
import com.flr.logConsole.FlrColoredLogEntity
import com.flr.logConsole.FlrLogConsole

/**
 * 无操作的 [FlrReporter]：用于设置页/对话框等无 console 上下文，仅为满足 FlrFileUtil 的 reporter 形参。
 */
object NoopFlrReporter : FlrReporter {
    override fun log(text: String, type: FlrLogConsole.LogType) {}
    override fun log(coloredLogEntity: FlrColoredLogEntity) {}
    override fun success(title: String, message: String, hasWarning: Boolean) {}
    override fun failure(title: String, message: String?) {}
}
