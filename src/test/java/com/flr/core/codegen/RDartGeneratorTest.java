package com.flr.core.codegen;

import com.flr.core.model.ResourceScanResult;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link RDartGenerator} 的纯单元测试：不依赖 IDE 平台与 VFS，直接喂入手工构造的
 * {@link ResourceScanResult}，校验生成的 r.g.dart 代码的关键不变量与 nullsafety 分支差异。
 *
 * 快速反馈用；完整的逐字节黄金回归见 GenerateHeadlessTest。
 */
public class RDartGeneratorTest {

    private static final String PKG = "flutter_r_demo";

    private ResourceScanResult sampleScan() {
        List<String> nonSvg = Arrays.asList("packages/flutter_r_demo/assets/images/test.png");
        List<String> svg = Arrays.asList("packages/flutter_r_demo/assets/images/test.svg");
        List<String> text = Arrays.asList("packages/flutter_r_demo/assets/texts/test.json");
        List<String> images = new ArrayList<>();
        images.addAll(nonSvg);
        images.addAll(svg);

        Map<String, Object> fontAsset = new LinkedHashMap<>();
        fontAsset.put("asset", "packages/flutter_r_demo/assets/fonts/Amiri/Amiri-Regular.ttf");
        Map<String, Object> family = new LinkedHashMap<>();
        family.put("family", "Amiri");
        family.put("fonts", new ArrayList<Map>(Arrays.asList(fontAsset)));
        List<Map> fontFamilyConfigs = new ArrayList<Map>(Arrays.asList(family));

        return new ResourceScanResult(images, nonSvg, svg, text, fontFamilyConfigs, Collections.emptyList());
    }

    @Test
    public void generatesCoreStructureAndAssetMembers() {
        String code = RDartGenerator.generate(sampleScan(), PKG, false, true);

        assertTrue("应包含 R 类", code.contains("class R {"));
        assertTrue("应包含 package 常量", code.contains("static const package = \"flutter_r_demo\";"));
        // 图片资产 test.png（prior .png）-> assetId "test"
        assertTrue("应生成图片方法 test()", code.contains("AssetImage test()"));
        // svg 资产 test.svg（prior .svg）-> assetId "test"
        assertTrue("应生成 svg 方法 test(...)", code.contains("AssetSvg test("));
        // 文本资产 test.json（prior .* 不匹配 -> 带类型后缀）-> assetId "test_json"
        assertTrue("应生成文本方法 test_json()", code.contains("Future<String> test_json()"));
        // 字体家族 Amiri -> id amiri
        assertTrue("应生成字体家族成员 amiri", code.contains("final amiri = \"Amiri\";"));
    }

    @Test
    public void nullsafetyTogglesAssetResourceAndSvgSignature() {
        String nnbd = RDartGenerator.generate(sampleScan(), PKG, false, true);
        String legacy = RDartGenerator.generate(sampleScan(), PKG, false, false);

        // nullsafety=true：AssetResource.packageName 可空，svg 用 required
        assertTrue(nnbd.contains("final String? packageName;"));
        assertTrue(nnbd.contains("{required double width, required double height}"));
        assertFalse(nnbd.contains("assert(assetName != null)"));

        // nullsafety=false：AssetResource 带 assert，svg 用 @required
        assertTrue(legacy.contains("assert(assetName != null)"));
        assertTrue(legacy.contains("{@required double width, @required double height}"));
        assertFalse(legacy.contains("final String? packageName;"));
    }
}
