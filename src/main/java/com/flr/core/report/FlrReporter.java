package com.flr.core.report;

import com.flr.logConsole.FlrColoredLogEntity;
import com.flr.logConsole.FlrLogConsole;

/**
 * 进度日志与结果通知接缝（object seam）。
 *
 * 把对 IDE 的 ConsoleView（{@link FlrLogConsole}）与通知（FlrMessageBox）的依赖
 * 隔离到该接口后面，使 generate 核心逻辑可在无 UI 的环境下运行——测试注入 no-op 替身即可。
 *
 * 说明：{@link FlrLogConsole.LogType} 与 {@link FlrColoredLogEntity} 本质是纯数据/枚举
 * （不含 com.intellij 运行时依赖），此处沿用它们作为日志词汇，避免大范围改动。
 *
 * IDE 实现见 {@link com.flr.infra.FlrConsoleReporter}。
 */
public interface FlrReporter {

    /** 打印一行日志。 */
    void log(String text, FlrLogConsole.LogType type);

    /** 打印一条彩色日志实体（多段不同类型）。 */
    void log(FlrColoredLogEntity coloredLogEntity);

    /** 成功通知（hasWarning 为 true 时以 warning 样式展示）。 */
    void success(String title, String message, boolean hasWarning);

    /** 失败通知。 */
    void failure(String title, String message);
}
