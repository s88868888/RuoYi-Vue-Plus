package org.dromara.resource.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.ai.domain.VectorSearchResult;
import org.dromara.common.ai.service.AiChatService;
import org.dromara.common.ai.service.CompanyVectorService;
import org.dromara.common.oss.core.OssClient;
import org.dromara.common.oss.factory.OssFactory;
import org.dromara.resource.domain.BizBidProject;
import org.dromara.resource.domain.BizCompanyInfo;
import org.dromara.resource.mapper.BizBidProjectMapper;
import org.dromara.resource.mapper.BizCompanyInfoMapper;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.resource.service.BidDocumentVectorService;
import org.dromara.resource.service.IAiAnalysisService;
import org.dromara.system.domain.SysDept;
import org.dromara.system.domain.vo.SysOssVo;
import org.dromara.system.mapper.SysDeptMapper;
import org.dromara.system.service.ISysOssService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    private final AiChatService aiChatService;
    private final BizBidProjectMapper bidProjectMapper;
    private final ISysOssService ossService;
    private final CompanyVectorService companyVectorService;
    private final BizCompanyInfoMapper companyInfoMapper;
    private final SysDeptMapper sysDeptMapper;
    private final BidDocumentVectorService bidDocumentVectorService;

    /**
     * 默认分析提示词
     */
    private static final String DEFAULT_PROMPT = """
        请根据附件中的招标文件内容进行分析，并结合以下项目信息提供详细的分析报告：

        项目名称：{projectName}
        招标单位：{bidOrg}
        项目类型：{projectType}
        预算金额：{budgetAmount}元
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
        请根据附件中的招标文件提取评分标准，并结合以下项目信息生成详细的评分标准。

        项目名称：{projectName}
        招标单位：{bidOrg}
        项目类型：{projectType}
        预算金额：{budgetAmount}元
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

    /**
     * 契合度分析提示词（多公司对比分析）
     */
    private static final String MATCH_ANALYSIS_PROMPT = """
          你是专业的招投标分析专家。请根据附件中的招标文件，对以下各公司分别评估与该招标项目的契合度（0-100分）。

          【招标项目信息】
          项目名称：{projectName}
          招标单位：{bidOrg}
          项目类型：{projectType}
          预算金额：{budgetAmount}元
          项目地区：{projectRegion}
          招标方式：{bidMethod}
          项目描述：{projectDesc}

          【待评估公司信息】
          {companyInfoSection}

          【输出要求】
          请严格按照以下Markdown格式输出，每个公司一个章节：

          # 契合度分析报告

          ## 综合评估摘要

          | 公司名称 | 契合度评分 | 推荐等级 |
          |---------|-----------|---------|
          | 公司A | score:85 | ⭐⭐⭐⭐ |
          | 公司B | score:72 | ⭐⭐⭐ |

          ## 一、[公司A名称]（score:85）

          ### 1. 优势分析
          - ...

          ### 2. 劣势分析
          - ...

          ### 3. 契合度详细评分
          | 评估维度 | 评分 | 说明 |
          |---------|------|------|
          | 业务范围匹配 | 90 | ... |
          | 技术能力 | 85 | ... |
          | 资质证书 | 80 | ... |
          | 项目经验 | 75 | ... |
          | 人员配备 | 85 | ... |
          | 地域优势 | 90 | ... |

          ### 4. 投标建议
          - ...

          ## 二、[公司B名称]（score:72）
          ...（同上结构）

          ## 最终建议
          综合分析结论...

          重要要求：
          1. 每个公司标题括号中必须包含”score:分数”格式，如（score:85）
          2. 评分要客观公正，基于公司实际情况与招标要求的匹配度
          3. 优势劣势分析要具体，结合招标文件的具体要求
          4. 评估维度要全面，包括但不限于：业务范围、技术能力、资质证书、项目经验、人员配备、地域优势、财务实力
        """;

    @Override
    public String analyzeBidProject(Long projectId, String prompt) {
        BizBidProject project = bidProjectMapper.selectById(projectId);
        if (project == null) {
            throw new RuntimeException("项目不存在");
        }

        String finalPrompt = buildPrompt(project, prompt);

        try {
            String result = chatWithAttachments(project.getAttachments(), finalPrompt);

            // 定向更新，避免并发覆盖
            bidProjectMapper.update(new LambdaUpdateWrapper<BizBidProject>()
                .eq(BizBidProject::getId, projectId)
                .set(BizBidProject::getAiAnalysisResult, result)
                .set(BizBidProject::getAiAnalysisStatus, "completed"));

            // 同步存入Milvus
            try {
                String tenantId = project.getTenantId();
                bidDocumentVectorService.clearProjectDataByType(tenantId, projectId, "ai_analysis");
                bidDocumentVectorService.indexAiAnalysis(tenantId, projectId, result);
            } catch (Exception ex) {
                log.warn("AI分析结果同步Milvus失败，不影响主流程: {}", ex.getMessage());
            }

            return result;
        } catch (Exception e) {
            log.error("AI分析失败", e);
            bidProjectMapper.update(new LambdaUpdateWrapper<BizBidProject>()
                .eq(BizBidProject::getId, projectId)
                .set(BizBidProject::getAiAnalysisStatus, "failed"));
            throw new RuntimeException("AI分析失败：" + e.getMessage());
        }
    }

    @Async
    @Override
    public void analyzeBidProjectAsync(Long projectId, String prompt) {
        TenantHelper.ignore(() -> {
            BizBidProject project = bidProjectMapper.selectById(projectId);
            if (project == null) {
                log.error("项目不存在：{}", projectId);
                return;
            }
            bidProjectMapper.update(new LambdaUpdateWrapper<BizBidProject>()
                .eq(BizBidProject::getId, projectId)
                .set(BizBidProject::getAiAnalysisStatus, "analyzing"));
            try {
                analyzeBidProject(projectId, prompt);
                log.info("项目{}分析完成", projectId);
            } catch (Exception e) {
                log.error("异步分析失败", e);
            }
        });
    }

    @Override
    public String extractScoringCriteria(Long projectId, String prompt) {
        BizBidProject project = bidProjectMapper.selectById(projectId);
        if (project == null) {
            throw new RuntimeException("项目不存在");
        }

        String finalPrompt = buildScoringCriteriaPrompt(project, prompt);

        try {
            String result = chatWithAttachments(project.getAttachments(), finalPrompt);

            // 定向更新，避免并发覆盖
            bidProjectMapper.update(new LambdaUpdateWrapper<BizBidProject>()
                .eq(BizBidProject::getId, projectId)
                .set(BizBidProject::getScoringCriteria, result)
                .set(BizBidProject::getScoringCriteriaStatus, "completed"));

            // 同步存入Milvus
            try {
                String tenantId = project.getTenantId();
                bidDocumentVectorService.clearProjectDataByType(tenantId, projectId, "scoring");
                bidDocumentVectorService.indexScoringCriteria(tenantId, projectId, result);
            } catch (Exception ex) {
                log.warn("评分标准同步Milvus失败，不影响主流程: {}", ex.getMessage());
            }

            return result;
        } catch (Exception e) {
            log.error("评分标准提取失败", e);
            bidProjectMapper.update(new LambdaUpdateWrapper<BizBidProject>()
                .eq(BizBidProject::getId, projectId)
                .set(BizBidProject::getScoringCriteriaStatus, "failed"));
            throw new RuntimeException("评分标准提取失败：" + e.getMessage());
        }
    }

    @Async
    @Override
    public void extractScoringCriteriaAsync(Long projectId, String prompt) {
        TenantHelper.ignore(() -> {
            BizBidProject project = bidProjectMapper.selectById(projectId);
            if (project == null) {
                log.error("项目不存在：{}", projectId);
                return;
            }
            bidProjectMapper.update(new LambdaUpdateWrapper<BizBidProject>()
                .eq(BizBidProject::getId, projectId)
                .set(BizBidProject::getScoringCriteriaStatus, "extracting"));
            try {
                extractScoringCriteria(projectId, prompt);
                log.info("项目{}评分标准提取完成", projectId);
            } catch (Exception e) {
                log.error("异步提取评分标准失败", e);
            }
        });
    }

    @Override
    public String analyzeMatchDegree(Long projectId, String prompt) {
        BizBidProject project = bidProjectMapper.selectById(projectId);
        if (project == null) {
            throw new RuntimeException("项目不存在");
        }

        try {
            // 1. 获取租户下所有公司信息
            String tenantId = project.getTenantId();
            String companyInfoSection = buildCompanyInfoSection(tenantId, project);

            // 2. 构建契合度分析提示词
            String finalPrompt = buildMatchAnalysisPrompt(project, prompt)
                .replace("{companyInfoSection}", companyInfoSection);

            // 3. 调用AI分析
            String result = chatWithAttachments(project.getAttachments(), finalPrompt);

            // 4. 解析各公司分数，取最高分
            Integer bestScore = parseBestMatchScore(result);

            // 5. 定向更新
            LambdaUpdateWrapper<BizBidProject> wrapper = new LambdaUpdateWrapper<BizBidProject>()
                .eq(BizBidProject::getId, projectId)
                .set(BizBidProject::getMatchAnalysisResult, result)
                .set(BizBidProject::getMatchAnalysisStatus, "completed");
            if (bestScore != null) {
                wrapper.set(BizBidProject::getMatchDegree, bestScore);
            }
            bidProjectMapper.update(wrapper);

            // 6. 同步存入Milvus
            try {
                bidDocumentVectorService.clearProjectDataByType(tenantId, projectId, "match_analysis");
                bidDocumentVectorService.indexMatchAnalysis(tenantId, projectId, result);
            } catch (Exception ex) {
                log.warn("契合度分析同步Milvus失败，不影响主流程: {}", ex.getMessage());
            }

            return result;
        } catch (Exception e) {
            log.error("契合度分析失败", e);
            bidProjectMapper.update(new LambdaUpdateWrapper<BizBidProject>()
                .eq(BizBidProject::getId, projectId)
                .set(BizBidProject::getMatchAnalysisStatus, "failed"));
            throw new RuntimeException("契合度分析失败：" + e.getMessage());
        }
    }

    @Async
    @Override
    public void analyzeMatchDegreeAsync(Long projectId, String prompt) {
        TenantHelper.ignore(() -> {
            BizBidProject project = bidProjectMapper.selectById(projectId);
            if (project == null) {
                log.error("项目不存在：{}", projectId);
                return;
            }
            bidProjectMapper.update(new LambdaUpdateWrapper<BizBidProject>()
                .eq(BizBidProject::getId, projectId)
                .set(BizBidProject::getMatchAnalysisStatus, "processing"));
            try {
                analyzeMatchDegree(projectId, prompt);
                log.info("项目{}契合度分析完成", projectId);
            } catch (Exception e) {
                log.error("异步契合度分析失败", e);
            }
        });
    }

    /**
     * 通过 ossId 下载文件并调用 AI 分析，无附件时直接文本对话
     */
    private String chatWithAttachments(String attachments, String prompt) {
        if (StrUtil.isNotBlank(attachments)) {
            String firstId = attachments.split(",")[0].trim();
            SysOssVo ossVo = ossService.getById(Long.parseLong(firstId));
            if (ossVo != null) {
                OssClient storage = OssFactory.instance(ossVo.getService());
                Path tempFile = storage.fileDownload(ossVo.getFileName());
                Resource resource = new FileSystemResource(tempFile.toFile());
                return aiChatService.chatWithDocument(resource, prompt);
            }
        }
        return aiChatService.chat(prompt);
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
    private String buildScoringCriteriaPrompt(BizBidProject project, String customPrompt) {
        String template = StrUtil.isNotBlank(customPrompt) ? customPrompt : SCORING_CRITERIA_PROMPT;
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
     * 构建契合度分析提示词
     */
    private String buildMatchAnalysisPrompt(BizBidProject project, String customPrompt) {
        String template = StrUtil.isNotBlank(customPrompt) ? customPrompt : MATCH_ANALYSIS_PROMPT;
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
     * 构建租户下所有公司信息段落（DB + Milvus向量检索）
     */
    private String buildCompanyInfoSection(String tenantId, BizBidProject project) {
        // 1. 查询租户下所有公司（通过biz_company_info的tenant_id）
        List<BizCompanyInfo> companies = companyInfoMapper.selectList(
            new LambdaQueryWrapper<BizCompanyInfo>()
                .eq(BizCompanyInfo::getTenantId, tenantId));

        if (companies.isEmpty()) {
            return "（当前租户下暂无公司信息）";
        }

        // 构建项目关键词用于向量检索
        String projectKeywords = String.join(" ",
            StrUtil.nullToEmpty(project.getProjectName()),
            StrUtil.nullToEmpty(project.getProjectType()),
            StrUtil.nullToEmpty(project.getProjectDesc()));

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < companies.size(); i++) {
            BizCompanyInfo company = companies.get(i);

            // 获取公司对应的部门名称作为公司全称
            String companyFullName = getCompanyFullName(company);

            sb.append("### 公司").append(i + 1).append("：").append(companyFullName).append("\n\n");

            // DB基本信息
            sb.append("**基本信息：**\n");
            if (StrUtil.isNotBlank(company.getEnterpriseAbbr())) {
                sb.append("- 企业简称：").append(company.getEnterpriseAbbr()).append("\n");
            }
            if (StrUtil.isNotBlank(company.getUnifiedCreditCode())) {
                sb.append("- 统一社会信用代码：").append(company.getUnifiedCreditCode()).append("\n");
            }
            if (StrUtil.isNotBlank(company.getLegalPerson())) {
                sb.append("- 法定代表人：").append(company.getLegalPerson()).append("\n");
            }
            if (StrUtil.isNotBlank(company.getRegisteredCapital())) {
                sb.append("- 注册资本：").append(company.getRegisteredCapital()).append("万元\n");
            }
            if (StrUtil.isNotBlank(company.getEnterpriseScale())) {
                sb.append("- 企业规模：").append(company.getEnterpriseScale()).append("\n");
            }
            if (StrUtil.isNotBlank(company.getIndustryCategory())) {
                sb.append("- 行业类别：").append(company.getIndustryCategory()).append("\n");
            }
            if (StrUtil.isNotBlank(company.getCompanyAddress())) {
                sb.append("- 公司地址：").append(company.getCompanyAddress()).append("\n");
            }
            if (StrUtil.isNotBlank(company.getBusinessScope())) {
                // 经营范围可能很长，截取前500字
                String scope = company.getBusinessScope();
                if (scope.length() > 500) {
                    scope = scope.substring(0, 500) + "...";
                }
                sb.append("- 经营范围：").append(scope).append("\n");
            }
            if (company.getTotalEmployees() != null) {
                sb.append("- 员工总数：").append(company.getTotalEmployees()).append("人\n");
            }
            if (StrUtil.isNotBlank(company.getManagementCertification())) {
                sb.append("- 管理体系认证：").append(company.getManagementCertification()).append("\n");
            }
            if (StrUtil.isNotBlank(company.getEnterpriseRegion())) {
                sb.append("- 企业所在地区：").append(company.getEnterpriseRegion()).append("\n");
            }

            // Milvus向量检索：获取与项目相关的公司资质、业绩、人员等
            sb.append("\n**向量库检索到的相关信息：**\n");
            try {
                List<VectorSearchResult> vectorResults = companyVectorService.searchCompanyAllData(
                    tenantId, company.getDeptId(), projectKeywords, 10);

                if (!vectorResults.isEmpty()) {
                    // 按文档类型分组展示
                    Map<String, List<VectorSearchResult>> grouped = vectorResults.stream()
                        .collect(Collectors.groupingBy(r -> StrUtil.nullToEmpty(r.getDocType())));

                    for (Map.Entry<String, List<VectorSearchResult>> entry : grouped.entrySet()) {
                        String docTypeLabel = getDocTypeLabel(entry.getKey());
                        sb.append("\n【").append(docTypeLabel).append("】\n");
                        for (VectorSearchResult vr : entry.getValue()) {
                            if (StrUtil.isNotBlank(vr.getContent())) {
                                sb.append("- ").append(vr.getContent()).append("\n");
                            }
                        }
                    }
                } else {
                    sb.append("- （向量库暂无该公司的详细数据）\n");
                }
            } catch (Exception e) {
                log.warn("Milvus检索公司{}数据失败: {}", company.getDeptId(), e.getMessage());
                sb.append("- （向量库检索异常，仅使用基本信息进行分析）\n");
            }

            sb.append("\n---\n\n");
        }

        return sb.toString();
    }

    /**
     * 获取公司全称（通过dept_id查询部门名称）
     */
    private String getCompanyFullName(BizCompanyInfo company) {
        if (company.getDeptId() != null) {
            try {
                SysDept dept = sysDeptMapper.selectById(company.getDeptId());
                if (dept != null && StrUtil.isNotBlank(dept.getDeptName())) {
                    return dept.getDeptName();
                }
            } catch (Exception e) {
                log.warn("查询部门名称失败: {}", e.getMessage());
            }
        }
        // 回退使用企业简称
        return StrUtil.isNotBlank(company.getEnterpriseAbbr()) ? company.getEnterpriseAbbr() : "未知公司";
    }

    /**
     * 文档类型中文标签
     */
    private String getDocTypeLabel(String docType) {
        return switch (docType) {
            case "company_info" -> "公司信息";
            case "personnel_info" -> "人员信息";
            case "product_info" -> "产品信息";
            case "qualification" -> "资质证书";
            case "performance" -> "业绩案例";
            case "patent" -> "专利/荣誉";
            case "financial" -> "财务信息";
            case "project" -> "项目知识";
            default -> docType;
        };
    }

    /**
     * 从AI返回结果中解析所有公司的分数，取最高分
     */
    private Integer parseBestMatchScore(String text) {
        if (StrUtil.isBlank(text)) {
            return null;
        }

        // 匹配所有 score:XX 格式（标题中、表格中）
        Pattern scorePattern = Pattern.compile("score\\s*[:：]\\s*(\\d{1,3})");
        Matcher matcher = scorePattern.matcher(text);

        Integer bestScore = null;
        while (matcher.find()) {
            Integer score = normalizeScore(matcher.group(1));
            if (score != null && (bestScore == null || score > bestScore)) {
                bestScore = score;
            }
        }

        if (bestScore != null) {
            log.info("解析到最高契合度: {}", bestScore);
        }
        return bestScore;
    }

    /**
     * 从AI返回文本中提取0-100分数（兼容旧格式）
     */
    private Integer parseMatchScore(String text) {
        if (StrUtil.isBlank(text)) {
            return null;
        }

        Pattern scorePattern = Pattern.compile("(?i)score\\s*[:：]\\s*(\\d{1,3})");
        Matcher scoreMatcher = scorePattern.matcher(text);
        if (scoreMatcher.find()) {
            return normalizeScore(scoreMatcher.group(1));
        }

        Pattern firstNumberPattern = Pattern.compile("\\b(\\d{1,3})\\b");
        Matcher firstNumberMatcher = firstNumberPattern.matcher(text);
        if (firstNumberMatcher.find()) {
            return normalizeScore(firstNumberMatcher.group(1));
        }
        return null;
    }

    private Integer normalizeScore(String scoreText) {
        try {
            int score = Integer.parseInt(scoreText);
            if (score < 0) {
                return 0;
            }
            if (score > 100) {
                return 100;
            }
            return score;
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
