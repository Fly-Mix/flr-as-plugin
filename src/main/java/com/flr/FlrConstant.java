package com.flr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FlrConstant {

    // 插件ID
    public static String PLUGIN_ID = "com.fly-mix.flr";

    // Flr的核心逻辑版本
    public static String CORE_VERSION = "3.0.0";

    // 插件对外展示的名称
    public static String PLUGIN_DISPLAY_NAME = "Flr";

    // Flr支持的非SVG类图片文件类型
    public static List<String> NON_SVG_IMAGE_FILE_TYPES= Arrays.asList(".png", ".jpg", ".jpeg", ".gif", ".webp", ".icon", ".bmp", ".wbmp");
    // Flr支持的SVG类图片文件类型
    public static List<String> SVG_IMAGE_FILE_TYPES= Arrays.asList(".svg");
    // Flr支持的图片文件类型
    public static List<String> IMAGE_FILE_TYPES= new ArrayList<String>(){
        {
            addAll(NON_SVG_IMAGE_FILE_TYPES);
            addAll(SVG_IMAGE_FILE_TYPES);
        }
    };
    // Flr支持的文本文件类型
    public static List<String> TEXT_FILE_TYPES= Arrays.asList(".txt", ".json", ".yaml", ".xml");
    // Flr支持的字体文件类型
    public static List<String> FONT_FILE_TYPES= Arrays.asList(".ttf", ".otf", ".ttc");

    // Flr优先考虑的非SVG类图片文件类型
    public static String PRIOR_NON_SVG_IMAGE_FILE_TYPE = ".png";
    // Flr优先考虑的SVG类图片文件类型
    public static String PRIOR_SVG_IMAGE_FILE_TYPE = ".svg";
    // Flr优先考虑的文本文件类型
    // 当前值为 ".*"， 意味所有文本文件类型的优先级都一样
    public static String PRIOR_TEXT_FILE_TYPE = ".*";
    // Flr优先考虑的字体文件类型
    // 当前值为 ".*"， 意味所有文本文件类型的优先级都一样
    public static String PRIOR_FONT_FILE_TYPE = ".*";

    // dartfmt工具的默认行长
    // 仅用于flr-cli
    public static int DARTFMT_LINE_LENGTH = 80;
}
