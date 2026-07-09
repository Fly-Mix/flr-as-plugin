package com.flr.core.report

import com.flr.logConsole.FlrColoredLogEntity
import com.flr.logConsole.FlrLogConsole

/**
 * 进度日志与结果通知接缝（object seam）。
 *
 * 把对 IDE 的 ConsoleView（[FlrLogConsole]）与通知（FlrMessageBox）的依赖隔离到该接口后面，
 * 使 generate 核心逻辑可在无 UI 的环境下运行——测试注入 no-op 替身即可。
 *
 * IDE 实现见 [com.flr.infra.FlrConsoleReporter]。
 */
interface FlrReporter {

    /** 打印一行日志。 */
    fun log(text: String, type: FlrLogConsole.LogType)

    /** 打印一条彩色日志实体（多段不同类型）。 */
    fun log(coloredLogEntity: FlrColoredLogEntity)

    /** 成功通知（hasWarning 为 true 时以 warning 样式展示）。 */
    fun success(title: String, message: String, hasWarning: Boolean)

    /** 失败通知。 */
    fun failure(title: String, message: String)
}
