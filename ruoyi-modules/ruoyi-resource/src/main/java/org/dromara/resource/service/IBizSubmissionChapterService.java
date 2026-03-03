package org.dromara.resource.service;

import org.dromara.resource.domain.vo.BizSubmissionChapterVo;

import java.util.List;
import java.util.Map;

/**
 * 标书章节Service接口
 *
 * @author ruoyi
 * @date 2026-02-26
 */
public interface IBizSubmissionChapterService {

    /**
     * 查询章节详情
     */
    BizSubmissionChapterVo queryById(Long id);

    /**
     * 获取章节树
     *
     * @param submissionId 投标项目ID
     * @param documentId 文档ID
     * @return 章节树
     */
    List<BizSubmissionChapterVo> getChapterTree(Long submissionId, Long documentId);

    /**
     * 生成章节内容
     *
     * @param id 章节ID
     */
    void generateChapter(Long id);

    /**
     * 填充模板章节
     *
     * @param id 章节ID
     */
    void fillTemplate(Long id);

    /**
     * 保存章节内容
     *
     * @param id 章节ID
     * @param content 章节内容
     */
    void saveChapterContent(Long id, String content);

    /**
     * 重新生成章节
     *
     * @param id 章节ID
     */
    void regenerateChapter(Long id);

    /**
     * 删除章节
     *
     * @param id 章节ID
     * @return 是否成功
     */
    Boolean deleteById(Long id);

    /**
     * 添加章节
     *
     * @param submissionDocumentId 文档ID
     * @param parentId 父章节ID
     * @param chapterTitle 章节标题
     * @param chapterType 章节类型
     * @param reasonDescription 章节说明
     */
    void addChapter(Long submissionDocumentId, Long parentId, String chapterTitle, String chapterType, String reasonDescription);

    /**
     * AI 生成章节结构（异步）
     *
     * @param submissionId 投标项目ID
     * @param documentConfigId 文档配置ID
     */
    void generateChapterStructure(Long submissionId, Long documentConfigId);

    /**
     * 执行生成章节结构（内部异步方法）
     *
     * @param submissionId 投标项目ID
     * @param documentConfigId 文档配置ID
     * @param userId 用户ID
     * @param tenantId 租户ID（异步线程无安全上下文，需显式传递）
     */
    void doGenerateChapterStructure(Long submissionId, Long documentConfigId, Long userId, String tenantId);

    /**
     * 重新生成章节结构（异步）
     *
     * @param submissionId 投标项目ID
     * @param documentConfigId 文档配置ID
     */
    void regenerateChapterStructure(Long submissionId, Long documentConfigId);

    /**
     * 批量更新章节排序
     *
     * @param sortItems 排序列表，每项包含 id、parentId、sortOrder
     */
    void updateChapterSort(List<Map<String, Object>> sortItems);

    /**
     * 清空文档下所有章节
     *
     * @param submissionId 投标项目ID
     * @param documentId   文档配置ID
     */
    void clearChapters(Long submissionId, Long documentId);

}
