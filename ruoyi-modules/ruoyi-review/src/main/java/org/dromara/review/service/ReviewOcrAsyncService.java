package org.dromara.review.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.oss.core.OssClient;
import org.dromara.common.oss.entity.UploadResult;
import org.dromara.common.oss.factory.OssFactory;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.review.domain.ReviewTaskFile;
import org.dromara.review.mapper.ReviewTaskFileMapper;
import org.dromara.system.domain.vo.SysOssVo;
import org.dromara.system.service.ISysOssService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 审核工具附件的 OCR 异步编排（生成 searchable PDF 并持久化到 review_task_file）。
 * <p>
 * 独立成 bean：{@code @Async} 必须经 Spring 代理调用才生效，挂在被自调用的内部方法上会失效。
 * 懒触发：查看器/向导轮询结果时，对 ocr_status 为空的 PDF 文件发起一次，拿到 searchableUrl 后查看器自动重载。
 *
 * @author Linson
 * @date 2026-06-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewOcrAsyncService {

    private final ReviewTaskFileMapper taskFileMapper;
    private final ISysOssService ossService;
    private final SearchablePdfService searchablePdfService;

    /**
     * 对单个附件文件做 OCR：PDF 扫描件 → searchable PDF 上传回 OSS；打印件标记 SKIP；非 PDF 标记 NONE。
     * 状态机写回 review_task_file.ocr_status / searchable_url。
     */
    @Async
    public void processFileAsync(Long fileId) {
        ReviewTaskFile file = taskFileMapper.selectById(fileId);
        if (file == null) {
            return;
        }
        // 非 PDF 无需 searchable，直接 NONE
        if (!isPdf(file)) {
            updateOcr(file, "NONE", null);
            return;
        }
        // 抢占：标记 RUNNING（避免重复触发）
        updateOcr(file, "RUNNING", null);
        try {
            byte[] bytes = downloadBytes(file);
            if (bytes == null || bytes.length == 0) {
                log.warn("[ReviewOcr] 文件下载为空 fileId={}, ossId={}", fileId, file.getOssId());
                updateOcr(file, "FAIL", null);
                return;
            }
            SearchablePdfService.BuildResult result = searchablePdfService.buildFromBytes(bytes, file.getFileName());
            if (result.hasTextLayerOriginally()) {
                // 打印件本就有文字层：查看器直接用原文件即可，searchableUrl 留空
                updateOcr(file, "SKIP", null);
                return;
            }
            // 扫描件：上传生成的 searchable PDF 回 OSS，记录 url
            OssClient storage = OssFactory.instance();
            UploadResult upload = storage.uploadSuffix(result.pdfBytes(), ".pdf", "application/pdf");
            updateOcr(file, "SUCCESS", upload.getUrl());
            log.info("[ReviewOcr] searchable PDF 生成成功 fileId={}, url={}", fileId, upload.getUrl());
        } catch (Exception e) {
            log.error("[ReviewOcr] 生成 searchable PDF 失败 fileId={}: {}", fileId, e.getMessage(), e);
            updateOcr(file, "FAIL", null);
        }
    }

    private boolean isPdf(ReviewTaskFile file) {
        String type = file.getFileType();
        if (type != null && type.toLowerCase().contains("pdf")) {
            return true;
        }
        String name = file.getFileName();
        return name != null && name.toLowerCase().endsWith(".pdf");
    }

    /** 优先按 ossId 下载，缺失时退回 filePath（http URL） */
    private byte[] downloadBytes(ReviewTaskFile file) throws Exception {
        if (file.getOssId() != null && file.getOssId() > 0) {
            SysOssVo ossVo = TenantHelper.ignore(() -> ossService.getById(file.getOssId()));
            if (ossVo != null) {
                OssClient storage = OssFactory.instance(ossVo.getService());
                Path tempFile = storage.fileDownload(ossVo.getFileName());
                try {
                    return Files.readAllBytes(tempFile);
                } finally {
                    try { Files.deleteIfExists(tempFile); } catch (Exception ignore) {}
                }
            }
        }
        String url = file.getFilePath();
        if (url != null && url.startsWith("http")) {
            try (var in = java.net.URI.create(url).toURL().openStream()) {
                return in.readAllBytes();
            }
        }
        return null;
    }

    private void updateOcr(ReviewTaskFile file, String status, String searchableUrl) {
        ReviewTaskFile patch = new ReviewTaskFile();
        patch.setId(file.getId());
        patch.setOcrStatus(status);
        patch.setSearchableUrl(searchableUrl);
        TenantHelper.ignore(() -> taskFileMapper.updateById(patch));
    }
}
