package com.flr.core.codegen

import com.flr.FlrConstant
import com.flr.command.util.FlrCodeUtil
import com.flr.core.model.ResourceScanResult

/**
 * r.g.dart 代码生成器（纯逻辑，无 IDE 依赖）。
 *
 * 逻辑整段迁移自原 FlrCommand.generateOne 的 Step-9（生成 asset_id 字典）与 Step-10~19
 * （按模板拼装各 R 类代码），行为不变。产出**未格式化**的 r.g.dart 内容字符串，格式化由调用方
 * （[com.flr.core.format.DartFormatter]）负责。
 */
object RDartGenerator {

    @JvmStatic
    fun generate(
        scan: ResourceScanResult,
        packageName: String,
        isPackageProjectType: Boolean,
        shouldSupportNullsafety: Boolean
    ): String {
        val nonSvgImageAssetArray = scan.nonSvgImageAssets
        val svgImageAssetArray = scan.svgImageAssets
        val textAssetArray = scan.textAssets
        // ResourceScanResult 仍为 Java（原始 List<Map>）；运行时元素均为 String 键的 map，显式转换以对齐 Kotlin 类型
        @Suppress("UNCHECKED_CAST")
        val fontFamilyConfigArray = scan.fontFamilyConfigs as List<Map<String, Any>>

        // ----- Step-9：生成 non_svg / svg / text 的 asset_id 字典（key=asset, value=asset_id）-----
        val nonSvgImageAssetIdDict = HashMap<String, String>()
        val svgImageAssetIdDict = HashMap<String, String>()
        val textAssetIdDict = HashMap<String, String>()

        for (asset in nonSvgImageAssetArray) {
            val usedAssetIdArray = ArrayList(nonSvgImageAssetIdDict.values)
            val assetId = FlrCodeUtil.generateAssetId(asset, usedAssetIdArray, FlrConstant.PRIOR_NON_SVG_IMAGE_FILE_TYPE)
            nonSvgImageAssetIdDict[asset] = assetId
        }

        for (asset in svgImageAssetArray) {
            val usedAssetIdArray = ArrayList(svgImageAssetIdDict.values)
            val assetId = FlrCodeUtil.generateAssetId(asset, usedAssetIdArray, FlrConstant.PRIOR_SVG_IMAGE_FILE_TYPE)
            svgImageAssetIdDict[asset] = assetId
        }

        for (asset in textAssetArray) {
            val usedAssetIdArray = ArrayList(textAssetIdDict.values)
            val assetId = FlrCodeUtil.generateAssetId(asset, usedAssetIdArray, FlrConstant.PRIOR_TEXT_FILE_TYPE)
            textAssetIdDict[asset] = assetId
        }

        // ----- Step-10：新建 r.g.dart 内容 -----
        var rDartFileContent = ""

        // ----- Step-11：R 类 -----
        rDartFileContent += FlrCodeUtil.generate_R_class(packageName)

        // ----- Step-12：AssetResource 类 -----
        rDartFileContent += "\n"
        rDartFileContent += FlrCodeUtil.generate_AssetResource_class(packageName, shouldSupportNullsafety)

        // ----- Step-13：_R_Image_AssetResource 类 -----
        rDartFileContent += "\n"
        rDartFileContent += FlrCodeUtil.generate__R_Image_AssetResource_class(nonSvgImageAssetArray, nonSvgImageAssetIdDict, packageName, isPackageProjectType)

        // ----- Step-14：_R_Svg_AssetResource 类 -----
        rDartFileContent += "\n"
        rDartFileContent += FlrCodeUtil.generate__R_Svg_AssetResource_class(svgImageAssetArray, svgImageAssetIdDict, packageName, isPackageProjectType)

        // ----- Step-15：_R_Text_AssetResource 类 -----
        rDartFileContent += "\n"
        rDartFileContent += FlrCodeUtil.generate__R_Text_AssetResource_class(textAssetArray, textAssetIdDict, packageName, isPackageProjectType)

        // ----- Step-16：_R_Image 类 -----
        rDartFileContent += "\n"
        rDartFileContent += FlrCodeUtil.generate__R_Image_class(nonSvgImageAssetArray, nonSvgImageAssetIdDict, packageName)

        // ----- Step-17：_R_Svg 类 -----
        rDartFileContent += "\n"
        rDartFileContent += FlrCodeUtil.generate__R_Svg_class(svgImageAssetArray, svgImageAssetIdDict, packageName, shouldSupportNullsafety)

        // ----- Step-18：_R_Text 类 -----
        rDartFileContent += "\n"
        rDartFileContent += FlrCodeUtil.generate__R_Text_class(textAssetArray, textAssetIdDict, packageName)

        // ----- Step-19：_R_FontFamily 类 -----
        rDartFileContent += "\n"
        rDartFileContent += FlrCodeUtil.generate__R_FontFamily_class(fontFamilyConfigArray, packageName)

        return rDartFileContent
    }
}
