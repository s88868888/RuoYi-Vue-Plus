package org.dromara.resource.service.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.resource.domain.*;
import org.dromara.resource.mapper.*;
import org.dromara.system.domain.SysOss;
import org.dromara.system.mapper.SysOssMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 图片检索Agent
 * 负责解析章节内容中的图片占位符，从数据库中匹配对应图片并替换为HTML img标签
 *
 * 支持五大知识库分类：
 * - PERSONNEL（人员照片及证书）
 * - QUALIFICATION（企业资质）
 * - PERFORMANCE（业绩案例）
 * - PATENT（专利奖章）
 * - FINANCE（财务信息）
 *
 * @author ruoyi
 * @date 2026-03-04
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageRetrievalAgent {

    private final BizPersonnelMapper personnelMapper;
    private final BizPersonnelCertificateMapper certificateMapper;
    private final BizQualificationMapper qualificationMapper;
    private final BizPerformanceMapper performanceMapper;
    private final BizPatentMedalMapper patentMedalMapper;
    private final BizFinanceInfoMapper financeInfoMapper;
    private final SysOssMapper sysOssMapper;

    /** 占位符正则：{{IMAGE:TYPE:NAME}} */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{IMAGE:(\\w+):(.+?)\\}\\}");

    /**
     * 解析并替换内容中的所有图片占位符
     *
     * @param content 含占位符的章节内容
     * @param deptId  公司/部门ID，用于数据权限过滤
     * @return 替换后的内容
     */
    public String resolveImagePlaceholders(String content, Long deptId) {
        if (content == null || content.isBlank()) {
            return content;
        }

        log.info("开始解析图片占位符，deptId: {}, 内容长度: {}", deptId, content.length());

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(content);
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;

        while (matcher.find()) {
            String type = matcher.group(1);
            String name = matcher.group(2).trim();

            log.info("发现图片占位符: type={}, name={}, deptId={}", type, name, deptId);

            // 追加占位符之前的内容
            result.append(content, lastEnd, matcher.start());

            // 解析占位符为图片HTML
            String imageHtml = resolveImage(type, name, deptId);
            result.append(imageHtml);

            lastEnd = matcher.end();
        }

        // 追加剩余内容
        result.append(content, lastEnd, content.length());
        return result.toString();
    }

    /**
     * 根据类型和名称查询图片并生成HTML
     */
    private String resolveImage(String type, String name, Long deptId) {
        try {
            return switch (type.toUpperCase()) {
                case "PERSONNEL" -> resolvePersonnelImages(name, deptId);
                case "QUALIFICATION" -> resolveQualificationImages(name, deptId);
                case "PERFORMANCE" -> resolvePerformanceImages(name, deptId);
                case "PATENT" -> resolvePatentImages(name, deptId);
                case "FINANCE" -> resolveFinanceImages(name, deptId);
                default -> buildMissingHtml(type, name);
            };
        } catch (Exception e) {
            log.warn("解析图片占位符失败: type={}, name={}", type, name, e);
            return buildMissingHtml(type, name);
        }
    }

    /**
     * 人员图片：照片 + 证书图片
     */
    private String resolvePersonnelImages(String name, Long deptId) {
        LambdaQueryWrapper<BizPersonnel> wrapper = new LambdaQueryWrapper<BizPersonnel>()
            .like(BizPersonnel::getName, name);
        if (deptId != null) {
            wrapper.eq(BizPersonnel::getDeptId, deptId);
        }

        List<BizPersonnel> personnelList = personnelMapper.selectList(wrapper);
        log.info("人员图片查询: name={}, deptId={}, 结果数量={}", name, deptId, personnelList.size());
        if (personnelList.isEmpty()) {
            return buildMissingHtml("PERSONNEL", name);
        }

        StringBuilder html = new StringBuilder();
        for (BizPersonnel person : personnelList) {
            // 先插入人员照片
            if (person.getPhoto() != null && !person.getPhoto().isBlank()) {
                html.append(buildImageHtml(person.getPhoto(), person.getName() + " - 照片"));
            }

            // 再插入该人员的所有证书图片
            List<BizPersonnelCertificate> certs = certificateMapper.selectList(
                new LambdaQueryWrapper<BizPersonnelCertificate>()
                    .eq(BizPersonnelCertificate::getPersonnelId, person.getId())
            );
            for (BizPersonnelCertificate cert : certs) {
                if (cert.getCertificateImage() != null && !cert.getCertificateImage().isBlank()) {
                    String caption = person.getName() + " - " +
                        (cert.getCertificateName() != null ? cert.getCertificateName() : "证书");
                    html.append(buildImageHtml(cert.getCertificateImage(), caption));
                }
            }
        }

        return html.isEmpty() ? buildMissingHtml("PERSONNEL", name) : html.toString();
    }

    /**
     * 企业资质图片
     */
    private String resolveQualificationImages(String name, Long deptId) {
        LambdaQueryWrapper<BizQualification> wrapper = new LambdaQueryWrapper<BizQualification>()
            .like(BizQualification::getCertName, name);
        if (deptId != null) {
            wrapper.eq(BizQualification::getDeptId, deptId);
        }

        List<BizQualification> qualList = qualificationMapper.selectList(wrapper);
        log.info("资质图片查询: name={}, deptId={}, 结果数量={}", name, deptId, qualList.size());
        if (qualList.isEmpty()) {
            return buildMissingHtml("QUALIFICATION", name);
        }

        StringBuilder html = new StringBuilder();
        for (BizQualification qual : qualList) {
            if (qual.getCertImages() != null && !qual.getCertImages().isBlank()) {
                String[] urls = qual.getCertImages().split(",");
                for (int i = 0; i < urls.length; i++) {
                    String url = urls[i].trim();
                    if (!url.isBlank()) {
                        String caption = qual.getCertName() + (urls.length > 1 ? " (" + (i + 1) + ")" : "");
                        html.append(buildImageHtml(url, caption));
                    }
                }
            }
        }

        return html.isEmpty() ? buildMissingHtml("QUALIFICATION", name) : html.toString();
    }

    /**
     * 业绩案例图片：合同图片、中标通知书、验收报告等
     */
    private String resolvePerformanceImages(String name, Long deptId) {
        LambdaQueryWrapper<BizPerformance> wrapper = new LambdaQueryWrapper<BizPerformance>()
            .like(BizPerformance::getName, name);
        if (deptId != null) {
            wrapper.eq(BizPerformance::getDeptId, deptId);
        }

        List<BizPerformance> perfList = performanceMapper.selectList(wrapper);
        log.info("业绩图片查询: name={}, deptId={}, 结果数量={}", name, deptId, perfList.size());
        if (perfList.isEmpty()) {
            return buildMissingHtml("PERFORMANCE", name);
        }

        StringBuilder html = new StringBuilder();
        for (BizPerformance perf : perfList) {
            appendMultiImages(html, perf.getContractImages(), perf.getName() + " - 合同");
            appendMultiImages(html, perf.getBidNoticeAttachment(), perf.getName() + " - 中标通知书");
            appendMultiImages(html, perf.getAcceptanceAttachment(), perf.getName() + " - 验收报告");
        }

        return html.isEmpty() ? buildMissingHtml("PERFORMANCE", name) : html.toString();
    }

    /**
     * 专利奖章图片
     */
    private String resolvePatentImages(String name, Long deptId) {
        LambdaQueryWrapper<BizPatentMedal> wrapper = new LambdaQueryWrapper<BizPatentMedal>()
            .like(BizPatentMedal::getPatentName, name);
        if (deptId != null) {
            wrapper.eq(BizPatentMedal::getDeptId, deptId);
        }

        List<BizPatentMedal> patentList = patentMedalMapper.selectList(wrapper);
        log.info("专利图片查询: name={}, deptId={}, 结果数量={}", name, deptId, patentList.size());
        if (patentList.isEmpty()) {
            return buildMissingHtml("PATENT", name);
        }

        StringBuilder html = new StringBuilder();
        for (BizPatentMedal patent : patentList) {
            if (patent.getPatentImage() != null && !patent.getPatentImage().isBlank()) {
                html.append(buildImageHtml(patent.getPatentImage(), patent.getPatentName() + " - 专利"));
            }
            if (patent.getCertificateImage() != null && !patent.getCertificateImage().isBlank()) {
                html.append(buildImageHtml(patent.getCertificateImage(), patent.getPatentName() + " - 证书"));
            }
        }

        return html.isEmpty() ? buildMissingHtml("PATENT", name) : html.toString();
    }

    /**
     * 财务信息附件
     */
    private String resolveFinanceImages(String name, Long deptId) {
        LambdaQueryWrapper<BizFinanceInfo> wrapper = new LambdaQueryWrapper<BizFinanceInfo>()
            .like(BizFinanceInfo::getFinanceName, name);
        if (deptId != null) {
            wrapper.eq(BizFinanceInfo::getDeptId, deptId);
        }

        List<BizFinanceInfo> financeList = financeInfoMapper.selectList(wrapper);
        log.info("财务图片查询: name={}, deptId={}, 结果数量={}", name, deptId, financeList.size());
        if (financeList.isEmpty()) {
            return buildMissingHtml("FINANCE", name);
        }

        StringBuilder html = new StringBuilder();
        for (BizFinanceInfo finance : financeList) {
            if (finance.getAttachmentUrl() != null && !finance.getAttachmentUrl().isBlank()) {
                html.append(buildImageHtml(finance.getAttachmentUrl(),
                    finance.getFinanceName() != null ? finance.getFinanceName() : "财务信息"));
            }
        }

        return html.isEmpty() ? buildMissingHtml("FINANCE", name) : html.toString();
    }

    /**
     * 处理逗号分隔的多张图片URL
     */
    private void appendMultiImages(StringBuilder html, String imageUrls, String captionPrefix) {
        if (imageUrls == null || imageUrls.isBlank()) {
            return;
        }
        String[] urls = imageUrls.split(",");
        for (int i = 0; i < urls.length; i++) {
            String url = urls[i].trim();
            if (!url.isBlank()) {
                String caption = captionPrefix + (urls.length > 1 ? " (" + (i + 1) + ")" : "");
                html.append(buildImageHtml(url, caption));
            }
        }
    }

    /**
     * 构建图片HTML（使用div+data-align确保在AiEditor中居中显示）
     */
    public String buildImageHtml(String urlOrOssId, String caption) {
        String url = resolveOssUrl(urlOrOssId);
        return String.format(
            "<div style=\"text-align:center\"><img src=\"%s\" alt=\"%s\" data-align=\"center\" style=\"max-width:80%%;border:1px solid #eee;border-radius:4px;\" /></div>" +
            "<p style=\"text-align:center;color:#666;font-size:12px;margin-top:4px;\">图：%s</p>\n",
            url, caption, caption);
    }

    /**
     * 将OSS ID转换为可访问的URL
     * 如果传入的是纯数字（OSS ID），从sys_oss表查询实际URL；否则原样返回
     */
    public String resolveOssUrl(String ossIdOrUrl) {
        if (ossIdOrUrl == null || ossIdOrUrl.isBlank()) {
            return ossIdOrUrl;
        }
        String value = ossIdOrUrl.trim();
        // 纯数字视为 OSS ID
        if (value.matches("\\d+")) {
            try {
                SysOss oss = sysOssMapper.selectById(Long.parseLong(value));
                if (oss != null && oss.getUrl() != null && !oss.getUrl().isBlank()) {
                    log.debug("OSS ID {} -> URL {}", value, oss.getUrl());
                    return oss.getUrl();
                }
                log.warn("OSS记录不存在或URL为空, ossId: {}", value);
            } catch (Exception e) {
                log.warn("OSS ID解析失败: {}", value, e);
            }
        }
        return value;
    }

    /**
     * 构建缺失图片占位HTML
     */
    private String buildMissingHtml(String type, String name) {
        log.warn("图片缺失: type={}, name={}", type, name);
        return String.format(
            "<div style=\"text-align:center;margin:16px 0;padding:20px;border:2px dashed #ffa940;border-radius:4px;background:#fff7e6;\">" +
            "<span style=\"color:#fa8c16;font-size:14px;\">&#9888; 缺失图片: %s</span></div>\n",
            name);
    }
}
