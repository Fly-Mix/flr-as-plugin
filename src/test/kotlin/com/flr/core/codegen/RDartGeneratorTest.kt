package com.flr.core.codegen

import com.flr.core.model.ResourceScanResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RDartGenerator] 的纯单元测试：不依赖 IDE 平台与 VFS，直接喂入手工构造的 [ResourceScanResult]，
 * 校验生成的 r.g.dart 代码的关键不变量与 nullsafety 分支差异。
 *
 * 快速反馈用；完整的逐字节黄金回归见 GenerateHeadlessTest。
 */
class RDartGeneratorTest {

    private val pkg = "flutter_r_demo"

    private fun sampleScan(): ResourceScanResult {
        val nonSvg = listOf("packages/flutter_r_demo/assets/images/test.png")
        val svg = listOf("packages/flutter_r_demo/assets/images/test.svg")
        val text = listOf("packages/flutter_r_demo/assets/texts/test.json")
        val images = ArrayList<String>()
        images.addAll(nonSvg)
        images.addAll(svg)

        val fontAsset: Map<String, Any> = linkedMapOf(
            "asset" to "packages/flutter_r_demo/assets/fonts/Amiri/Amiri-Regular.ttf"
        )
        val family: Map<String, Any> = linkedMapOf(
            "family" to "Amiri",
            "fonts" to arrayListOf(fontAsset)
        )
        val fontFamilyConfigs = arrayListOf(family)

        return ResourceScanResult(images, nonSvg, svg, text, fontFamilyConfigs, emptyList())
    }

    @Test
    fun generatesCoreStructureAndAssetMembers() {
        val code = RDartGenerator.generate(sampleScan(), pkg, false, true)

        assertTrue("应包含 R 类", code.contains("class R {"))
        assertTrue("应包含 package 常量", code.contains("static const package = \"flutter_r_demo\";"))
        // 图片资产 test.png（prior .png）-> assetId "test"
        assertTrue("应生成图片方法 test()", code.contains("AssetImage test()"))
        // svg 资产 test.svg（prior .svg）-> assetId "test"
        assertTrue("应生成 svg 方法 test(...)", code.contains("AssetSvg test("))
        // 文本资产 test.json（prior .* 不匹配 -> 带类型后缀）-> assetId "test_json"
        assertTrue("应生成文本方法 test_json()", code.contains("Future<String> test_json()"))
        // 字体家族 Amiri -> id amiri
        assertTrue("应生成字体家族成员 amiri", code.contains("final amiri = \"Amiri\";"))
    }

    @Test
    fun nullsafetyTogglesAssetResourceAndSvgSignature() {
        val nnbd = RDartGenerator.generate(sampleScan(), pkg, false, true)
        val legacy = RDartGenerator.generate(sampleScan(), pkg, false, false)

        // nullsafety=true：AssetResource.packageName 可空，svg 用 required
        assertTrue(nnbd.contains("final String? packageName;"))
        assertTrue(nnbd.contains("{required double width, required double height}"))
        assertFalse(nnbd.contains("assert(assetName != null)"))

        // nullsafety=false：AssetResource 带 assert，svg 用 @required
        assertTrue(legacy.contains("assert(assetName != null)"))
        assertTrue(legacy.contains("{@required double width, @required double height}"))
        assertFalse(legacy.contains("final String? packageName;"))
    }
}
