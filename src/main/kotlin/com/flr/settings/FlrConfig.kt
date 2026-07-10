package com.flr.settings

import com.flr.FlrConstant

/**
 * flr 配置（pubspec.yaml 的 `flr` 节点）的可编辑模型，供设置页与配置对话框共用。
 *
 * - [assetDirs] / [fontDirs]：需扫描的图片/文本 与 字体资源目录（相对工程根的路径）。
 * - [dartfmtLineLength]：dartfmt 行长。
 * - [coreVersion]：flr 核心逻辑版本（表单中只读展示）。
 */
data class FlrConfig(
    val assetDirs: MutableList<String> = mutableListOf(),
    val fontDirs: MutableList<String> = mutableListOf(),
    var dartfmtLineLength: Int = FlrConstant.DARTFMT_LINE_LENGTH,
    var coreVersion: String = FlrConstant.CORE_VERSION,
)
