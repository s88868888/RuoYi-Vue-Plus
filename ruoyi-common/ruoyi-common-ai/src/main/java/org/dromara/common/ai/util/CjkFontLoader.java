package org.dromara.common.ai.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeCollection;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * SearchablePdfBuilder 用的中文字体加载器
 * <p>
 * PDFBox 不内置 CJK,需要从系统/资源加载字体。策略:
 * 1. 先找 classpath 资源 {@code /fonts/cjk-default.ttf}(项目可手动放)
 * 2. 找操作系统字体目录的常见中文字体(按已知绝对路径)
 *    - Linux: 文泉驿/思源黑体
 *    - Windows: 微软雅黑(msyh.ttc 是 TTC 集合,需要解出单字体)
 *    - macOS: PingFang
 * 3. 兜底:递归扫描 {@link #SCAN_DIRS} 字体目录,挑第一个真正覆盖中文的 ttf/ttc
 *    (Alpine/musl、各发行版字体落盘路径不一致,硬编码绝对路径常常落空,靠扫描兜住)
 * 4. 都找不到则抛异常,不可降级到 Latin 字体——会丢失全部中文
 * <p>
 * 字体只用于 PDF 文字层(不可见 rendering mode 3),不显示给用户看,选字符集覆盖最广的即可。
 *
 * @author Linson
 */
@Slf4j
public final class CjkFontLoader {

    private CjkFontLoader() {}

    /** 优先扫描的字体路径(按 OS 常见位置) */
    private static final String[] CANDIDATE_PATHS = {
        // Linux
        "/usr/share/fonts/opentype/source-han-sans/SourceHanSansSC-Regular.otf",
        "/usr/share/fonts/truetype/source-han-sans/SourceHanSansSC-Regular.otf",
        "/usr/share/fonts/wqy-zenhei/wqy-zenhei.ttc",
        "/usr/share/fonts/wqy-microhei/wqy-microhei.ttc",
        "/usr/share/fonts/truetype/arphic/ukai.ttc",
        "/usr/share/fonts/truetype/arphic/uming.ttc",
        // Windows
        "C:/Windows/Fonts/msyh.ttc",
        "C:/Windows/Fonts/msyhl.ttc",
        "C:/Windows/Fonts/msyhbd.ttc",
        "C:/Windows/Fonts/simsun.ttc",
        "C:/Windows/Fonts/simhei.ttf",
        "C:/Windows/Fonts/simfang.ttf",
        "C:/Windows/Fonts/simkai.ttf",
        // macOS
        "/System/Library/Fonts/PingFang.ttc",
        "/System/Library/Fonts/STHeiti Medium.ttc",
        "/Library/Fonts/Arial Unicode.ttf",
    };

    /** TTC 集合内优先选择的字体名(按 Windows/通用顺序) */
    private static final String[] PREFERRED_TTC_FONT_NAMES = {
        "Microsoft YaHei",
        "MicrosoftYaHei",
        "Microsoft YaHei UI",
        "SimSun",
        "NSimSun",
        "PingFangSC-Regular",
        "STHeiti",
        "WenQuanYi Zen Hei",
        "WenQuanYi Micro Hei",
    };

    /** classpath 资源路径(如果项目手动放了字体) */
    private static final String CLASSPATH_FONT = "/fonts/cjk-default.ttf";

    /** 兜底扫描的字体根目录(覆盖各发行版 / Alpine / 用户字体) */
    private static final String[] SCAN_DIRS = {
        "/usr/share/fonts",
        "/usr/local/share/fonts",
        "/root/.fonts",
    };

    /** 扫描兜底命中的字体文件缓存,避免每次构建 PDF 都遍历磁盘 */
    private static volatile File scannedFontFile;

    /**
     * 加载一个中文字体并嵌入到指定 PDDocument。
     * 第一次调用会做 IO,但字体本身在 PDF 内只嵌入一次(PDFBox 自动子集)。
     *
     * @param doc 要嵌入字体的 PDF 文档
     * @return PDType0Font(支持中文)
     */
    public static PDType0Font load(PDDocument doc) throws IOException {
        // 1. classpath 资源优先
        InputStream rs = CjkFontLoader.class.getResourceAsStream(CLASSPATH_FONT);
        if (rs != null) {
            try (InputStream in = rs) {
                log.debug("[CjkFontLoader] 用 classpath 字体: {}", CLASSPATH_FONT);
                return PDType0Font.load(doc, in, true);
            }
        }
        // 2. 系统字体扫描
        for (String path : CANDIDATE_PATHS) {
            File f = new File(path);
            if (!f.exists() || !f.canRead()) continue;
            try {
                String lower = path.toLowerCase();
                if (lower.endsWith(".ttc")) {
                    PDType0Font font = loadFromTtc(doc, f);
                    if (font != null) {
                        log.debug("[CjkFontLoader] 用系统 TTC 字体: {}", path);
                        return font;
                    }
                } else {
                    log.debug("[CjkFontLoader] 用系统字体: {}", path);
                    return PDType0Font.load(doc, f);
                }
            } catch (Exception e) {
                log.warn("[CjkFontLoader] 加载字体失败,尝试下一个: {} - {}", path, e.toString());
            }
        }
        // 3. 兜底:递归扫描字体目录,找第一个真正覆盖中文的 ttf/ttc
        //    解决 Alpine/各发行版字体落盘路径与 CANDIDATE_PATHS 不一致导致的找不到
        PDType0Font scanned = loadFromScan(doc);
        if (scanned != null) {
            return scanned;
        }
        throw new IOException("找不到可用的中文字体。请将 ttf 放到 classpath:/fonts/cjk-default.ttf, "
            + "或安装常见中文字体(微软雅黑/思源黑体/文泉驿)");
    }

    /**
     * 递归扫描 {@link #SCAN_DIRS},挑第一个真正覆盖中文('中')的 ttf/ttc 嵌入。
     * 命中后缓存文件路径,后续调用直接复用,不再遍历磁盘。
     */
    private static PDType0Font loadFromScan(PDDocument doc) {
        File cached = scannedFontFile;
        if (cached != null && cached.exists() && cached.canRead()) {
            try {
                PDType0Font font = loadCoveringFont(doc, cached);
                if (font != null) return font;
            } catch (Exception e) {
                log.warn("[CjkFontLoader] 缓存字体加载失败,重新扫描: {} - {}", cached, e.toString());
                scannedFontFile = null;
            }
        }
        for (String root : SCAN_DIRS) {
            File dir = new File(root);
            if (!dir.isDirectory()) continue;
            Deque<File> stack = new ArrayDeque<>();
            stack.push(dir);
            while (!stack.isEmpty()) {
                File cur = stack.pop();
                File[] children = cur.listFiles();
                if (children == null) continue;
                for (File child : children) {
                    if (child.isDirectory()) {
                        stack.push(child);
                        continue;
                    }
                    String lower = child.getName().toLowerCase();
                    // 只认 TrueType(.ttf / .ttc);.otf 多为 CFF,PDFBox 嵌入不稳,跳过
                    if (!lower.endsWith(".ttf") && !lower.endsWith(".ttc")) continue;
                    if (!child.canRead()) continue;
                    try {
                        PDType0Font font = loadCoveringFont(doc, child);
                        if (font != null) {
                            scannedFontFile = child;
                            log.info("[CjkFontLoader] 扫描命中中文字体: {}", child.getAbsolutePath());
                            return font;
                        }
                    } catch (Exception e) {
                        log.debug("[CjkFontLoader] 扫描字体不可用,跳过: {} - {}", child, e.toString());
                    }
                }
            }
        }
        return null;
    }

    /**
     * 加载字体文件,但仅当它真正覆盖中文时才返回;否则返回 null(避免选到纯西文字体)。
     */
    private static PDType0Font loadCoveringFont(PDDocument doc, File f) throws IOException {
        if (f.getName().toLowerCase().endsWith(".ttc")) {
            try (TrueTypeCollection ttc = new TrueTypeCollection(f)) {
                final TrueTypeFont[] holder = new TrueTypeFont[1];
                ttc.processAllFonts(ttf -> {
                    if (holder[0] == null && coversCjk(ttf)) {
                        holder[0] = ttf;
                    }
                });
                if (holder[0] != null) {
                    return PDType0Font.load(doc, holder[0], true);
                }
            }
            return null;
        }
        // 单 ttf:先用 fontbox 解析校验中文覆盖,再嵌入
        try (RandomAccessReadBufferedFile raf = new RandomAccessReadBufferedFile(f)) {
            TrueTypeFont ttf = new TTFParser().parse(raf);
            if (!coversCjk(ttf)) {
                return null;
            }
            return PDType0Font.load(doc, ttf, true);
        }
    }

    /** 用 cmap 判断字体是否覆盖中文(取常用字 '中' U+4E2D 探测) */
    private static boolean coversCjk(TrueTypeFont ttf) {
        try {
            return ttf.getUnicodeCmapLookup().getGlyphId('中') > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从 TTC(TrueType Collection)文件中取出一个 TrueTypeFont 并嵌入。
     * <p>
     * 注意:PDType0Font.load(doc, ttf, true) 必须在 TrueTypeCollection 仍打开时调用,
     * close 后底层 ByteBuffer 会失效。所以本方法把 load 放在 try-with-resources 内部。
     */
    private static PDType0Font loadFromTtc(PDDocument doc, File f) throws IOException {
        try (TrueTypeCollection ttc = new TrueTypeCollection(f)) {
            // 优先按已知中文字体名查找
            for (String name : PREFERRED_TTC_FONT_NAMES) {
                try {
                    TrueTypeFont ttf = ttc.getFontByName(name);
                    if (ttf != null) {
                        return PDType0Font.load(doc, ttf, true);
                    }
                } catch (IOException ignore) {
                    // 继续尝试下一个名字
                }
            }
            // 找不到指定名字,取集合内第一个字体
            final TrueTypeFont[] holder = new TrueTypeFont[1];
            ttc.processAllFonts(ttf -> {
                if (holder[0] == null) {
                    holder[0] = ttf;
                }
            });
            if (holder[0] != null) {
                return PDType0Font.load(doc, holder[0], true);
            }
        }
        return null;
    }
}
