package org.dromara.common.ai.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.fontbox.ttf.TrueTypeCollection;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * SearchablePdfBuilder 用的中文字体加载器
 * <p>
 * PDFBox 不内置 CJK,需要从系统/资源加载字体。策略:
 * 1. 先找 classpath 资源 {@code /fonts/cjk-default.ttf}(项目可手动放)
 * 2. 找操作系统字体目录的常见中文字体
 *    - Linux: 文泉驿/思源黑体
 *    - Windows: 微软雅黑(msyh.ttc 是 TTC 集合,需要解出单字体)
 *    - macOS: PingFang
 * 3. 都找不到则抛异常,不可降级到 Latin 字体——会丢失全部中文
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
        throw new IOException("找不到可用的中文字体。请将 ttf 放到 classpath:/fonts/cjk-default.ttf, "
            + "或安装常见中文字体(微软雅黑/思源黑体/文泉驿)");
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
