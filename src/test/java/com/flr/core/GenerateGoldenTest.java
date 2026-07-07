package com.flr.core;

import com.flr.command.util.FlrFileUtil;
import com.flr.core.codegen.RDartGenerator;
import com.flr.core.model.ResourceScanResult;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * r.g.dart 代码生成的黄金回归测试（纯 JUnit，不启动 IDE 平台，绕开 VFS 与平台测试 bootstrap）。
 *
 * 思路：从基准工程 flutter_r_demo 的黄金 pubspec（git HEAD）取出已声明的 flutter.assets / fonts，
 * 按扩展名重建出与扫描器等价的 {@link ResourceScanResult}（分类用 FlrFileUtil 的纯 File 方法 + 升序排序），
 * 喂给 {@link RDartGenerator}，再与黄金 lib/r.g.dart 做**归一化比较**（同一 dart format 抹平 formatter 版本差异）。
 *
 * 覆盖范围：RDartGenerator 的 Step-9~19 全流程（asset_id 生成含去重、各 R 类模板、nullsafety 分支）
 * 对真实完整资产集的逐字节复现。扫描器（Step-4~6，VFS）与 pubspec 合并的端到端回归由
 * scripts/verify_flr_baseline.sh（真实 IDE）覆盖；其 headless 版待 P1 迁移到 Gradle 插件 2.x 后补齐
 * （当前 gradle-intellij-plugin 1.x + 2024.3 平台存在测试 bootstrap 兼容问题 IJPL-155647）。
 *
 * 运行参数（build.gradle 注入）：flr.demo.path（默认同级 flutter_r_demo）、flr.dart.path。缺失则跳过。
 */
public class GenerateGoldenTest {

    private static final String PACKAGE_NAME = "flutter_r_demo";
    private static final boolean IS_PACKAGE_PROJECT_TYPE = false; // 基准工程为 app 工程
    private static final boolean SHOULD_SUPPORT_NULLSAFETY = true;
    private static final int NORMALIZE_LINE_LENGTH = 100;

    private String demoPath() {
        return System.getProperty("flr.demo.path", "");
    }

    private String dartPath() {
        return System.getProperty("flr.dart.path", "");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void rDartMatchesGoldenAfterNormalization() throws Exception {
        File demoDir = new File(demoPath());
        if (demoPath().isEmpty() || !demoDir.isDirectory()) {
            System.out.println("[skip] flr.demo.path 未提供或不存在，跳过黄金回归：" + demoPath());
            return;
        }
        if (dartPath().isEmpty() || !new File(dartPath()).isFile()) {
            System.out.println("[skip] flr.dart.path 未提供，跳过黄金回归（需 dart 做归一化）：" + dartPath());
            return;
        }

        // 从黄金 pubspec 取 flutter.assets / fonts
        Map<String, Object> pubspec = loadYaml(gitShow(demoDir, "HEAD:pubspec.yaml"));
        Map<String, Object> flutter = (Map<String, Object>) pubspec.get("flutter");
        List<String> assets = (List<String>) flutter.get("assets");
        List<Map> fontFamilyConfigs = (List<Map>) flutter.get("fonts");

        // 按扩展名重建 non_svg / svg / text 资产并升序排序（复现扫描器的分类与排序）
        List<String> nonSvg = new ArrayList<>();
        List<String> svg = new ArrayList<>();
        List<String> text = new ArrayList<>();
        for (String asset : assets) {
            File assetFile = new File(asset);
            if (FlrFileUtil.isSvgImageResourceFile(assetFile)) {
                svg.add(asset);
            } else if (FlrFileUtil.isNonSvgImageResourceFile(assetFile)) {
                nonSvg.add(asset);
            } else if (FlrFileUtil.isTextResourceFile(assetFile)) {
                text.add(asset);
            }
        }
        Collections.sort(nonSvg);
        Collections.sort(svg);
        Collections.sort(text);
        List<String> images = new ArrayList<>(nonSvg);
        images.addAll(svg);
        Collections.sort(images);

        ResourceScanResult scan = new ResourceScanResult(
                images, nonSvg, svg, text, fontFamilyConfigs, Collections.emptyList());
        String rDart = RDartGenerator.generate(scan, PACKAGE_NAME, IS_PACKAGE_PROJECT_TYPE, SHOULD_SUPPORT_NULLSAFETY);

        String goldenRDart = gitShow(demoDir, "HEAD:lib/r.g.dart");
        assertEquals("r.g.dart 归一化后应与基线逐字节一致",
                dartFormat(goldenRDart), dartFormat(rDart));
    }

    // ---- helpers ----

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadYaml(String content) {
        Yaml yaml = new Yaml();
        for (Object doc : yaml.loadAll(content)) {
            if (doc instanceof Map) {
                return (Map<String, Object>) doc;
            }
        }
        return null;
    }

    private static String gitShow(File repoDir, String ref) throws Exception {
        Process p = new ProcessBuilder("git", "-C", repoDir.getPath(), "show", ref).start();
        String out = readStream(p.getInputStream());
        int code = p.waitFor();
        if (code != 0) {
            throw new IllegalStateException("git show " + ref + " 失败（code=" + code + "）");
        }
        return out;
    }

    private String dartFormat(String content) throws Exception {
        File tmp = File.createTempFile("flr_rg_", ".dart");
        try {
            Files.write(tmp.toPath(), content.getBytes(StandardCharsets.UTF_8));
            Process p = new ProcessBuilder(
                    dartPath(), "format", "--line-length", String.valueOf(NORMALIZE_LINE_LENGTH), tmp.getPath())
                    .redirectErrorStream(true).start();
            String out = readStream(p.getInputStream());
            int code = p.waitFor();
            if (code != 0) {
                throw new IllegalStateException("dart format 失败（code=" + code + "）：" + out);
            }
            return new String(Files.readAllBytes(tmp.toPath()), StandardCharsets.UTF_8);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    private static String readStream(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            bos.write(buf, 0, n);
        }
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }
}
