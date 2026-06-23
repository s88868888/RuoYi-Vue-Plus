package org.dromara.review.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.ai.dto.AiModelConfigDto;
import org.dromara.common.ai.ocr.OcrProvider;
import org.dromara.common.ai.ocr.PaddleOcrProvider;
import org.dromara.common.ai.util.PdfTextExtractor;
import org.dromara.common.ai.util.SearchablePdfBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Searchable PDF 生成服务
 * <p>
 * 给外部系统(目前主要是城更)使用,把扫描件 PDF 转成带不可见文字层的 PDF。
 * 内部用 {@link SearchablePdfBuilder} + {@link PaddleOcrProvider}。
 *
 * @author Linson
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchablePdfService {

    private final List<OcrProvider> providers;
    private final IReviewModelConfigService modelConfigService;

    /**
     * 上传 PDF,返回处理后的 searchable PDF 字节数组。
     * 已经是有文字层的 PDF 直接返回原字节,避免重复 OCR。
     */
    public BuildResult build(MultipartFile pdf) throws IOException {
        File tmp = Files.createTempFile("ocr-src-", ".pdf").toFile();
        try {
            try (var in = pdf.getInputStream()) {
                Files.copy(in, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return buildFromTempFile(tmp, pdf.getOriginalFilename());
        } finally {
            try { Files.deleteIfExists(tmp.toPath()); } catch (Exception ignore) {}
        }
    }

    /**
     * 直接对字节流构建 searchable PDF（OSS 已存文件走这条，无需 MultipartFile 包装）。
     * 已有文字层直接返回原字节。
     */
    public BuildResult buildFromBytes(byte[] pdfBytes, String filename) throws IOException {
        File tmp = Files.createTempFile("ocr-src-", ".pdf").toFile();
        try {
            Files.write(tmp.toPath(), pdfBytes);
            return buildFromTempFile(tmp, filename);
        } finally {
            try { Files.deleteIfExists(tmp.toPath()); } catch (Exception ignore) {}
        }
    }

    /** 共用：对临时 PDF 文件做文字层探测 + 必要时 OCR 生成 searchable PDF */
    private BuildResult buildFromTempFile(File tmp, String filename) throws IOException {
        boolean hasText = PdfTextExtractor.hasTextLayer(tmp);
        if (hasText) {
            log.info("[SearchablePdfService] {} 已有文字层,直接返回原文件", filename);
            return new BuildResult(true, Files.readAllBytes(tmp.toPath()));
        }
        // 强制用 paddleocr:只有它实现了 ocrStructured(返回坐标)
        OcrProvider provider = providers.stream()
            .filter(p -> PaddleOcrProvider.NAME.equalsIgnoreCase(p.getName()))
            .findFirst()
            .orElseThrow(() -> new IOException("未找到 PaddleOcrProvider,无法构建 searchable PDF"));
        AiModelConfigDto ocrConfig = resolvePaddleOcrConfig();
        byte[] out = SearchablePdfBuilder.build(tmp, provider, ocrConfig);
        return new BuildResult(false, out);
    }

    private AiModelConfigDto resolvePaddleOcrConfig() {
        try {
            AiModelConfigDto config = modelConfigService.getDefaultByPurposeAndProvider("ocr", PaddleOcrProvider.NAME);
            if (config == null) {
                log.info("[SearchablePdfService] 未找到启用的 PaddleOCR 模型配置,使用 yml 连接参数 + searchable PDF 坐标安全选项");
                return searchablePdfFallbackConfig();
            }
            log.info("[SearchablePdfService] 使用 OCR 模型配置生成 searchable PDF: id={}, code={}, provider={}, baseUrl={}, extraOptions={}",
                config.getId(), config.getCode(), config.getProvider(), config.getBaseUrl(), config.getExtraOptions());
            config.setExtraOptions(searchablePdfOptions(config.getExtraOptions()));
            return config;
        } catch (Exception e) {
            log.warn("[SearchablePdfService] 加载 OCR 模型配置失败,使用 yml 连接参数 + searchable PDF 坐标安全选项: {}", e.getMessage());
            return searchablePdfFallbackConfig();
        }
    }

    private AiModelConfigDto searchablePdfFallbackConfig() {
        return AiModelConfigDto.builder()
            .provider(PaddleOcrProvider.NAME)
            .extraOptions(searchablePdfOptions(null))
            .build();
    }

    private Map<String, Object> searchablePdfOptions(Map<String, Object> source) {
        Map<String, Object> options = new LinkedHashMap<>();
        if (source != null) {
            options.putAll(source);
        }
        // searchable PDF 坐标写回要求 OCR 返回的坐标必须和输入渲染图一致。
        options.put("useDocOrientationClassify", false);
        options.put("useDocUnwarping", false);
        options.put("useTextlineOrientation", false);
        return options;
    }

    /** 探测 PDF 是否已有文字层(打印版),不做 OCR */
    public boolean hasTextLayer(MultipartFile pdf) throws IOException {
        File tmp = Files.createTempFile("ocr-probe-", ".pdf").toFile();
        try {
            try (var in = pdf.getInputStream()) {
                Files.copy(in, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return PdfTextExtractor.hasTextLayer(tmp);
        } finally {
            try { Files.deleteIfExists(tmp.toPath()); } catch (Exception ignore) {}
        }
    }

    public record BuildResult(boolean hasTextLayerOriginally, byte[] pdfBytes) {}
}
