package com.flr.core.model

import com.intellij.openapi.vfs.VirtualFile

/**
 * 资源扫描结果领域模型，取代原 generateOne 中散落的多个局部数组与元组。
 *
 * 各字段语义与原 generateOne 的同名局部变量一致（均已按原算法去重/排序）：
 * - [imageAssets]：合并去重并排序后的全部图片资产（non-svg + svg）
 * - [nonSvgImageAssets] / [svgImageAssets]：按 SVG 分类并排序后的图片资产
 * - [textAssets]：去重排序后的文本资产
 * - [fontFamilyConfigs]：排序后的字体家族配置（每项含 family / fonts，键为 String）
 * - [illegalResourceFiles]：非法命名的资源文件（image + text + font 合并）
 *
 * 全部消费方（AssetScanner / RDartGenerator / GenerateUseCase）与测试均为 Kotlin，故无需 `@JvmSuppressWildcards`。
 */
data class ResourceScanResult(
    val imageAssets: List<String>,
    val nonSvgImageAssets: List<String>,
    val svgImageAssets: List<String>,
    val textAssets: List<String>,
    val fontFamilyConfigs: List<Map<String, Any>>,
    val illegalResourceFiles: List<VirtualFile>,
)
