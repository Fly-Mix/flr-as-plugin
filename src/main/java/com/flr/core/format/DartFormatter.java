package com.flr.core.format;

import java.io.File;

/**
 * Dart 代码格式化接缝（object seam）。
 *
 * 把对 IDE 的 DartStyleAction 的依赖隔离到该接口后面，
 * 使 generate 核心逻辑不直接耦合格式化实现——测试可注入 no-op 替身，
 * 并在断言时用外部 `dart format` 做归一化比较（脱离 formatter 版本）。
 *
 * IDE 实现见 {@link com.flr.infra.IdeDartFormatter}。
 */
public interface DartFormatter {

    /** 格式化指定的 dart 文件（就地格式化并刷新）。 */
    void format(File dartFile);
}
