package org.dromara.resource.domain.dto;

import lombok.Data;

/**
 * 快速生成招标项目DTO
 *
 * @author ruoyi
 * @date 2026-02-27
 */
@Data
public class QuickGenerateDto {

    /**
     * 是否执行AI分析
     */
    private Boolean enableAiAnalysis = false;

    /**
     * 是否提取评分标准
     */
    private Boolean enableExtractScoringCriteria = false;

    /**
     * 是否执行契合度分析
     */
    private Boolean analyzeMatchDegree = false;

    /**
     * 自定义AI分析提示词（可选）
     */
    private String aiPrompt;

    /**
     * 自定义评分标准提取提示词（可选）
     */
    private String scoringPrompt;

    /**
     * 自定义契合度分析提示词（可选）
     */
    private String matchAnalysisPrompt;

}
