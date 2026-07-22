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
) {
    /**
     * 合并另一个 [ResourceScanResult] 到当前结果。
     * 各资产列表去重后排序，fontFamilyConfigs 去重合并后排序，非法资源文件合并。
     */
    fun merge(other: ResourceScanResult): ResourceScanResult {
        val mergedImageAssets = ArrayList(imageAssets)
        mergedImageAssets.addAll(other.imageAssets)
        val dedupedImageAssets = ArrayList(HashSet(mergedImageAssets)).sorted()

        val mergedNonSvg = ArrayList(nonSvgImageAssets)
        mergedNonSvg.addAll(other.nonSvgImageAssets)
        val dedupedNonSvg = ArrayList(HashSet(mergedNonSvg)).sorted()

        val mergedSvg = ArrayList(svgImageAssets)
        mergedSvg.addAll(other.svgImageAssets)
        val dedupedSvg = ArrayList(HashSet(mergedSvg)).sorted()

        val mergedText = ArrayList(textAssets)
        mergedText.addAll(other.textAssets)
        val dedupedText = ArrayList(HashSet(mergedText)).sorted()

        // 合并 fontFamilyConfigs：按 family 去重，后入者覆盖前入者（同一 family 的 fonts 配置合并）
        val familyMap = LinkedHashMap<String, Map<String, Any>>()
        for (config in fontFamilyConfigs) {
            val family = config["family"] as String
            familyMap[family] = config
        }
        for (config in other.fontFamilyConfigs) {
            val family = config["family"] as String
            familyMap[family] = config
        }
        val mergedFonts = familyMap.values.toList().sortedWith(Comparator { o1, o2 ->
            (o1["family"] as String).compareTo(o2["family"] as String)
        })

        val mergedIllegal = ArrayList(illegalResourceFiles)
        mergedIllegal.addAll(other.illegalResourceFiles)

        return ResourceScanResult(
            dedupedImageAssets,
            dedupedNonSvg,
            dedupedSvg,
            dedupedText,
            mergedFonts,
            mergedIllegal
        )
    }
}
