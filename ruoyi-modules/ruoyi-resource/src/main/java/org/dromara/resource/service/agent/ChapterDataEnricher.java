package org.dromara.resource.service.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.resource.domain.*;
import org.dromara.resource.mapper.*;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 章节数据富化器
 * 根据章节标题关键词检测内容类型，从DB查询真实结构化数据，
 * 格式化为Markdown表格注入AI生成prompt，并收集附件图片引用。
 *
 * @author ruoyi
 * @date 2026-03-06
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChapterDataEnricher {

    private final BizQualificationMapper qualificationMapper;
    private final BizPersonnelMapper personnelMapper;
    private final BizPersonnelCertificateMapper certificateMapper;
    private final BizPersonnelProjectMapper personnelProjectMapper;
    private final BizPerformanceMapper performanceMapper;
    private final BizPatentMedalMapper patentMedalMapper;
    private final BizFinanceInfoMapper financeInfoMapper;
    private final BizProductMapper productMapper;
    private final BizCompanyInfoMapper companyInfoMapper;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ==================== 枚举 & 数据类 ====================

    /**
     * 章节内容类型枚举
     */
    public enum ChapterContentType {
        QUALIFICATION,    // 资质证书
        PERFORMANCE,      // 业绩案例
        PERSONNEL,        // 人员配备
        PATENT,           // 专利/知识产权
        FINANCE,          // 财务信息
        PRODUCT,          // 设备/产品
        COMPANY_PROFILE,  // 公司概况
        NONE              // 非规定格式
    }

    /**
     * 数据富化结果
     */
    @Data
    public static class EnrichmentResult {
        private ChapterContentType contentType;
        /** Markdown 表格，注入 prompt */
        private String structuredDataMarkdown;
        /** 格式化生成指令 */
        private String formatInstructions;
        /** 待追加的附件图片 */
        private List<AttachmentRef> attachments;
        /** 查询到的记录数 */
        private int recordCount;

        public static EnrichmentResult empty() {
            EnrichmentResult result = new EnrichmentResult();
            result.setContentType(ChapterContentType.NONE);
            result.setStructuredDataMarkdown(null);
            result.setFormatInstructions(null);
            result.setAttachments(Collections.emptyList());
            result.setRecordCount(0);
            return result;
        }
    }

    /**
     * 附件引用
     */
    @Data
    public static class AttachmentRef {
        /** 类型：QUALIFICATION/PERSONNEL/... */
        private String type;
        /** 图片标题 */
        private String caption;
        /** OSS ID 或 URL */
        private String imageUrl;

        public AttachmentRef(String type, String caption, String imageUrl) {
            this.type = type;
            this.caption = caption;
            this.imageUrl = imageUrl;
        }
    }

    // ==================== 关键词配置 ====================

    private static final Map<ChapterContentType, List<String>> KEYWORD_MAP = new LinkedHashMap<>();

    static {
        KEYWORD_MAP.put(ChapterContentType.QUALIFICATION,
            List.of("资质", "证书", "认证", "许可证", "资格", "体系认证"));
        KEYWORD_MAP.put(ChapterContentType.PERFORMANCE,
            List.of("业绩", "案例", "工程经验", "项目经验", "类似业绩", "同类业绩"));
        KEYWORD_MAP.put(ChapterContentType.PERSONNEL,
            List.of("人员配置", "拟投入", "项目团队", "人员安排", "技术人员", "管理人员", "岗位人员"));
        KEYWORD_MAP.put(ChapterContentType.PATENT,
            List.of("专利", "知识产权", "发明", "实用新型", "科技成果", "奖项"));
        KEYWORD_MAP.put(ChapterContentType.FINANCE,
            List.of("财务", "审计", "财报", "资产负债", "财务报告"));
        KEYWORD_MAP.put(ChapterContentType.PRODUCT,
            List.of("设备", "产品", "仪器", "设备清单", "投入设备"));
        KEYWORD_MAP.put(ChapterContentType.COMPANY_PROFILE,
            List.of("公司概况", "公司简介", "企业概况", "投标人基本情况"));
    }

    // ==================== 核心入口 ====================

    /**
     * 对章节进行数据富化
     *
     * @param chapter   章节信息
     * @param companyId 公司/部门ID
     * @return 富化结果
     */
    public EnrichmentResult enrich(BizSubmissionChapter chapter, Long companyId) {
        if (companyId == null) {
            log.debug("companyId为null，跳过数据富化");
            return EnrichmentResult.empty();
        }

        ChapterContentType contentType = detectContentType(chapter);
        if (contentType == ChapterContentType.NONE) {
            return EnrichmentResult.empty();
        }

        log.info("章节[{}]检测为规定格式类型: {}", chapter.getChapterTitle(), contentType);

        try {
            return switch (contentType) {
                case QUALIFICATION -> enrichQualification(companyId);
                case PERFORMANCE -> enrichPerformance(companyId);
                case PERSONNEL -> enrichPersonnel(companyId);
                case PATENT -> enrichPatent(companyId);
                case FINANCE -> enrichFinance(companyId);
                case PRODUCT -> enrichProduct(companyId);
                case COMPANY_PROFILE -> enrichCompanyProfile(companyId);
                default -> EnrichmentResult.empty();
            };
        } catch (Exception e) {
            log.error("数据富化失败: chapter={}, type={}", chapter.getChapterTitle(), contentType, e);
            return EnrichmentResult.empty();
        }
    }

    // ==================== 内容类型检测 ====================

    /**
     * 根据章节标题+备注中的关键词检测内容类型
     */
    ChapterContentType detectContentType(BizSubmissionChapter chapter) {
        String text = (chapter.getChapterTitle() != null ? chapter.getChapterTitle() : "")
            + " " + (chapter.getRemark() != null ? chapter.getRemark() : "");

        ChapterContentType bestType = ChapterContentType.NONE;
        int bestScore = 0;

        for (Map.Entry<ChapterContentType, List<String>> entry : KEYWORD_MAP.entrySet()) {
            int score = 0;
            for (String keyword : entry.getValue()) {
                if (text.contains(keyword)) {
                    score++;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestType = entry.getKey();
            }
        }

        return bestScore >= 1 ? bestType : ChapterContentType.NONE;
    }

    // ==================== 各类型数据查询与格式化 ====================

    /**
     * 资质证书数据富化
     */
    private EnrichmentResult enrichQualification(Long companyId) {
        List<BizQualification> list = qualificationMapper.selectList(
            new LambdaQueryWrapper<BizQualification>()
                .eq(BizQualification::getDeptId, companyId)
                .eq(BizQualification::getCertStatus, "有效")
                .last("LIMIT 20")
        );

        if (list.isEmpty()) {
            log.info("资质证书数据为空，companyId={}", companyId);
            return EnrichmentResult.empty();
        }

        StringBuilder md = new StringBuilder();
        md.append("以下为公司实际持有的资质证书数据（共").append(list.size()).append("条）：\n\n");
        md.append("| 序号 | 证书名称 | 证书类别 | 证书编号 | 发证机关 | 有效期 |\n");
        md.append("|------|----------|----------|----------|----------|--------|\n");

        List<AttachmentRef> attachments = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            BizQualification q = list.get(i);
            md.append(String.format("| %d | %s | %s | %s | %s | %s |\n",
                i + 1,
                safe(q.getCertName()),
                safe(q.getCertCategory()),
                safe(q.getCertNumber()),
                safe(q.getIssuingAuthority()),
                formatDateRange(q.getValidStartDate(), q.getValidEndDate())
            ));
            collectImages(attachments, "QUALIFICATION", q.getCertName(), q.getCertImages());
        }

        EnrichmentResult result = new EnrichmentResult();
        result.setContentType(ChapterContentType.QUALIFICATION);
        result.setStructuredDataMarkdown(truncate(md.toString(), 8000));
        result.setFormatInstructions(INSTRUCTION_QUALIFICATION);
        result.setAttachments(attachments);
        result.setRecordCount(list.size());
        return result;
    }

    /**
     * 业绩案例数据富化
     */
    private EnrichmentResult enrichPerformance(Long companyId) {
        List<BizPerformance> list = performanceMapper.selectList(
            new LambdaQueryWrapper<BizPerformance>()
                .eq(BizPerformance::getDeptId, companyId)
                .orderByDesc(BizPerformance::getSigningDate)
                .last("LIMIT 15")
        );

        if (list.isEmpty()) {
            log.info("业绩案例数据为空，companyId={}", companyId);
            return EnrichmentResult.empty();
        }

        StringBuilder md = new StringBuilder();
        md.append("以下为公司实际业绩案例数据（共").append(list.size()).append("条）：\n\n");
        md.append("| 序号 | 项目名称 | 业主单位 | 合同金额(万元) | 签约日期 | 竣工日期 |\n");
        md.append("|------|----------|----------|----------------|----------|----------|\n");

        List<AttachmentRef> attachments = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            BizPerformance p = list.get(i);
            String amount = p.getContractAmount() != null
                ? p.getContractAmount().movePointLeft(4).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
                : "-";
            md.append(String.format("| %d | %s | %s | %s | %s | %s |\n",
                i + 1,
                safe(p.getName()),
                safe(p.getOwnerUnitName()),
                amount,
                formatDate(p.getSigningDate()),
                formatDate(p.getCompletionDate())
            ));
            collectImages(attachments, "PERFORMANCE", p.getName() + " - 合同", p.getContractImages());
            collectImages(attachments, "PERFORMANCE", p.getName() + " - 中标通知书", p.getBidNoticeAttachment());
            collectImages(attachments, "PERFORMANCE", p.getName() + " - 验收报告", p.getAcceptanceAttachment());
        }

        EnrichmentResult result = new EnrichmentResult();
        result.setContentType(ChapterContentType.PERFORMANCE);
        result.setStructuredDataMarkdown(truncate(md.toString(), 8000));
        result.setFormatInstructions(INSTRUCTION_PERFORMANCE);
        result.setAttachments(attachments);
        result.setRecordCount(list.size());
        return result;
    }

    /**
     * 人员配备数据富化
     */
    private EnrichmentResult enrichPersonnel(Long companyId) {
        List<BizPersonnel> list = personnelMapper.selectList(
            new LambdaQueryWrapper<BizPersonnel>()
                .eq(BizPersonnel::getDeptId, companyId)
                .eq(BizPersonnel::getStatus, "0") // 在职
                .last("LIMIT 15")
        );

        if (list.isEmpty()) {
            log.info("人员数据为空，companyId={}", companyId);
            return EnrichmentResult.empty();
        }

        // 批量查询证书和项目经验
        List<Long> personnelIds = list.stream().map(BizPersonnel::getId).collect(Collectors.toList());

        List<BizPersonnelCertificate> allCerts = certificateMapper.selectList(
            new LambdaQueryWrapper<BizPersonnelCertificate>()
                .in(BizPersonnelCertificate::getPersonnelId, personnelIds)
        );
        Map<Long, List<BizPersonnelCertificate>> certMap = allCerts.stream()
            .collect(Collectors.groupingBy(BizPersonnelCertificate::getPersonnelId));

        List<BizPersonnelProject> allProjects = personnelProjectMapper.selectList(
            new LambdaQueryWrapper<BizPersonnelProject>()
                .in(BizPersonnelProject::getPersonnelId, personnelIds)
        );
        Map<Long, List<BizPersonnelProject>> projectMap = allProjects.stream()
            .collect(Collectors.groupingBy(BizPersonnelProject::getPersonnelId));

        StringBuilder md = new StringBuilder();
        md.append("以下为公司拟投入人员数据（共").append(list.size()).append("人）：\n\n");
        md.append("| 序号 | 姓名 | 职务 | 工作年限 | 持有证书 | 代表项目 |\n");
        md.append("|------|------|------|----------|----------|----------|\n");

        List<AttachmentRef> attachments = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            BizPersonnel p = list.get(i);
            // 证书名称列表
            List<BizPersonnelCertificate> certs = certMap.getOrDefault(p.getId(), Collections.emptyList());
            String certNames = certs.stream()
                .map(BizPersonnelCertificate::getCertificateName)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("、"));
            if (certNames.isEmpty()) certNames = "-";

            // 代表项目
            List<BizPersonnelProject> projects = projectMap.getOrDefault(p.getId(), Collections.emptyList());
            String projectNames = projects.stream()
                .map(BizPersonnelProject::getProjectName)
                .filter(Objects::nonNull)
                .limit(2)
                .collect(Collectors.joining("、"));
            if (projectNames.isEmpty()) projectNames = "-";

            md.append(String.format("| %d | %s | %s | %s | %s | %s |\n",
                i + 1,
                safe(p.getName()),
                safe(p.getPosition()),
                p.getWorkYears() != null ? p.getWorkYears() + "年" : "-",
                certNames,
                projectNames
            ));

            // 人员照片
            if (p.getPhoto() != null && !p.getPhoto().isBlank()) {
                attachments.add(new AttachmentRef("PERSONNEL", p.getName() + " - 照片", p.getPhoto()));
            }
            // 证书图片
            for (BizPersonnelCertificate cert : certs) {
                collectImages(attachments, "PERSONNEL",
                    p.getName() + " - " + safe(cert.getCertificateName()),
                    cert.getCertificateImage());
            }
        }

        EnrichmentResult result = new EnrichmentResult();
        result.setContentType(ChapterContentType.PERSONNEL);
        result.setStructuredDataMarkdown(truncate(md.toString(), 8000));
        result.setFormatInstructions(INSTRUCTION_PERSONNEL);
        result.setAttachments(attachments);
        result.setRecordCount(list.size());
        return result;
    }

    /**
     * 专利/知识产权数据富化
     */
    private EnrichmentResult enrichPatent(Long companyId) {
        List<BizPatentMedal> list = patentMedalMapper.selectList(
            new LambdaQueryWrapper<BizPatentMedal>()
                .eq(BizPatentMedal::getDeptId, companyId)
                .eq(BizPatentMedal::getStatus, "0") // 有效
                .last("LIMIT 20")
        );

        if (list.isEmpty()) {
            log.info("专利数据为空，companyId={}", companyId);
            return EnrichmentResult.empty();
        }

        StringBuilder md = new StringBuilder();
        md.append("以下为公司实际持有的专利/知识产权数据（共").append(list.size()).append("条）：\n\n");
        md.append("| 序号 | 专利名称 | 专利类型 | 专利号 | 授权日期 | 发明人 |\n");
        md.append("|------|----------|----------|--------|----------|--------|\n");

        List<AttachmentRef> attachments = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            BizPatentMedal p = list.get(i);
            String typeName = switch (safe(p.getPatentType())) {
                case "1" -> "发明专利";
                case "2" -> "实用新型";
                case "3" -> "外观设计";
                default -> safe(p.getPatentType());
            };
            md.append(String.format("| %d | %s | %s | %s | %s | %s |\n",
                i + 1,
                safe(p.getPatentName()),
                typeName,
                safe(p.getPatentNumber()),
                formatDate(p.getAuthorizationDate()),
                safe(p.getInventor())
            ));
            collectImages(attachments, "PATENT", p.getPatentName() + " - 专利", p.getPatentImage());
            collectImages(attachments, "PATENT", p.getPatentName() + " - 证书", p.getCertificateImage());
        }

        EnrichmentResult result = new EnrichmentResult();
        result.setContentType(ChapterContentType.PATENT);
        result.setStructuredDataMarkdown(truncate(md.toString(), 8000));
        result.setFormatInstructions(INSTRUCTION_PATENT);
        result.setAttachments(attachments);
        result.setRecordCount(list.size());
        return result;
    }

    /**
     * 财务信息数据富化
     */
    private EnrichmentResult enrichFinance(Long companyId) {
        List<BizFinanceInfo> list = financeInfoMapper.selectList(
            new LambdaQueryWrapper<BizFinanceInfo>()
                .eq(BizFinanceInfo::getDeptId, companyId)
                .orderByDesc(BizFinanceInfo::getFinanceDate)
                .last("LIMIT 10")
        );

        if (list.isEmpty()) {
            log.info("财务信息数据为空，companyId={}", companyId);
            return EnrichmentResult.empty();
        }

        StringBuilder md = new StringBuilder();
        md.append("以下为公司财务信息数据（共").append(list.size()).append("条）：\n\n");
        md.append("| 序号 | 名称 | 信息类型 | 日期 |\n");
        md.append("|------|------|----------|------|\n");

        List<AttachmentRef> attachments = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            BizFinanceInfo f = list.get(i);
            md.append(String.format("| %d | %s | %s | %s |\n",
                i + 1,
                safe(f.getFinanceName()),
                safe(f.getInfoType()),
                formatDate(f.getFinanceDate())
            ));
            collectImages(attachments, "FINANCE", safe(f.getFinanceName()), f.getAttachmentUrl());
        }

        EnrichmentResult result = new EnrichmentResult();
        result.setContentType(ChapterContentType.FINANCE);
        result.setStructuredDataMarkdown(truncate(md.toString(), 8000));
        result.setFormatInstructions(INSTRUCTION_FINANCE);
        result.setAttachments(attachments);
        result.setRecordCount(list.size());
        return result;
    }

    /**
     * 设备/产品数据富化
     */
    private EnrichmentResult enrichProduct(Long companyId) {
        List<BizProduct> list = productMapper.selectList(
            new LambdaQueryWrapper<BizProduct>()
                .eq(BizProduct::getDeptId, companyId)
                .last("LIMIT 15")
        );

        if (list.isEmpty()) {
            log.info("产品/设备数据为空，companyId={}", companyId);
            return EnrichmentResult.empty();
        }

        StringBuilder md = new StringBuilder();
        md.append("以下为公司拟投入的设备/产品数据（共").append(list.size()).append("条）：\n\n");
        md.append("| 序号 | 产品名称 | 型号 | 数量 | 性能说明 |\n");
        md.append("|------|----------|------|------|----------|\n");

        List<AttachmentRef> attachments = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            BizProduct p = list.get(i);
            md.append(String.format("| %d | %s | %s | %s | %s |\n",
                i + 1,
                safe(p.getProductName()),
                safe(p.getProductModel()),
                p.getQuantity() != null ? String.valueOf(p.getQuantity()) : "-",
                truncate(safe(p.getPerformanceDesc()), 50)
            ));
            collectImages(attachments, "PRODUCT", p.getProductName() + " - 实物", p.getProductImage());
            collectImages(attachments, "PRODUCT", p.getProductName() + " - 相关", p.getRelatedImages());
        }

        EnrichmentResult result = new EnrichmentResult();
        result.setContentType(ChapterContentType.PRODUCT);
        result.setStructuredDataMarkdown(truncate(md.toString(), 8000));
        result.setFormatInstructions(INSTRUCTION_PRODUCT);
        result.setAttachments(attachments);
        result.setRecordCount(list.size());
        return result;
    }

    /**
     * 公司概况数据富化
     */
    private EnrichmentResult enrichCompanyProfile(Long companyId) {
        BizCompanyInfo info = companyInfoMapper.selectOne(
            new LambdaQueryWrapper<BizCompanyInfo>()
                .eq(BizCompanyInfo::getDeptId, companyId)
                .last("LIMIT 1")
        );

        if (info == null) {
            log.info("公司概况数据为空，companyId={}", companyId);
            return EnrichmentResult.empty();
        }

        StringBuilder md = new StringBuilder();
        md.append("以下为公司基本信息（真实数据）：\n\n");
        appendKeyValue(md, "统一社会信用代码", info.getUnifiedCreditCode());
        appendKeyValue(md, "法定代表人", info.getLegalPerson());
        appendKeyValue(md, "注册资本", info.getRegisteredCapital());
        appendKeyValue(md, "企业性质", info.getEnterpriseNature());
        appendKeyValue(md, "成立日期", formatDate(info.getEstablishmentDate()));
        appendKeyValue(md, "注册地址", info.getRegisteredAddress());
        appendKeyValue(md, "经营范围", info.getBusinessScope());
        appendKeyValue(md, "企业规模", info.getEnterpriseScale());
        appendKeyValue(md, "行业类别", info.getIndustryCategory());
        appendKeyValue(md, "公司地址", info.getCompanyAddress());
        appendKeyValue(md, "公司网站", info.getCompanyWebsite());
        appendKeyValue(md, "企业电话", info.getEnterprisePhone());
        appendKeyValue(md, "员工总数", info.getTotalEmployees() != null ? String.valueOf(info.getTotalEmployees()) : null);
        appendKeyValue(md, "高级职称人数", info.getSeniorTitleCount() != null ? String.valueOf(info.getSeniorTitleCount()) : null);
        appendKeyValue(md, "中级职称人数", info.getJuniorTitleCount() != null ? String.valueOf(info.getJuniorTitleCount()) : null);
        appendKeyValue(md, "管理体系认证", info.getManagementCertification());

        List<AttachmentRef> attachments = new ArrayList<>();
        collectImages(attachments, "COMPANY", "营业执照", info.getBusinessLicenseImg());
        collectImages(attachments, "COMPANY", "安全生产许可证", info.getSafetyPermitImg());

        EnrichmentResult result = new EnrichmentResult();
        result.setContentType(ChapterContentType.COMPANY_PROFILE);
        result.setStructuredDataMarkdown(truncate(md.toString(), 8000));
        result.setFormatInstructions(INSTRUCTION_COMPANY_PROFILE);
        result.setAttachments(attachments);
        result.setRecordCount(1);
        return result;
    }

    // ==================== 格式指令常量 ====================

    private static final String INSTRUCTION_QUALIFICATION = """
        本章节需要按照规定格式生成企业资质证书一览表。请基于以下真实资质数据生成内容：
        1. 开头用 1-2 段概述公司资质总体情况（总数量、覆盖领域）
        2. 按类别分组展示（如：行业资质、体系认证等），每组用三级标题
        3. 每组内使用 Markdown 表格：序号、证书名称、证书编号、发证机关、有效期
        4. 表格后可加简要说明，突出资质的竞争力
        5. 严禁编造不在数据中的资质信息
        6. 不需要插入 {{IMAGE:...}} 占位符，附件图片会自动追加在章节末尾""";

    private static final String INSTRUCTION_PERFORMANCE = """
        本章节需要按照规定格式生成业绩案例展示。请基于以下真实业绩数据生成内容：
        1. 开头概述公司业绩总体情况（总数量、总合同额、涉及领域）
        2. 使用 Markdown 表格展示全部业绩：序号、项目名称、业主单位、合同金额(万元)、签约日期、竣工日期
        3. 选取 3-5 个代表性业绩用详细段落展开描述
        4. 强调与当前招标项目类型相关的业绩经验
        5. 严禁编造不在数据中的业绩项目
        6. 不需要插入 {{IMAGE:...}} 占位符""";

    private static final String INSTRUCTION_PERSONNEL = """
        本章节需要按照规定格式生成拟投入人员配置表。请基于以下真实人员数据生成内容：
        1. 开头概述项目团队配置思路和人员优势
        2. 使用 Markdown 表格：序号、姓名、拟任岗位、工作年限、持有证书、代表项目
        3. 对关键岗位人员用段落详细介绍其资历和项目经验
        4. 严禁编造不在数据中的人员信息
        5. 不需要插入 {{IMAGE:...}} 占位符""";

    private static final String INSTRUCTION_PATENT = """
        本章节需要按照规定格式生成专利/知识产权一览表。请基于以下真实专利数据生成内容：
        1. 开头概述公司技术创新能力和知识产权总体情况
        2. 按专利类型分组（发明专利、实用新型、外观设计），各组使用 Markdown 表格
        3. 表格列：序号、专利名称、专利号、专利类型、授权日期、发明人
        4. 突出与招标项目相关的技术专利
        5. 严禁编造不在数据中的专利信息
        6. 不需要插入 {{IMAGE:...}} 占位符""";

    private static final String INSTRUCTION_FINANCE = """
        本章节需要按照规定格式生成财务信息概要。请基于以下真实财务数据生成内容：
        1. 开头概述公司财务状况总体健康度
        2. 使用 Markdown 表格：序号、名称、类型、日期
        3. 如有审计报告，说明审计结果
        4. 突出财务稳健性
        5. 严禁编造不在数据中的财务信息""";

    private static final String INSTRUCTION_PRODUCT = """
        本章节需要按照规定格式生成设备/产品配置表。请基于以下真实产品数据生成内容：
        1. 开头概述拟投入的设备/产品总体情况
        2. 使用 Markdown 表格：序号、名称、型号、数量、性能说明
        3. 对关键设备做补充说明
        4. 严禁编造不在数据中的设备信息""";

    private static final String INSTRUCTION_COMPANY_PROFILE = """
        本章节需要生成公司概况介绍。请基于以下真实公司数据生成内容：
        1. 包含：公司基本信息、发展历程概述、主营业务、组织规模
        2. 使用段落 + 关键数据表格相结合的形式
        3. 突出公司在本行业领域的优势和地位
        4. 严禁编造不在数据中的公司信息""";

    // ==================== 工具方法 ====================

    private String safe(String value) {
        return value != null ? value : "-";
    }

    private String formatDate(Date date) {
        if (date == null) return "-";
        try {
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_FORMAT);
        } catch (Exception e) {
            return "-";
        }
    }

    private String formatDateRange(Date start, Date end) {
        String s = formatDate(start);
        String e = formatDate(end);
        if ("-".equals(s) && "-".equals(e)) return "-";
        return s + " ~ " + e;
    }

    private void appendKeyValue(StringBuilder sb, String key, String value) {
        if (value != null && !value.isBlank()) {
            sb.append("- **").append(key).append("**：").append(value).append("\n");
        }
    }

    /**
     * 收集图片引用（支持逗号分隔的多张图片）
     */
    private void collectImages(List<AttachmentRef> attachments, String type, String caption, String imageUrls) {
        if (imageUrls == null || imageUrls.isBlank()) return;
        String[] urls = imageUrls.split(",");
        for (int i = 0; i < urls.length; i++) {
            String url = urls[i].trim();
            if (!url.isBlank()) {
                String cap = urls.length > 1
                    ? caption + " (" + (i + 1) + ")"
                    : caption;
                attachments.add(new AttachmentRef(type, cap, url));
            }
        }
    }

    /**
     * 截断字符串
     */
    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) + "\n...(数据已截断)" : text;
    }
}
