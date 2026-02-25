package org.dromara.resource.service.impl;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.resource.domain.BizBidProject;
import org.dromara.resource.mapper.BizBidProjectMapper;
import org.dromara.resource.service.IAiAnalysisService;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * AI分析服务实现
 *
 * @author ruoyi
 * @date 2026-02-24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAnalysisServiceImpl implements IAiAnalysisService {

    private final ChatModel chatModel;
    private final BizBidProjectMapper bidProjectMapper;

    /**
     * 默认分析提示词
     */
    private static final String DEFAULT_PROMPT = """
            请分析以下招标项目信息，并提供详细的分析报告：

            项目名称：{projectName}
            招标单位：{bidOrg}
            项目类型：{projectType}
            预算金额：{budgetAmount}万元
            项目地区：{projectRegion}
            招标方式：{bidMethod}
            项目描述：{projectDesc}

            请从以下几个方面进行分析：
            1. 项目概况总结
            2. 项目规模和预算评估
            3. 技术要求分析
            4. 竞争态势预判
            5. 投标建议和注意事项
            6. 风险点提示

            请以结构化的方式输出分析结果。
            """;

    /**
     * 评分标准提取提示词
     */
    private static final String SCORING_CRITERIA_PROMPT = """
            请根据以下招标项目信息，生成详细的评分标准。

            项目名称：{projectName}
            招标单位：{bidOrg}
            项目类型：{projectType}
            预算金额：{budgetAmount}万元
            项目地区：{projectRegion}
            招标方式：{bidMethod}
            项目描述：{projectDesc}

            请以Markdown格式生成评分标准，包括以下内容：
            1. 项目基本信息
            2. 评分维度（通常包括技术方案、商务报价、企业资质、服务承诺等）
            3. 每个维度的评分标准和权重
            4. 评分等级说明
            5. 总体评分规则

            请确保输出格式清晰、结构完整、易于理解。
            """;

    @Override
    public String analyzeBidProject(Long projectId, String prompt) {
        BizBidProject project = bidProjectMapper.selectById(projectId);
        if (project == null) {
            throw new RuntimeException("项目不存在");
        }

        String finalPrompt = buildPrompt(project, prompt);

        try {
            String result = chatModel.call(finalPrompt);

            // 更新分析结果
            project.setAiAnalysisResult(result);
            project.setAiAnalysisStatus("completed");
            bidProjectMapper.updateById(project);

            return result;
        } catch (Exception e) {
            log.error("AI分析失败", e);
            project.setAiAnalysisStatus("failed");
            bidProjectMapper.updateById(project);
            throw new RuntimeException("AI分析失败：" + e.getMessage());
        }
    }

    @Async
    @Override
    public void analyzeBidProjectAsync(Long projectId, String prompt) {
        BizBidProject project = bidProjectMapper.selectById(projectId);
        if (project == null) {
            log.error("项目不存在：{}", projectId);
            return;
        }

        // 更新状态为分析中
        project.setAiAnalysisStatus("analyzing");
        bidProjectMapper.updateById(project);

        try {
            String result = analyzeBidProject(projectId, prompt);
            log.info("项目{}分析完成", projectId);
        } catch (Exception e) {
            log.error("异步分析失败", e);
        }
    }

    @Override
    public String extractScoringCriteria(Long projectId) {
        BizBidProject project = bidProjectMapper.selectById(projectId);
        if (project == null) {
            throw new RuntimeException("项目不存在");
        }

        String finalPrompt = buildScoringCriteriaPrompt(project);

        try {
            String result = chatModel.call(finalPrompt);

            // 更新评分标准结果
            project.setScoringCriteria(result);
            project.setScoringCriteriaStatus("completed");
            bidProjectMapper.updateById(project);

            return result;
        } catch (Exception e) {
            log.error("评分标准提取失败", e);
            project.setScoringCriteriaStatus("failed");
            bidProjectMapper.updateById(project);
            throw new RuntimeException("评分标准提取失败：" + e.getMessage());
        }
    }

    @Async
    @Override
    public void extractScoringCriteriaAsync(Long projectId) {
        BizBidProject project = bidProjectMapper.selectById(projectId);
        if (project == null) {
            log.error("项目不存在：{}", projectId);
            return;
        }

        // 更新状态为提取中
        project.setScoringCriteriaStatus("extracting");
        bidProjectMapper.updateById(project);

        try {
            extractScoringCriteria(projectId);
            log.info("项目{}评分标准提取完成", projectId);
        } catch (Exception e) {
            log.error("异步提取评分标准失败", e);
        }
    }

    /**
     * 构建提示词
     */
    private String buildPrompt(BizBidProject project, String customPrompt) {
        String template = StrUtil.isNotBlank(customPrompt) ? customPrompt : DEFAULT_PROMPT;

        return template
            .replace("{projectName}", StrUtil.nullToEmpty(project.getProjectName()))
            .replace("{bidOrg}", StrUtil.nullToEmpty(project.getBidOrg()))
            .replace("{projectType}", StrUtil.nullToEmpty(project.getProjectType()))
            .replace("{budgetAmount}", project.getBudgetAmount() != null ? project.getBudgetAmount().toString() : "未知")
            .replace("{projectRegion}", StrUtil.nullToEmpty(project.getProjectRegion()))
            .replace("{bidMethod}", StrUtil.nullToEmpty(project.getBidMethod()))
            .replace("{projectDesc}", StrUtil.nullToEmpty(project.getProjectDesc()));
    }

    /**
     * 构建评分标准提示词
     */
    private String buildScoringCriteriaPrompt(BizBidProject project) {
        return SCORING_CRITERIA_PROMPT
            .replace("{projectName}", StrUtil.nullToEmpty(project.getProjectName()))
            .replace("{bidOrg}", StrUtil.nullToEmpty(project.getBidOrg()))
            .replace("{projectType}", StrUtil.nullToEmpty(project.getProjectType()))
            .replace("{budgetAmount}", project.getBudgetAmount() != null ? project.getBudgetAmount().toString() : "未知")
            .replace("{projectRegion}", StrUtil.nullToEmpty(project.getProjectRegion()))
            .replace("{bidMethod}", StrUtil.nullToEmpty(project.getBidMethod()))
            .replace("{projectDesc}", StrUtil.nullToEmpty(project.getProjectDesc()));
    }

}
