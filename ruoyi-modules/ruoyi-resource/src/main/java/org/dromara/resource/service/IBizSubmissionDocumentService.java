package org.dromara.resource.service;

import jakarta.servlet.http.HttpServletResponse;
import org.dromara.resource.domain.vo.BizSubmissionDocumentVo;

import java.util.List;

/**
 * 标书文档版本Service接口
 */
public interface IBizSubmissionDocumentService {

    /**
     * 查询指定投标项目的最新版本文档列表
     */
    List<BizSubmissionDocumentVo> listLatestBySubmissionId(Long submissionId);

    /**
     * 查询某配置下所有历史版本（按版本倒序）
     */
    List<BizSubmissionDocumentVo> listVersionsByConfigId(Long documentConfigId);

    /**
     * 保存当前章节内容为新版本
     */
    BizSubmissionDocumentVo saveVersion(Long documentConfigId, Long submissionId);

    /**
     * 导出文档（docx/pdf）
     */
    void exportDocument(Long documentId, String format, HttpServletResponse response);
}
