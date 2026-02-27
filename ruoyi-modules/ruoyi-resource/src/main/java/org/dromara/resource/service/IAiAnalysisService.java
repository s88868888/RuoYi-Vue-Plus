package org.dromara.resource.service;

/**
 * AI分析服务接口
 *
 * @author ruoyi
 * @date 2026-02-24
 */
public interface IAiAnalysisService {

    /**
     * 分析招标项目
     *
     * @param projectId 项目ID
     * @param prompt 自定义提示词（可选）
     * @return 分析结果
     */
    String analyzeBidProject(Long projectId, String prompt);

    /**
     * 异步分析招标项目
     *
     * @param projectId 项目ID
     * @param prompt 自定义提示词（可选）
     */
    void analyzeBidProjectAsync(Long projectId, String prompt);

    /**
     * 提取评分标准
     *
     * @param projectId 项目ID
     * @param prompt 自定义提示词（可选）
     */
    String extractScoringCriteria(Long projectId, String prompt);

    /**
     * 异步提取评分标准
     *
     * @param projectId 项目ID
     * @param prompt 自定义提示词（可选）
     */
    void extractScoringCriteriaAsync(Long projectId, String prompt);

    /**
     * 契合度分析
     *
     * @param projectId 项目ID
     * @param prompt 自定义提示词（可选）
     * @return 分析结果
     */
    String analyzeMatchDegree(Long projectId, String prompt);

    /**
     * 异步契合度分析
     *
     * @param projectId 项目ID
     * @param prompt 自定义提示词（可选）
     */
    void analyzeMatchDegreeAsync(Long projectId, String prompt);

}
