package com.flr.core.codegen

import com.flr.command.util.FlrCodeUtil
import com.flr.core.model.ResourceScanResult
import org.junit.Assert.assertEquals
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

    @Test
    fun flrAssetsUsesDirectoryBasedAssetId() {
        // flr.assets 资源的 ID 应源自子目录名称，而非文件 basename + 扩展名后缀
        val usedIds = listOf<String>()
        val testCases = listOf(
            "packages/flutter_r_demo/flr.assets/images/test_gif/test_gif.gif" to "test_gif",
            "packages/flutter_r_demo/flr.assets/images/test_jpg_variant/test_jpg_variant.jpg" to "test_jpg_variant",
            "packages/flutter_r_demo/flr.assets/images/test_png_variant_0/test_png_variant_0.png" to "test_png_variant_0",
            "packages/flutter_r_demo/flr.assets/images/test_3.0x_svg/test_3.0x_svg.svg" to "test_3_0x_svg",
            "packages/flutter_r_demo/flr.assets/rawfiles/test_json/test_json.json" to "test_json",
        )
        for ((asset, expectedId) in testCases) {
            val actualId = FlrCodeUtil.generateFlrAssetsAssetId(asset, usedIds)
            assertEquals("flr.assets ID for $asset", expectedId, actualId)
            assertTrue("isFlrAssetsAsset should detect $asset", FlrCodeUtil.isFlrAssetsAsset(asset))
        }
    }

    @Test
    fun nonFlrAssetsUsesFileBasenameBasedAssetId() {
        // 非 flr.assets 资源应沿用原有的文件 basename + 扩展名后缀策略
        val nonFlrAssets = listOf(
            "packages/flutter_r_demo/assets/images/test.jpg",
            "packages/flutter_r_demo/assets/texts/test.json",
        )
        for (asset in nonFlrAssets) {
            assertFalse("isFlrAssetsAsset should return false for $asset", FlrCodeUtil.isFlrAssetsAsset(asset))
        }
        // 验证原有 ID 生成逻辑不变：test.jpg（prior .png）→ test_jpg
        val jpgId = com.flr.command.util.FlrCodeUtil.generateAssetId(
            "packages/flutter_r_demo/assets/images/test.jpg", null, ".png"
        )
        assertEquals("test_jpg", jpgId)
    }
}
