package org.dromara.review.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
import org.dromara.review.service.SearchablePdfService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * OCR 工具接口(给城更等外部系统调)
 * <p>
 * - {@code POST /review/ocr/searchable-pdf}:上传 PDF,返回带不可见文字层的 PDF
 * - {@code POST /review/ocr/probe}:仅探测 PDF 是否已有文字层
 * <p>
 * 走 {@link SaIgnore} 跳过登录鉴权。生产环境建议在网关层做 IP 白名单或加内部 token。
 *
 * @author Linson
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/review/ocr")
public class SearchablePdfController extends BaseController {

    private final SearchablePdfService searchablePdfService;

    /**
     * 上传一个 PDF,返回处理后的 PDF 字节流。
     * 响应头 X-Has-Text-Layer 标记是否原本就是打印版(true=未做 OCR,直接返回原文件)。
     */
    @SaIgnore
    @PostMapping(value = "/searchable-pdf",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
        produces = MediaType.APPLICATION_PDF_VALUE)
    public void buildSearchablePdf(@RequestParam("file") MultipartFile file,
                                    HttpServletResponse response) throws IOException {
        if (file == null || file.isEmpty()) {
            response.sendError(400, "file is required");
            return;
        }
        SearchablePdfService.BuildResult result;
        try {
            result = searchablePdfService.build(file);
        } catch (IOException e) {
            log.error("[OCR] searchable-pdf 处理失败: {}", e.getMessage(), e);
            response.sendError(500, e.getMessage());
            return;
        }
        response.setContentType(MediaType.APPLICATION_PDF_VALUE);
        response.setHeader("X-Has-Text-Layer", String.valueOf(result.hasTextLayerOriginally()));
        response.setContentLength(result.pdfBytes().length);
        try (OutputStream out = response.getOutputStream()) {
            out.write(result.pdfBytes());
        }
    }

    /** 探测一个 PDF 是否已有文字层。返回 {@code {hasTextLayer: bool}} */
    @SaIgnore
    @PostMapping(value = "/probe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<Map<String, Object>> probe(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return R.fail("file is required");
        }
        boolean hasText = searchablePdfService.hasTextLayer(file);
        Map<String, Object> data = new HashMap<>();
        data.put("hasTextLayer", hasText);
        data.put("filename", file.getOriginalFilename());
        data.put("size", file.getSize());
        return R.ok(data);
    }
}
