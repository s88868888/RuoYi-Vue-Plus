package org.dromara.review.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.List;

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
            boolean hasText = PdfTextExtractor.hasTextLayer(tmp);
            if (hasText) {
                log.info("[SearchablePdfService] {} 已有文字层,直接返回原文件", pdf.getOriginalFilename());
                return new BuildResult(true, Files.readAllBytes(tmp.toPath()));
            }
            // 强制用 paddleocr:只有它实现了 ocrStructured(返回坐标)
            OcrProvider provider = providers.stream()
                .filter(p -> PaddleOcrProvider.NAME.equalsIgnoreCase(p.getName()))
                .findFirst()
                .orElseThrow(() -> new IOException("未找到 PaddleOcrProvider,无法构建 searchable PDF"));
            byte[] out = SearchablePdfBuilder.build(tmp, provider);
            return new BuildResult(false, out);
        } finally {
            try { Files.deleteIfExists(tmp.toPath()); } catch (Exception ignore) {}
        }
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
