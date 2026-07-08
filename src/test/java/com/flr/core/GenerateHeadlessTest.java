package com.flr.core;

import com.flr.command.util.FlrAssetUtil;
import com.flr.core.codegen.RDartGenerator;
import com.flr.core.model.ResourceScanResult;
import com.flr.core.scan.AssetScanner;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * generate 流水线的 headless 全流程基准回归测试（走真实 VFS 扫描）。
 *
 * 与纯 codegen 的 {@link GenerateGoldenTest} 不同，本测试真正跑 {@link AssetScanner} 扫描基准工程
 * flutter_r_demo 的磁盘资源，再断言两条验收标准；覆盖扫描器（Step-4~6）+ pubspec 合并这段 pure test 覆盖不到的逻辑。
 *
 * 需要平台测试框架（BasePlatformTestCase 启动 Application + VFS）。这在 gradle-intellij-plugin 1.x + 2024.3
 * 下曾因测试 bootstrap 兼容问题（SettingsController，IJPL-155647）无法运行；迁移到 IntelliJ Platform
 * Gradle Plugin 2.x + 显式 testFramework(Platform) 后恢复。
 *
 * 经三接缝解耦，本测试不依赖 Dart/Flutter 插件与真实 SDK：nullsafety 用固定值 true；格式化用外部 dart format 归一化比较。
 * 全程内存计算，绝不写入基准工程。运行参数：flr.demo.path（默认同级 flutter_r_demo）、flr.dart.path。缺失则跳过。
 */
public class GenerateHeadlessTest extends BasePlatformTestCase {

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

    @SuppressWarnings("unchecked")
    public void testGenerateReproducesGolden() throws Exception {
        File demoDir = new File(demoPath());
        if (demoPath().isEmpty() || !demoDir.isDirectory()) {
            System.out.println("[skip] flr.demo.path 未提供或不存在，跳过 headless 全流程回归：" + demoPath());
            return;
        }
        // 规范化去掉 ".."：VFS 文件路径是规范化的，flutterProjectDir 必须一致，否则资产前缀剥离失败导致绝对路径泄漏
        demoDir = demoDir.getCanonicalFile();
        String root = demoDir.getPath();

        // 从基准工程 flr 配置得到合法资源目录（相对 -> 绝对，过滤不存在）
        Map<String, Object> pubspec = loadYaml(new String(
                Files.readAllBytes(new File(demoDir, "pubspec.yaml").toPath()), StandardCharsets.UTF_8));
        Map<String, Object> flrConfig = (Map<String, Object>) pubspec.get("flr");
        List<String> assetsLegalDirs = toLegalDirs(root, (List<String>) flrConfig.get("assets"));
        List<String> fontsLegalDirs = toLegalDirs(root, (List<String>) flrConfig.get("fonts"));

        // 刷新 VFS，使扫描器（LocalFileSystem/VfsUtilCore）能看到磁盘上的资源
        refreshVfs(assetsLegalDirs);
        refreshVfs(fontsLegalDirs);

        // 真实扫描 + 生成（内存）
        ResourceScanResult scan = AssetScanner.scan(root, PACKAGE_NAME, assetsLegalDirs, fontsLegalDirs);
        String rDart = RDartGenerator.generate(scan, PACKAGE_NAME, IS_PACKAGE_PROJECT_TYPE, SHOULD_SUPPORT_NULLSAFETY);

        // ---- 标准 1：r.g.dart 归一化比较（脱离 formatter 版本）----
        String goldenRDart = gitShow(demoDir, "HEAD:lib/r.g.dart");
        if (dartPath().isEmpty() || !new File(dartPath()).isFile()) {
            System.out.println("[skip] flr.dart.path 未提供，跳过标准1（r.g.dart 归一化比较）：" + dartPath());
        } else {
            assertEquals("标准1：扫描+生成的 r.g.dart 归一化后应与基线一致",
                    dartFormat(goldenRDart), dartFormat(rDart));
        }

        // ---- 标准 2：flutter 节点（assets + fonts）YAML 对象比较 ----
        Map<String, Object> goldenPubspec = loadYaml(gitShow(demoDir, "HEAD:pubspec.yaml"));
        Map<String, Object> goldenFlutter = (Map<String, Object>) goldenPubspec.get("flutter");
        List<String> goldenAssets = new ArrayList<>((List<String>) goldenFlutter.get("assets"));
        List<Map> goldenFonts = (List<Map>) goldenFlutter.get("fonts");

        List<String> newAssets = new ArrayList<>();
        newAssets.addAll(scan.getImageAssets());
        newAssets.addAll(scan.getTextAssets());
        // mergeFlutterAssets 会修改入参，故用副本
        List<String> mergedAssets = FlrAssetUtil.mergeFlutterAssets(
                root, PACKAGE_NAME, newAssets, new ArrayList<>(goldenAssets));

        assertEquals("标准2：flutter.assets 应与基线一致", goldenAssets, mergedAssets);
        assertEquals("标准2：flutter.fonts 应与基线一致", goldenFonts, scan.getFontFamilyConfigs());
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

    private static List<String> toLegalDirs(String root, List<String> relativeDirs) {
        List<String> legal = new ArrayList<>();
        if (relativeDirs == null) {
            return legal;
        }
        for (String rel : relativeDirs) {
            if (rel == null || rel.trim().isEmpty()) {
                continue;
            }
            File dir = new File(root + "/" + rel);
            if (dir.isDirectory()) {
                legal.add(dir.getPath());
            }
        }
        return legal;
    }

    private static void refreshVfs(List<String> dirs) {
        for (String dir : dirs) {
            VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(dir));
            if (vf != null) {
                vf.refresh(false, true); // 递归刷新
            }
        }
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
