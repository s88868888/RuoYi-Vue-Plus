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
import org.dromara.resource.domain.BizCompetitor;
import org.dromara.resource.domain.BizBidSubmission;
import org.dromara.resource.mapper.BizBidProjectMapper;
import org.dromara.resource.mapper.BizCompanyInfoMapper;
import org.dromara.resource.mapper.BizCompetitorMapper;
import org.dromara.resource.mapper.BizBidSubmissionMapper;
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
    private final BizCompetitorMapper competitorMapper;
    private final BizBidSubmissionMapper bidSubmissionMapper;
    private static final Pattern MATCH_SCORE_PATTERN = Pattern.compile("(?im)(?:score|总分|综合得分)\\s*[:：=]\\s*(\\d{1,3})");

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

    private static final String MATCH_ANALYSIS_PROMPT_STRICT = """
        你是专业的招投标契合度分析专家。请结合招标文件内容、招标项目信息以及候选公司资料，对每家公司做基于证据的逐项评分。

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

        【评分规则】
        1. 业务范围与项目类型匹配：0-25分
        2. 资质证书与合规条件匹配：0-20分
        3. 同类项目经验匹配：0-20分
        4. 团队与人员配置匹配：0-15分
        5. 区域履约与交付能力：0-10分
        6. 财务、信誉与综合实力：0-10分
        7. 总分 = 六项得分之和，范围必须为0-100分

        【强约束】
        1. 严禁沿用任何固定示例分数，尤其不要默认输出 85 分、72 分等模板分数
        2. 必须根据当前招标项目的具体要求评分，不同项目的类型、区域、资质门槛、预算、经验要求不同，总分也应体现差异
        3. 若某项缺少明确证据，按保守原则扣分，并说明“资料未体现”
        4. 不要输出“公司A/公司B/示例”这类占位词，必须使用真实公司名称
        5. 每家公司标题中必须包含 `score:分数` 格式，便于系统提取，例如 `（score:81）`

        【输出要求】
        请严格按以下 Markdown 结构输出：

        # 契合度分析报告

        ## 项目关键要求
        - 门槛条件：...
        - 关键加分项：...
        - 主要风险点：...

        ## 综合评分汇总
        | 公司名称 | 业务范围(25) | 资质证书(20) | 项目经验(20) | 团队人员(15) | 区域履约(10) | 财务信誉(10) | score:总分 | 推荐等级 |
        |---|---:|---:|---:|---:|---:|---:|---:|---|
        | 真实公司名称 | [0-25] | [0-20] | [0-20] | [0-15] | [0-10] | [0-10] | score:[0-100] | 高/中/低 |

        ## [真实公司名称]（score:[0-100]）
        ### 1. 评分依据
        - 结合招标文件与公司资料，说明主要命中点与扣分点

        ### 2. 优势分析
        - ...

        ### 3. 短板与风险
        - ...

        ### 4. 分项评分明细
        | 评估维度 | 分值上限 | 实得分 | 扣分原因/证据 |
        |---|---:|---:|---|
        | 业务范围与项目类型匹配 | 25 | [0-25] | ... |
        | 资质证书与合规条件匹配 | 20 | [0-20] | ... |
        | 同类项目经验匹配 | 20 | [0-20] | ... |
        | 团队与人员配置匹配 | 15 | [0-15] | ... |
        | 区域履约与交付能力 | 10 | [0-10] | ... |
        | 财务、信誉与综合实力 | 10 | [0-10] | ... |
        | 总分 | 100 | [0-100] | 六项加总 |

        ### 5. 投标建议
        - ...

        ## 最终建议
        - 对所有公司进行横向比较后，给出推荐顺序与理由
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
        String template = StrUtil.isNotBlank(customPrompt) ? customPrompt : MATCH_ANALYSIS_PROMPT_STRICT;
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

        // 匹配所有总分标记，兼容 score/总分/综合得分
        Matcher matcher = MATCH_SCORE_PATTERN.matcher(text);

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

        Matcher scoreMatcher = MATCH_SCORE_PATTERN.matcher(text);
        if (scoreMatcher.find()) {
            return normalizeScore(scoreMatcher.group(1));
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

    /**
     * 竞争对手分析提示词
     */
    private static final String COMPETITOR_ANALYSIS_PROMPT = """
        你是一位资深的招投标分析专家。请根据以下招标项目信息和竞争对手资料，对竞争态势进行深入分析。

        ## 招标项目信息
        - 项目名称：{projectName}
        - 招标单位：{bidOrg}
        - 项目类型：{projectType}
        - 预算金额：{budgetAmount}元
        - 项目区域：{projectRegion}
        - 招标方式：{bidMethod}
        - 项目描述：{projectDesc}

        ## 我方公司信息
        {companyInfoSection}

        ## 竞争对手信息
        {competitorSection}

        ## 分析要求
        请从以下维度进行分析，并给出竞争力总评分（score:XX 格式）：

        1. **竞争态势总览**：概述本项目竞争格局
        2. **逐一竞争对手分析**：每个竞争对手的优劣势、中标可能性
        3. **我方竞争力评估**：我方在本项目中的竞争优势和劣势
        4. **对比分析表**：维度包括资质、业绩、技术能力、价格、地域优势等
        5. **投标策略建议**：针对竞争对手特点的应对策略
        6. **风险提示**：主要竞争风险及应对措施

        请在报告开头给出总体竞争力评分：score:XX（0-100分）
        """;

    private static final String COMPETITOR_ANALYSIS_PROMPT_STRICT = """
        你是专业的招投标竞争对手分析专家。请结合招标项目、我方公司资料与竞争对手资料，输出一份基于证据的竞争态势分析报告。

        【招标项目信息】
        项目名称：{projectName}
        招标单位：{bidOrg}
        项目类型：{projectType}
        预算金额：{budgetAmount}元
        项目区域：{projectRegion}
        招标方式：{bidMethod}
        项目描述：{projectDesc}

        【我方公司信息】
        {companyInfoSection}

        【竞争对手信息】
        {competitorSection}

        【总体竞争力评分规则】
        1. 资质与合规竞争力：0-20分
        2. 同类项目业绩竞争力：0-20分
        3. 技术与方案竞争力：0-20分
        4. 团队与资源保障能力：0-15分
        5. 商务报价与成本竞争力：0-15分
        6. 区域履约与客户关系优势：0-10分
        7. 总分 = 六项加总，范围必须为0-100分

        【强约束】
        1. 报告全文只能出现一次 `score:` 标记，且必须放在开头的“总体竞争力评分”行
        2. 严禁默认输出固定模板分数，例如 85、88、90 等
        3. 分项评分明细中不要写 `score:`，只写纯数字
        4. 若缺少明确证据，按保守原则扣分，并说明“资料未体现”
        5. 结论必须体现当前项目特征，不同项目的总分应允许明显差异

        【输出结构】
        # 竞争对手分析报告

        ## 总体竞争力评分
        score:[0-100]

        ## 评分摘要
        | 维度 | 分值上限 | 实得分 | 评分依据 |
        |---|---:|---:|---|
        | 资质与合规竞争力 | 20 | [0-20] | ... |
        | 同类项目业绩竞争力 | 20 | [0-20] | ... |
        | 技术与方案竞争力 | 20 | [0-20] | ... |
        | 团队与资源保障能力 | 15 | [0-15] | ... |
        | 商务报价与成本竞争力 | 15 | [0-15] | ... |
        | 区域履约与客户关系优势 | 10 | [0-10] | ... |
        | 总分 | 100 | [0-100] | 六项加总 |

        ## 竞争态势总览
        - ...

        ## 逐一竞争对手分析
        - 分别分析每个竞争对手的优势、弱点、中标可能性

        ## 我方竞争力评估
        - 说明我方在本项目中的优势与短板

        ## 对比分析表
        - 从资质、业绩、技术、报价、区域优势等维度横向比较

        ## 投标策略建议
        - ...

        ## 风险提示
        - ...
        """;

    @Override
    public String analyzeCompetitors(Long submissionId, String prompt) {
        BizBidSubmission submission = bidSubmissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new RuntimeException("投标项目不存在");
        }

        // 获取关联的招标项目（用于附件和详细信息）
        BizBidProject project = null;
        if (submission.getBidProjectId() != null) {
            project = bidProjectMapper.selectById(submission.getBidProjectId());
        }

        String tenantId = submission.getTenantId();

        try {
            // 1. 构建己方公司信息
            String companyInfoSection;
            if (project != null) {
                companyInfoSection = buildCompanyInfoSection(tenantId, project);
            } else {
                companyInfoSection = "（暂无我方公司详细信息）";
            }

            // 2. 构建竞争对手信息段落
            String competitorSection = buildCompetitorSection(tenantId);

            // 3. 构建提示词
            String template = StrUtil.isNotBlank(prompt) ? prompt : COMPETITOR_ANALYSIS_PROMPT_STRICT;
            String finalPrompt = template
                .replace("{projectName}", StrUtil.nullToEmpty(submission.getProjectName()))
                .replace("{bidOrg}", StrUtil.nullToEmpty(submission.getBidOrg()))
                .replace("{projectType}", StrUtil.nullToEmpty(submission.getProjectType()))
                .replace("{budgetAmount}", submission.getBudgetAmount() != null ? submission.getBudgetAmount().toString() : "未知")
                .replace("{projectRegion}", StrUtil.nullToEmpty(submission.getProjectRegion()))
                .replace("{bidMethod}", StrUtil.nullToEmpty(submission.getBidMethod()))
                .replace("{projectDesc}", StrUtil.nullToEmpty(submission.getProjectDesc()))
                .replace("{companyInfoSection}", companyInfoSection)
                .replace("{competitorSection}", competitorSection);

            // 4. 调用AI分析（如有招标文件附件则携带）
            String attachments = project != null ? project.getAttachments() : null;
            String result = chatWithAttachments(attachments, finalPrompt);

            // 5. 解析竞争力评分
            Integer score = parseMatchScore(result);

            // 6. 更新投标项目
            LambdaUpdateWrapper<BizBidSubmission> wrapper = new LambdaUpdateWrapper<BizBidSubmission>()
                .eq(BizBidSubmission::getId, submissionId)
                .set(BizBidSubmission::getCompetitorAnalysisResult, result)
                .set(BizBidSubmission::getCompetitorAnalysisStatus, "completed");
            if (score != null) {
                wrapper.set(BizBidSubmission::getCompetitorScore, score);
            }
            bidSubmissionMapper.update(wrapper);

            return result;
        } catch (Exception e) {
            log.error("竞争对手分析失败, submissionId={}", submissionId, e);
            bidSubmissionMapper.update(new LambdaUpdateWrapper<BizBidSubmission>()
                .eq(BizBidSubmission::getId, submissionId)
                .set(BizBidSubmission::getCompetitorAnalysisStatus, "failed")
                .set(BizBidSubmission::getCompetitorAnalysisResult, "分析失败：" + e.getMessage()));
            throw new RuntimeException("竞争对手分析失败：" + e.getMessage());
        }
    }

    @Async
    @Override
    public void analyzeCompetitorsAsync(Long submissionId, String prompt) {
        TenantHelper.ignore(() -> {
            BizBidSubmission submission = bidSubmissionMapper.selectById(submissionId);
            if (submission == null) {
                log.error("竞争对手分析：投标项目不存在，submissionId={}", submissionId);
                return;
            }
            bidSubmissionMapper.update(new LambdaUpdateWrapper<BizBidSubmission>()
                .eq(BizBidSubmission::getId, submissionId)
                .set(BizBidSubmission::getCompetitorAnalysisStatus, "analyzing"));
            try {
                analyzeCompetitors(submissionId, prompt);
                log.info("投标项目{}竞争对手分析完成", submissionId);
            } catch (Exception e) {
                log.error("异步竞争对手分析失败, submissionId={}", submissionId, e);
                try {
                    bidSubmissionMapper.update(new LambdaUpdateWrapper<BizBidSubmission>()
                        .eq(BizBidSubmission::getId, submissionId)
                        .set(BizBidSubmission::getCompetitorAnalysisStatus, "failed")
                        .set(BizBidSubmission::getCompetitorAnalysisResult, "分析失败：" + e.getMessage()));
                } catch (Exception ex) {
                    log.error("兜底更新分析状态失败, submissionId={}", submissionId, ex);
                }
            }
        });
    }

    /**
     * 构建竞争对手信息段落
     */
    private String buildCompetitorSection(String tenantId) {
        List<BizCompetitor> competitors = competitorMapper.selectList(
            new LambdaQueryWrapper<BizCompetitor>()
                .eq(BizCompetitor::getTenantId, tenantId));

        if (competitors.isEmpty()) {
            return "（当前租户下暂无竞争对手信息）";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < competitors.size(); i++) {
            BizCompetitor comp = competitors.get(i);
            sb.append("### 竞争对手").append(i + 1).append("：").append(StrUtil.nullToEmpty(comp.getCompanyName())).append("\n\n");

            if (StrUtil.isNotBlank(comp.getCompanyType())) {
                sb.append("- 公司类型：").append(comp.getCompanyType()).append("\n");
            }
            if (StrUtil.isNotBlank(comp.getBusinessScope())) {
                sb.append("- 主营业务：").append(comp.getBusinessScope()).append("\n");
            }
            if (StrUtil.isNotBlank(comp.getRegisteredCapital())) {
                sb.append("- 注册资本：").append(comp.getRegisteredCapital()).append("万元\n");
            }
            if (StrUtil.isNotBlank(comp.getFoundedYear())) {
                sb.append("- 成立年份：").append(comp.getFoundedYear()).append("\n");
            }
            if (StrUtil.isNotBlank(comp.getProvince()) || StrUtil.isNotBlank(comp.getCity())) {
                sb.append("- 所在地区：").append(StrUtil.nullToEmpty(comp.getProvince())).append(" ").append(StrUtil.nullToEmpty(comp.getCity())).append("\n");
            }
            if (StrUtil.isNotBlank(comp.getMainProducts())) {
                sb.append("- 主要产品/服务：").append(comp.getMainProducts()).append("\n");
            }
            if (StrUtil.isNotBlank(comp.getStrengths())) {
                sb.append("- 竞争优势：").append(comp.getStrengths()).append("\n");
            }
            if (StrUtil.isNotBlank(comp.getWeaknesses())) {
                sb.append("- 竞争劣势：").append(comp.getWeaknesses()).append("\n");
            }
            if (StrUtil.isNotBlank(comp.getCompetitorLevel())) {
                sb.append("- 竞争级别：").append(comp.getCompetitorLevel()).append("\n");
            }

            sb.append("\n---\n\n");
        }

        return sb.toString();
    }

}
