package org.dromara.review.service;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 审核任务「工程包」导出 / 导入服务。
 * <p>
 * 工程包 = 一个自包含 .zip：每条任务的整行数据（含 focusData/redactData/resultJson/统计）、
 * 关联标准 + 规则快照、审核结果明细、以及按 ossId 拉取的附件原件字节（含 OCR 后的 searchable PDF）。
 * 导出供离线归档 / 换环境搬运；导入在目标库重新落库成全新任务（新 id、附件重传 OSS），
 * 列表里多出可直接预览、可复看的真实记录。
 */
public interface IReviewProjectService {

    /**
     * 导出选中任务为工程包 zip，直接流式写入响应。
     *
     * @param taskIds  勾选的任务ID列表
     * @param response HTTP 响应（设置下载头 + 写 zip 流）
     */
    void exportProject(List<Long> taskIds, HttpServletResponse response);

    /**
     * 导入工程包 zip，在当前库重新落库为全新任务。
     *
     * @param file 上传的工程包 zip
     * @return 新建的任务ID列表
     */
    List<Long> importProject(MultipartFile file);
}
