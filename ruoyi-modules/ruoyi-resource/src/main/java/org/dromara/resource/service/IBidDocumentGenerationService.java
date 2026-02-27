package org.dromara.resource.service;

import org.dromara.resource.domain.vo.BidSubmissionProgressVo;

/**
 * 标书文档生成Service接口
 *
 * @author ruoyi
 * @date 2026-02-26
 */
public interface IBidDocumentGenerationService {

    /**
     * 异步生成投标项目的所有文档
     *
     * @param submissionId 投标项目ID
     */
    void generateDocumentsAsync(Long submissionId);

    /**
     * 生成单个文档
     *
     * @param documentId 文档ID
     */
    void generateSingleDocument(Long documentId);

    /**
     * 获取生成进度
     *
     * @param submissionId 投标项目ID
     * @return 进度信息
     */
    BidSubmissionProgressVo getGenerationProgress(Long submissionId);

    /**
     * 取消生成任务
     *
     * @param submissionId 投标项目ID
     */
    void cancelGeneration(Long submissionId);

}
