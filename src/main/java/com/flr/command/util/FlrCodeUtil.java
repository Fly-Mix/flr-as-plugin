package com.flr.command.util;

import com.flr.FlrConstant;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/*
* 代码生成相关的工具类方法
* */
public class FlrCodeUtil {
    /*
    * 根据模板生成 R class 的代码
    * */
    public static String generate_R_class(@NotNull String packageName) {
        String code = String.format("// IT IS GENERATED BY FLR - DO NOT MODIFY BY HAND\n" +
                "// YOU CAN GET MORE DETAILS ABOUT FLR FROM:\n" +
                "// - https://github.com/Fly-Mix/flr-cli\n" +
                "// - https://github.com/Fly-Mix/flr-vscode-extension\n" +
                "// - https://github.com/Fly-Mix/flr-as-plugin\n" +
                "//\n" +
                "\n" +
                "// ignore: unused_import\n" +
                "import 'package:flutter/widgets.dart';\n" +
                "// ignore: unused_import\n" +
                "import 'package:flutter/services.dart' show rootBundle;\n" +
                "// ignore: unused_import\n" +
                "import 'package:path/path.dart' as path;\n" +
                "// ignore: unused_import\n" +
                "import 'package:flutter_svg/flutter_svg.dart';\n" +
                "// ignore: unused_import\n" +
                "import 'package:r_dart_library/asset_svg.dart';\n" +
                "\n" +
                "/// This `R` class is generated and contains references to static asset resources.\n" +
                "class R {\n" +
                "  /// package name: %s\n" +
                "  static const package = \"%s\";\n" +
                "\n" +
                "  /// This `R.image` struct is generated, and contains static references to static non-svg type image asset resources.\n" +
                "  static const image = _R_Image();\n" +
                "\n" +
                "  /// This `R.svg` struct is generated, and contains static references to static svg type image asset resources.\n" +
                "  static const svg = _R_Svg();\n" +
                "\n" +
                "  /// This `R.text` struct is generated, and contains static references to static text asset resources.\n" +
                "  static const text = _R_Text();\n" +
                "\n" +
                "  /// This `R.fontFamily` struct is generated, and contains static references to static font asset resources.\n" +
                "  static const fontFamily = _R_FontFamily();\n" +
                "}",
                packageName,
                packageName);

        return code;
    }

    /*
    * 根据模板生成 AssetResource class 的代码
    * */
    public static String generate_AssetResource_class(@NotNull String packageName, @NotNull boolean shouldSupportNullsafety) {
        String code = "/// Asset resource’s metadata class.\n" +
                "/// For example, here is the metadata of `packages/flutter_demo/assets/images/example.png` asset:\n" +
                "/// - packageName：flutter_demo\n" +
                "/// - assetName：assets/images/example.png\n" +
                "/// - fileDirname：assets/images\n" +
                "/// - fileBasename：example.png\n" +
                "/// - fileBasenameNoExtension：example\n" +
                "/// - fileExtname：.png\n";
        if (shouldSupportNullsafety) {
            code += "class AssetResource {\n" +
                    "  /// Creates an object to hold the asset resource’s metadata.\n" +
                    "  const AssetResource(this.assetName, {this.packageName});\n" +
                    "\n" +
                    "  /// The name of the main asset from the set of asset resources to choose from.\n" +
                    "  final String assetName;\n" +
                    "\n" +
                    "  /// The name of the package from which the asset resource is included.\n" +
                    "  final String? packageName;\n" +
                    "\n" +
                    "  /// The name used to generate the key to obtain the asset resource. For local assets\n" +
                    "  /// this is [assetName], and for assets from packages the [assetName] is\n" +
                    "  /// prefixed 'packages/<package_name>/'.\n" +
                    "  String get keyName => packageName == null ? assetName : \"packages/$packageName/$assetName\";\n" +
                    "\n" +
                    "  /// The file basename of the asset resource.\n" +
                    "  String get fileBasename {\n" +
                    "    final basename = path.basename(assetName);\n" +
                    "    return basename;\n" +
                    "  }\n" +
                    "\n" +
                    "  /// The no extension file basename of the asset resource.\n" +
                    "  String get fileBasenameNoExtension {\n" +
                    "    final basenameWithoutExtension = path.basenameWithoutExtension(assetName);\n" +
                    "    return basenameWithoutExtension;\n" +
                    "  }\n" +
                    "\n" +
                    "  /// The file extension name of the asset resource.\n" +
                    "  String get fileExtname {\n" +
                    "    final extension = path.extension(assetName);\n" +
                    "    return extension;\n" +
                    "  }\n" +
                    "\n" +
                    "  /// The directory path name of the asset resource.\n" +
                    "  String get fileDirname {\n" +
                    "    var dirname = assetName;\n" +
                    "    if (packageName != null) {\n" +
                    "      final packageStr = \"packages/$packageName/\";\n" +
                    "      dirname = dirname.replaceAll(packageStr, \"\");\n" +
                    "    }\n" +
                    "    final filenameStr = \"$fileBasename/\";\n" +
                    "    dirname = dirname.replaceAll(filenameStr, \"\");\n" +
                    "    return dirname;\n" +
                    "  }\n" +
                    "}";
        } else {
            code += "class AssetResource {\n" +
                    "  /// Creates an object to hold the asset resource’s metadata.\n" +
                    "  const AssetResource(this.assetName, {this.packageName}) : assert(assetName != null);\n" +
                    "\n" +
                    "  /// The name of the main asset from the set of asset resources to choose from.\n" +
                    "  final String assetName;\n" +
                    "\n" +
                    "  /// The name of the package from which the asset resource is included.\n" +
                    "  final String packageName;\n" +
                    "\n" +
                    "  /// The name used to generate the key to obtain the asset resource. For local assets\n" +
                    "  /// this is [assetName], and for assets from packages the [assetName] is\n" +
                    "  /// prefixed 'packages/<package_name>/'.\n" +
                    "  String get keyName => packageName == null ? assetName : \"packages/$packageName/$assetName\";\n" +
                    "\n" +
                    "  /// The file basename of the asset resource.\n" +
                    "  String get fileBasename {\n" +
                    "    final basename = path.basename(assetName);\n" +
                    "    return basename;\n" +
                    "  }\n" +
                    "\n" +
                    "  /// The no extension file basename of the asset resource.\n" +
                    "  String get fileBasenameNoExtension {\n" +
                    "    final basenameWithoutExtension = path.basenameWithoutExtension(assetName);\n" +
                    "    return basenameWithoutExtension;\n" +
                    "  }\n" +
                    "\n" +
                    "  /// The file extension name of the asset resource.\n" +
                    "  String get fileExtname {\n" +
                    "    final extension = path.extension(assetName);\n" +
                    "    return extension;\n" +
                    "  }\n" +
                    "\n" +
                    "  /// The directory path name of the asset resource.\n" +
                    "  String get fileDirname {\n" +
                    "    var dirname = assetName;\n" +
                    "    if (packageName != null) {\n" +
                    "      final packageStr = \"packages/$packageName/\";\n" +
                    "      dirname = dirname.replaceAll(packageStr, \"\");\n" +
                    "    }\n" +
                    "    final filenameStr = \"$fileBasename/\";\n" +
                    "    dirname = dirname.replaceAll(filenameStr, \"\");\n" +
                    "    return dirname;\n" +
                    "  }\n" +
                    "}";
        }

        return code;
    }

    /*
     * 为asset生成assetId；其中priorAssetType为优先的资产类型，其决定了当前asset的assetId是否带有资产类型信息。
     * 如priorAssetType为".png"，
     * 这时候若asset为“packages/flutter_demo/assets/images/test.png”，这时其生成assetId为“test”，不带有类型信息；
     * 这时候若asset为“packages/flutter_demo/assets/images/test.jpg”，这时其生成assetId为“test_jpg”，带有类型信息；
     *
     * @param asset 指定的资产
     * @param usedAssetIdArray 已使用的assetId数组
     * @param priorAssetType 优先的资产类型，默认值为null，代表“.*”，意味生成的assetId总是带有资产类型信息
     * @return assetId 资产ID
     * */
    public static String generateAssetId(@NotNull String asset, List<String> usedAssetIdArray, String priorAssetType) {
        File assetFile = new File(asset);
        String fileExtName = FlrFileUtil.getFileExtension(assetFile).toLowerCase();
        String fileBasenameWithoutExtension = FlrFileUtil.getFileBasenameWithoutExtension(assetFile);

        String assetId = fileBasenameWithoutExtension;
        if(priorAssetType == null || priorAssetType.equals(fileExtName) == false) {
            String extInfo = "_" + fileExtName.substring(1);
            assetId = fileBasenameWithoutExtension + extInfo;
        }

        // 过滤非法字符
        assetId = assetId.replaceAll("[^a-zA-Z0-9_$]", "_");

        // 检测首字符是不是字母；
        // 若是字母，则检测其是不是大写字母，若是，则转换为小写字母；
        // 若不是字母，则添加一个前缀字母“a”
        Character firstChar = assetId.charAt(0);
        if(Character.isLetter(firstChar)) {
            if(Character.isUpperCase(firstChar)) {
                String firstCharStr = firstChar.toString().toLowerCase();
                assetId = firstCharStr + assetId.substring(1);
            }
        } else {
            String firstCharStr = "a";
            assetId = firstCharStr + assetId;
        }

        // 处理 asset_id 重名的情况
        if(usedAssetIdArray != null
                && usedAssetIdArray.isEmpty() == false
                && usedAssetIdArray.contains(assetId)) {
            // 当前asset_id重名次数，初始值为1
            int repeatCount = 1;

            // 查找当前asset_id衍生出来的asset_id_brother（id兄弟）
            // asset_id_brother = #{asset_id}$#{repeat_count}
            // 其中，repeat_count >= 1
            //
            // Example：
            // asset_id = test
            // asset_id_brother = test$1
            //

            String idBrotherRegex = String.format("^%s\\$[1-9][0-9]*$", assetId);
            Pattern pattern = Pattern.compile(idBrotherRegex);
            List<String> curAssetIdBrothers = usedAssetIdArray.stream().filter(pattern.asPredicate()).collect(Collectors.toList());

            if(curAssetIdBrothers != null && curAssetIdBrothers.isEmpty() == false) {
                repeatCount += curAssetIdBrothers.size();
            }

            assetId = String.format("%s$%d",assetId, repeatCount);
        }

        return assetId;
    }

    /*
     * 为当前asset生成注释
     *
     * === Examples
     * packageName = "flutter_r_demo"
     *
     * === Example-1
     * asset = "packages/flutter_r_demo/assets/images/test.png"
     * assetComment = "asset: lib/assets/images/test.png"
     *
     * === Example-2
     * asset = "assets/images/test.png"
     * assetComment = "asset: assets/images/test.png"
     *
     * */
    public static String generateAssetComment(@NotNull String asset,@NotNull String packageName) {
        String packagesPrefix = "packages/" + packageName + "/";

        if(asset.startsWith(packagesPrefix)) {
            // asset: packages/flutter_r_demo/assets/images/test.png
            // to get assetName: assets/images/test.png
            String assetName = asset.replaceFirst(packagesPrefix, "");

            String assetComment = "asset: lib/" + assetName;
            return assetComment;
        } else {
            // asset: assets/images/test.png
            // to get assetName: assets/images/test.png
            String assetName = asset;

            String assetComment = "asset: " + assetName;
            return assetComment;
        }
    }

    /*
    * 为当前 asset 生成 AssetResource property 的代码
    * */
    public static String generate_AssetResource_property(@NotNull String asset, @NotNull  Map<String, String> assetIdDict, @NotNull String packageName, boolean isPackageProjectType, String priorAssetType) {
        String assetId = assetIdDict.get(asset);
        String assetComment = generateAssetComment(asset, packageName);

        String assetName = "";
        boolean needPackage = false;

        String packagesPrefix = "packages/" + packageName + "/";
        if(asset.startsWith(packagesPrefix)) {
            // asset: packages/flutter_r_demo/assets/images/test.png
            // to get assetName: assets/images/test.png
            assetName = asset.replaceFirst(packagesPrefix, "");;
            needPackage = true;
        } else {
            // asset: assets/images/test.png
            // to get assetName: assets/images/test.png
            assetName = asset;

            if (isPackageProjectType) {
                needPackage = true;
            } else {
                needPackage = false;
            }
        }

        // 对字符串中的 '$' 进行转义处理：'$' -> '\$'
        // assetName: assets/images/test$.png
        // to get escapedAssetName: assets/images/test\$.png
        String escapedAssetName = assetName.replace("$", "\\$");

        if(needPackage) {
            String code = String.format("  /// %s\n" +
                            "  // ignore: non_constant_identifier_names\n" +
                            "  final %s = const AssetResource(\"%s\", packageName: R.package);",
                    assetComment,
                    assetId,
                    escapedAssetName) ;

            return code;
        } else {
            String code = String.format("  /// %s\n" +
                            "  // ignore: non_constant_identifier_names\n" +
                            "  final %s = const AssetResource(\"%s\", packageName: null);",
                    assetComment,
                    assetId,
                    escapedAssetName) ;

            return code;
        }
    }

    /*
    * 根据模板，为 nonSvgImageAssetArray（非svg类的图片资产数组）生成 _R_Image_AssetResource class 的代码
    * */
    public static String generate__R_Image_AssetResource_class(@NotNull List<String> nonSvgImageAssetArray, @NotNull Map<String, String> nonSvgImageAssetIdDict, @NotNull String packageName, boolean isPackageProjectType) {

        String all_g_AssetResource_property_code = "";

        for (String asset : nonSvgImageAssetArray) {
            all_g_AssetResource_property_code += "\n";
            String g_AssetResource_property_code = generate_AssetResource_property(asset, nonSvgImageAssetIdDict, packageName, isPackageProjectType, FlrConstant.PRIOR_NON_SVG_IMAGE_FILE_TYPE);
            all_g_AssetResource_property_code += g_AssetResource_property_code;
        }

        String code = String.format("// ignore: camel_case_types\n" +
                "class _R_Image_AssetResource {\n" +
                "  const _R_Image_AssetResource();\n" +
                "%s\n" +
                "}",
                all_g_AssetResource_property_code);
        return code;
    }

    /*
     * 根据模板，为 svgImageAssetArray（svg类的图片资产数组）生成 _R_Svg_AssetResource class 的代码
     * */
    public static String generate__R_Svg_AssetResource_class(@NotNull List<String> svgImageAssetArray, @NotNull Map<String, String> svgImageAssetIdDict, @NotNull String packageName, boolean isPackageProjectType) {

        String all_g_AssetResource_property_code = "";

        for (String asset : svgImageAssetArray) {
            all_g_AssetResource_property_code += "\n";
            String g_AssetResource_property_code = generate_AssetResource_property(asset, svgImageAssetIdDict, packageName, isPackageProjectType, FlrConstant.PRIOR_SVG_IMAGE_FILE_TYPE);
            all_g_AssetResource_property_code += g_AssetResource_property_code;
        }

        String code = String.format("// ignore: camel_case_types\n" +
                        "class _R_Svg_AssetResource {\n" +
                        "  const _R_Svg_AssetResource();\n" +
                        "%s\n" +
                        "}",
                all_g_AssetResource_property_code);
        return code;
    }

    /*
     * 根据模板，为 textAssetArray（文本资产数组）生成 _R_Text_AssetResource class 的代码
     * */
    public static String generate__R_Text_AssetResource_class(@NotNull List<String> textAssetArray, @NotNull Map<String, String> textAssetIdDict, @NotNull String packageName, boolean isPackageProjectType) {

        String all_g_AssetResource_property_code = "";

        for (String asset : textAssetArray) {
            all_g_AssetResource_property_code += "\n";
            String g_AssetResource_property_code = generate_AssetResource_property(asset, textAssetIdDict, packageName, isPackageProjectType, FlrConstant.PRIOR_TEXT_FILE_TYPE);
            all_g_AssetResource_property_code += g_AssetResource_property_code;
        }

        String code = String.format("// ignore: camel_case_types\n" +
                        "class _R_Text_AssetResource {\n" +
                        "  const _R_Text_AssetResource();\n" +
                        "%s\n" +
                        "}",
                all_g_AssetResource_property_code);
        return code;
    }

    /*
     * 根据模板，为 nonSvgImageAssetArray（非svg类的图片资产数组）生成 _R_Image class 的代码
     * */
    public static String generate__R_Image_class(@NotNull List<String> nonSvgImageAssetArray, @NotNull Map<String, String> nonSvgImageAssetIdDict, @NotNull String packageName) {
        String all_g_Asset_method_code = "";

        for (String asset : nonSvgImageAssetArray) {
            all_g_Asset_method_code += "\n";

            String assetId = nonSvgImageAssetIdDict.get(asset);
            String assetComment = generateAssetComment(asset, packageName);

            String g_Asset_method_code = String.format("  /// %s\n" +
                    "  // ignore: non_constant_identifier_names\n" +
                    "  AssetImage %s() {\n" +
                    "    return AssetImage(asset.%s.keyName);\n" +
                    "  }",
                    assetComment,
                    assetId,
                    assetId);

            all_g_Asset_method_code += g_Asset_method_code;
        }

        String code = String.format("/// This `_R_Image` class is generated and contains references to static non-svg type image asset resources.\n" +
                "// ignore: camel_case_types\n" +
                "class _R_Image {\n" +
                "  const _R_Image();\n" +
                "\n" +
                "  final asset = const _R_Image_AssetResource();\n" +
                "%s\n" +
                "}",
                all_g_Asset_method_code);

        return code;
    }

    /*
     * 根据模板，为 svgImageAssetArray（svg类的图片资产数组）生成 _R_Svg class 的代码
     * */
    public static String generate__R_Svg_class(@NotNull List<String> svgImageAssetArray, @NotNull Map<String, String> svgImageAssetIdDict, @NotNull String packageName, @NotNull boolean shouldSupportNullsafety) {
        String all_g_Asset_method_code = "";

        for (String asset : svgImageAssetArray) {
            all_g_Asset_method_code += "\n";

            String assetId = svgImageAssetIdDict.get(asset);
            String assetComment = generateAssetComment(asset, packageName);
            String g_Asset_method_code = "";

            if (shouldSupportNullsafety) {
                g_Asset_method_code = String.format("  /// %s\n" +
                                "  // ignore: non_constant_identifier_names\n" +
                                "  AssetSvg %s({required double width, required double height}) {\n" +
                                "    final imageProvider = AssetSvg(asset.%s.keyName, width: width, height: height);\n" +
                                "    return imageProvider;\n" +
                                "  }",
                        assetComment,
                        assetId,
                        assetId);
            } else {
                g_Asset_method_code = String.format("  /// %s\n" +
                                "  // ignore: non_constant_identifier_names\n" +
                                "  AssetSvg %s({@required double width, @required double height}) {\n" +
                                "    final imageProvider = AssetSvg(asset.%s.keyName, width: width, height: height);\n" +
                                "    return imageProvider;\n" +
                                "  }",
                        assetComment,
                        assetId,
                        assetId);
            }

            all_g_Asset_method_code += g_Asset_method_code;
        }

        String code = String.format("/// This `_R_Svg` class is generated and contains references to static svg type image asset resources.\n" +
                        "// ignore: camel_case_types\n" +
                        "class _R_Svg {\n" +
                        "  const _R_Svg();\n" +
                        "\n" +
                        "  final asset = const _R_Svg_AssetResource();\n" +
                        "%s\n" +
                        "}",
                all_g_Asset_method_code);

        return code;
    }

    /*
     * 根据模板，为 textAssetArray（文本资产数组）生成 _R_Text class 的代码
     * */
    public static String generate__R_Text_class(@NotNull List<String> textAssetArray, @NotNull Map<String, String> textAssetIdDict, @NotNull String packageName) {
        String all_g_Asset_method_code = "";

        for (String asset : textAssetArray) {
            all_g_Asset_method_code += "\n";

            String assetId = textAssetIdDict.get(asset);
            String assetComment = generateAssetComment(asset, packageName);

            String g_Asset_method_code = String.format("  /// %s\n" +
                            "  // ignore: non_constant_identifier_names\n" +
                            "  Future<String> %s() {\n" +
                            "    final str = rootBundle.loadString(asset.%s.keyName);\n" +
                            "    return str;\n" +
                            "  }",
                    assetComment,
                    assetId,
                    assetId);

            all_g_Asset_method_code += g_Asset_method_code;
        }

        String code = String.format("/// This `_R_Text` class is generated and contains references to static text asset resources.\n" +
                        "// ignore: camel_case_types\n" +
                        "class _R_Text {\n" +
                        "  const _R_Text();\n" +
                        "\n" +
                        "  final asset = const _R_Text_AssetResource();\n" +
                        "%s\n" +
                        "}",
                all_g_Asset_method_code);

        return code;
    }

    /*
    * 为当前 font_family_name 生成 font_family_id；font_family_id 一般为 asset 的 font_family_name；
    * 但是为了保证 font_family_id 的健壮性，需要对 font_family_name 做以下加工处理：
    * - 处理非法字符：把除了字母（a-z, A-Z）、数字（0-9）、'_' 字符、'$' 字符之外的字符转换为 '_' 字符
    * - 首字母转化为小写
    * - 处理首字符异常情况：检测首字符是不是数字、'_'、'$'，若是则添加前缀字符"a"
    *
    * === Examples
    * a_font_family_name = "Amiri"
    * b_font_family_name = "Baloo-Thambi-2"
    * a_font_family_id = "amiri"
    * b_font_family_id = "baloo_Thambi_2"
    * */
    public static String generateFontFamilyId(@NotNull String fontFamilyName) {
        String fontFamilyId = fontFamilyName;

        // 过滤非法字符
        fontFamilyId = fontFamilyId.replaceAll("[^a-zA-Z0-9_$]", "_");

        // 检测首字符是不是字母；
        // 若是字母，则检测其是不是大写字母，若是，则转换为小写字母；
        // 若不是字母，则添加一个前缀字母“a”
        Character firstChar = fontFamilyId.charAt(0);
        if(Character.isLetter(firstChar)) {
            if(Character.isUpperCase(firstChar)) {
                String firstCharStr = firstChar.toString().toLowerCase();
                fontFamilyId = firstCharStr + fontFamilyId.substring(1);
            }
        } else {
            String firstCharStr = "a";
            fontFamilyId = firstCharStr + fontFamilyId;
        }

        return fontFamilyId;
    }

    /*
    * 根据模板，为 fontFamilyConfigArray（字体家族配置数组）生成 _R_FontFamily class 的代码
     * */
    public static String generate__R_FontFamily_class(@NotNull List<Map> fontFamilyConfigArray, @NotNull String packageName) {
        String all_g_AssetResource_property_code = "";

        for (Map fontFamilyConfig : fontFamilyConfigArray) {
            all_g_AssetResource_property_code += "\n";

            String fontFamilyName = (String)fontFamilyConfig.get("family");
            String fontFamilyId = generateFontFamilyId(fontFamilyName);

            String fontFamilyComment = String.format("font family: %s", fontFamilyName);

            String g_AssetResource_property_code = String.format("  /// %s\n" +
                    "  // ignore: non_constant_identifier_names\n" +
                    "  final %s = \"%s\";",
                    fontFamilyComment,
                    fontFamilyId,
                    fontFamilyName);

            all_g_AssetResource_property_code += g_AssetResource_property_code;
        }

        String code = String.format("/// This `_R_FontFamily` class is generated and contains references to static font asset resources.\n" +
                "// ignore: camel_case_types\n" +
                "class _R_FontFamily {\n" +
                "  const _R_FontFamily();\n" +
                "%s\n" +
                "}", all_g_AssetResource_property_code);
        return code;
    }
}
