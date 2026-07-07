package com.flr.core.codegen;

import com.flr.FlrConstant;
import com.flr.command.util.FlrCodeUtil;
import com.flr.core.model.ResourceScanResult;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * r.g.dart 代码生成器（纯逻辑，无 IDE 依赖）。
 *
 * 逻辑整段迁移自原 FlrCommand.generateOne 的 Step-9（生成 asset_id 字典）与 Step-10~19
 * （按模板拼装各 R 类代码），行为不变。产出**未格式化**的 r.g.dart 内容字符串，格式化由调用方
 * （{@link com.flr.core.format.DartFormatter}）负责。
 */
public final class RDartGenerator {

    private RDartGenerator() {
    }

    public static String generate(@NotNull ResourceScanResult scan,
                                  @NotNull String packageName,
                                  boolean isPackageProjectType,
                                  boolean shouldSupportNullsafety) {
        List<String> nonSvgImageAssetArray = scan.getNonSvgImageAssets();
        List<String> svgImageAssetArray = scan.getSvgImageAssets();
        List<String> textAssetArray = scan.getTextAssets();
        List<Map> fontFamilyConfigArray = scan.getFontFamilyConfigs();

        // ----- Step-9：生成 non_svg / svg / text 的 asset_id 字典（key=asset, value=asset_id）-----
        Map<String, String> nonSvgImageAssetIdDict = new HashMap<>();
        Map<String, String> svgImageAssetIdDict = new HashMap<>();
        Map<String, String> textAssetIdDict = new HashMap<>();

        for (String asset : nonSvgImageAssetArray) {
            List<String> usedAssetIdArray = new ArrayList<>(nonSvgImageAssetIdDict.values());
            String assetId = FlrCodeUtil.generateAssetId(asset, usedAssetIdArray, FlrConstant.PRIOR_NON_SVG_IMAGE_FILE_TYPE);
            nonSvgImageAssetIdDict.put(asset, assetId);
        }

        for (String asset : svgImageAssetArray) {
            List<String> usedAssetIdArray = new ArrayList<>(svgImageAssetIdDict.values());
            String assetId = FlrCodeUtil.generateAssetId(asset, usedAssetIdArray, FlrConstant.PRIOR_SVG_IMAGE_FILE_TYPE);
            svgImageAssetIdDict.put(asset, assetId);
        }

        for (String asset : textAssetArray) {
            List<String> usedAssetIdArray = new ArrayList<>(textAssetIdDict.values());
            String assetId = FlrCodeUtil.generateAssetId(asset, usedAssetIdArray, FlrConstant.PRIOR_TEXT_FILE_TYPE);
            textAssetIdDict.put(asset, assetId);
        }

        // ----- Step-10：新建 r.g.dart 内容 -----
        String r_dart_file_content = "";

        // ----- Step-11：R 类 -----
        String g_R_class_code = FlrCodeUtil.generate_R_class(packageName);
        r_dart_file_content += g_R_class_code;

        // ----- Step-12：AssetResource 类 -----
        r_dart_file_content += "\n";
        String g_AssetResource_class_code = FlrCodeUtil.generate_AssetResource_class(packageName, shouldSupportNullsafety);
        r_dart_file_content += g_AssetResource_class_code;

        // ----- Step-13：_R_Image_AssetResource 类 -----
        r_dart_file_content += "\n";
        String g__R_Image_AssetResource_class_code = FlrCodeUtil.generate__R_Image_AssetResource_class(nonSvgImageAssetArray, nonSvgImageAssetIdDict, packageName, isPackageProjectType);
        r_dart_file_content += g__R_Image_AssetResource_class_code;

        // ----- Step-14：_R_Svg_AssetResource 类 -----
        r_dart_file_content += "\n";
        String g__R_Svg_AssetResource_class_code = FlrCodeUtil.generate__R_Svg_AssetResource_class(svgImageAssetArray, svgImageAssetIdDict, packageName, isPackageProjectType);
        r_dart_file_content += g__R_Svg_AssetResource_class_code;

        // ----- Step-15：_R_Text_AssetResource 类 -----
        r_dart_file_content += "\n";
        String g__R_Text_AssetResource_class_code = FlrCodeUtil.generate__R_Text_AssetResource_class(textAssetArray, textAssetIdDict, packageName, isPackageProjectType);
        r_dart_file_content += g__R_Text_AssetResource_class_code;

        // ----- Step-16：_R_Image 类 -----
        r_dart_file_content += "\n";
        String g__R_Image_class_code = FlrCodeUtil.generate__R_Image_class(nonSvgImageAssetArray, nonSvgImageAssetIdDict, packageName);
        r_dart_file_content += g__R_Image_class_code;

        // ----- Step-17：_R_Svg 类 -----
        r_dart_file_content += "\n";
        String g__R_Svg_class_code = FlrCodeUtil.generate__R_Svg_class(svgImageAssetArray, svgImageAssetIdDict, packageName, shouldSupportNullsafety);
        r_dart_file_content += g__R_Svg_class_code;

        // ----- Step-18：_R_Text 类 -----
        r_dart_file_content += "\n";
        String g__R_Text_class_code = FlrCodeUtil.generate__R_Text_class(textAssetArray, textAssetIdDict, packageName);
        r_dart_file_content += g__R_Text_class_code;

        // ----- Step-19：_R_FontFamily 类 -----
        r_dart_file_content += "\n";
        String g__R_Font_Family_class_code = FlrCodeUtil.generate__R_FontFamily_class(fontFamilyConfigArray, packageName);
        r_dart_file_content += g__R_Font_Family_class_code;

        return r_dart_file_content;
    }
}
