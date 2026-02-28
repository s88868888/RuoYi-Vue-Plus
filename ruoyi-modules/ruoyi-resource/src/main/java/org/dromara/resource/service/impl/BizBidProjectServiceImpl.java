package org.dromara.resource.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.resource.domain.BizBidProject;
import org.dromara.resource.domain.bo.BizBidProjectBo;
import org.dromara.resource.domain.dto.QuickGenerateDto;
import org.dromara.resource.domain.vo.BizBidProjectVo;
import org.dromara.resource.mapper.BizBidProjectMapper;
import org.dromara.resource.service.IAiAnalysisService;
import org.dromara.resource.service.IBizBidProjectService;
import org.dromara.system.domain.vo.SysOssVo;
import org.dromara.system.service.ISysOssService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 招标项目Service实现
 *
 * @author ruoyi
 * @date 2026-02-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BizBidProjectServiceImpl extends ServiceImpl<BizBidProjectMapper, BizBidProject> implements IBizBidProjectService {

    private final BizBidProjectMapper bizBidProjectMapper;
    private final ChatModel chatModel;
    private final IAiAnalysisService aiAnalysisService;
    private final ISysOssService sysOssService;

    @Override
    public TableDataInfo<BizBidProjectVo> queryPageList(BizBidProjectBo bo, PageQuery pageQuery) {
        Page<BizBidProjectVo> page = bizBidProjectMapper.selectVoPage(pageQuery.build(), buildQueryWrapper(bo));
        return TableDataInfo.build(page);
    }

    @Override
    public List<BizBidProjectVo> queryList(BizBidProjectBo bo) {
        return bizBidProjectMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public BizBidProjectVo queryById(Long id) {
        return bizBidProjectMapper.selectVoById(id);
    }

    @Override
    public Boolean insertByBo(BizBidProjectBo bo) {
        BizBidProject add = MapstructUtils.convert(bo, BizBidProject.class);
        validEntityBeforeSave(add);
        boolean flag = bizBidProjectMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    @Override
    public Boolean updateByBo(BizBidProjectBo bo) {
        BizBidProject update = MapstructUtils.convert(bo, BizBidProject.class);
        validEntityBeforeSave(update);
        return bizBidProjectMapper.updateById(update) > 0;
    }

    @Override
    public Boolean deleteWithValidByIds(List<Long> ids, Boolean isValid) {
        if (isValid) {
            List<BizBidProject> list = bizBidProjectMapper.selectByIds(ids);
            if (list.size() != ids.size()) {
                throw new ServiceException("删除失败，部分数据不存在");
            }
        }
        return bizBidProjectMapper.deleteByIds(ids) > 0;
    }

    /**
     * 构建查询条件
     */
    private LambdaQueryWrapper<BizBidProject> buildQueryWrapper(BizBidProjectBo bo) {
        LambdaQueryWrapper<BizBidProject> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(ObjectUtil.isNotEmpty(bo.getProjectName()), BizBidProject::getProjectName, bo.getProjectName());
        wrapper.like(ObjectUtil.isNotEmpty(bo.getBidOrg()), BizBidProject::getBidOrg, bo.getBidOrg());
        wrapper.eq(ObjectUtil.isNotEmpty(bo.getProjectType()), BizBidProject::getProjectType, bo.getProjectType());
        wrapper.eq(ObjectUtil.isNotEmpty(bo.getStatus()), BizBidProject::getStatus, bo.getStatus());
        wrapper.eq(ObjectUtil.isNotEmpty(bo.getBidMethod()), BizBidProject::getBidMethod, bo.getBidMethod());
        wrapper.eq(ObjectUtil.isNotEmpty(bo.getProjectSource()), BizBidProject::getProjectSource, bo.getProjectSource());
        wrapper.ge(ObjectUtil.isNotEmpty(bo.getPublishDate()), BizBidProject::getPublishDate, bo.getPublishDate());
        wrapper.le(ObjectUtil.isNotEmpty(bo.getDeadline()), BizBidProject::getDeadline, bo.getDeadline());
        wrapper.orderByDesc(BizBidProject::getCreateTime);
        return wrapper;
    }

    /**
     * 保存前校验
     */
    private void validEntityBeforeSave(BizBidProject entity) {
        // 可以在此处添加校验逻辑
    }

    @Override
    public Long quickGenerateFromPdf(QuickGenerateDto dto, MultipartFile file) {
        log.info("开始从PDF快速生成招标项目: {}", file.getOriginalFilename());

        try {
            // 1. 提取PDF文本内容
            String pdfContent = extractPdfText(file);

            // 2. 使用AI提取招标信息
            Map<String, Object> bidInfo = extractBidInfoFromAi(pdfContent);

            // 3. 上传文件到OSS
            SysOssVo ossVo = sysOssService.upload(file);

            // 4. 创建招标项目
            BizBidProject project = new BizBidProject();
            project.setProjectName((String) bidInfo.getOrDefault("projectName", "未命名项目"));
            project.setBidOrg((String) bidInfo.getOrDefault("bidOrg", ""));
            project.setProjectType((String) bidInfo.getOrDefault("projectType", ""));
            project.setBudgetAmount((BigDecimal) bidInfo.get("budgetAmount"));
            project.setPublishDate((Date) bidInfo.get("publishDate"));
            project.setDeadline((Date) bidInfo.get("deadline"));
            project.setProjectRegion((String) bidInfo.getOrDefault("projectRegion", ""));
            project.setBidMethod((String) bidInfo.getOrDefault("bidMethod", ""));
            project.setContactPerson((String) bidInfo.getOrDefault("contactPerson", ""));
            project.setContactPhone((String) bidInfo.getOrDefault("contactPhone", ""));
            project.setProjectDesc((String) bidInfo.getOrDefault("projectDesc", ""));
            project.setStatus("following");
            project.setProjectSource("quick_generate");
            project.setAttachments(String.valueOf(ossVo.getOssId()));
            project.setAttachmentName(ossVo.getOriginalName());
            project.setAiPrompt(dto.getAiPrompt());
            project.setAiAnalysisStatus("pending");
            project.setScoringCriteriaStatus("pending");

            // 5. 保存项目
            bizBidProjectMapper.insert(project);
            Long projectId = project.getId();

            log.info("招标项目创建成功，ID: {}", projectId);

            // 6. 如果需要AI分析
            if (Boolean.TRUE.equals(dto.getEnableAiAnalysis())) {
                aiAnalysisService.analyzeBidProjectAsync(projectId, dto.getAiPrompt());
            }

            // 7. 如果需要提取评分标准
            if (Boolean.TRUE.equals(dto.getEnableExtractScoringCriteria())) {
                aiAnalysisService.extractScoringCriteriaAsync(projectId, dto.getScoringPrompt());
            }

            // 8. 如果需要契合度分析
            if (Boolean.TRUE.equals(dto.getAnalyzeMatchDegree())) {
                aiAnalysisService.analyzeMatchDegreeAsync(projectId, dto.getMatchAnalysisPrompt());
            }

            return projectId;

        } catch (Exception e) {
            log.error("从PDF快速生成招标项目失败", e);
            throw new ServiceException("解析失败：" + e.getMessage());
        }
    }

    /**
     * 从MultipartFile提取PDF文本
     */
    private String extractPdfText(MultipartFile file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    /**
     * 使用AI从PDF内容中提取招标信息
     */
    private Map<String, Object> extractBidInfoFromAi(String pdfContent) {
        Map<String, Object> result = new HashMap<>();

        String prompt = buildExtractPrompt(pdfContent);

        try {
            String aiResponse = chatModel.call(new Prompt(List.of(
                new SystemMessage("你是一个专业的招标信息提取助手，负责从招标文件中提取结构化的项目信息。"),
                new UserMessage(prompt)
            ))).getResult().getOutput().getText();

            // 解析AI返回的JSON结果
            result = parseAiResponse(aiResponse);

        } catch (Exception e) {
            log.error("AI提取招标信息失败", e);
            // 返回默认空值
            result.put("projectName", "未命名项目");
        }

        return result;
    }

    /**
     * 构建提取提示词
     */
    private String buildExtractPrompt(String pdfContent) {
        // 限制内容长度，避免超出模型上下文限制
        String content = pdfContent.length() > 15000 ? pdfContent.substring(0, 15000) + "..." : pdfContent;

        return """
            请从以下招标文件中提取项目信息，并以JSON格式返回。

            招标文件内容：
            %s

            请提取以下字段并返回JSON：
            {
                "projectName": "项目名称",
                "bidOrg": "招标单位/采购单位",
                "projectType": "项目类型（engineering工程 goods货物 service服务）",
                "budgetAmount": 预算金额（纯数字，单位元，如100万则返回1000000，如无则返回null）,
                "publishDate": "发布日期（YYYY-MM-DD格式，如无则返回null）",
                "deadline": "截止日期/投标截止时间（YYYY-MM-DD格式，如无则返回null）",
                "projectRegion": "项目所在地区/省份城市",
                "bidMethod": "招标方式（public公开招标 invite邀请招标 competitive竞争性谈判 inquiry询价采购 single单一来源）",
                "contactPerson": "联系人姓名（如无则返回空字符串）",
                "contactPhone": "联系电话（如无则返回空字符串）",
                "projectDesc": "项目描述/概况（100字以内）"
            }

            注意事项：
            - budgetAmount只能是纯数字，不包含单位，无法确定时返回null
            - 日期格式必须为YYYY-MM-DD，无法确定时返回null
            - 只返回JSON，不要有其他说明文字
            """.formatted(content);
    }

    /**
     * 解析AI返回的JSON响应
     */
    private Map<String, Object> parseAiResponse(String aiResponse) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 尝试提取JSON部分
            Pattern jsonPattern = Pattern.compile("\\{[\\s\\S]*\\}");
            Matcher matcher = jsonPattern.matcher(aiResponse);

            if (matcher.find()) {
                String jsonStr = matcher.group();

                // 简单解析JSON（实际可以使用JSON库）
                result.put("projectName", extractJsonValue(jsonStr, "projectName"));
                result.put("bidOrg", extractJsonValue(jsonStr, "bidOrg"));
                result.put("projectType", extractJsonValue(jsonStr, "projectType"));
                result.put("projectRegion", extractJsonValue(jsonStr, "projectRegion"));
                result.put("bidMethod", extractJsonValue(jsonStr, "bidMethod"));
                result.put("contactPerson", extractJsonValue(jsonStr, "contactPerson"));
                result.put("contactPhone", extractJsonValue(jsonStr, "contactPhone"));
                result.put("projectDesc", extractJsonValue(jsonStr, "projectDesc"));

                // 解析预算金额
                String budgetStr = extractJsonValue(jsonStr, "budgetAmount");
                if (budgetStr != null && !budgetStr.isEmpty()) {
                    try {
                        result.put("budgetAmount", new BigDecimal(budgetStr.replaceAll("[^0-9.]", "")));
                    } catch (NumberFormatException e) {
                        result.put("budgetAmount", null);
                    }
                }

                // 解析日期
                String publishDateStr = extractJsonValue(jsonStr, "publishDate");
                if (publishDateStr != null && !publishDateStr.isEmpty()) {
                    result.put("publishDate", parseDate(publishDateStr));
                }

                String deadlineStr = extractJsonValue(jsonStr, "deadline");
                if (deadlineStr != null && !deadlineStr.isEmpty()) {
                    result.put("deadline", parseDate(deadlineStr));
                }
            }
        } catch (Exception e) {
            log.error("解析AI响应失败", e);
        }

        return result;
    }

    /**
     * 从JSON字符串中提取指定字段的值（支持字符串、数字、null）
     */
    private String extractJsonValue(String json, String field) {
        // 先匹配字符串值 "field": "value"
        Pattern strPattern = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher strMatcher = strPattern.matcher(json);
        if (strMatcher.find()) {
            return strMatcher.group(1);
        }
        // 再匹配数字或null值 "field": 123 / "field": null
        Pattern numPattern = Pattern.compile("\"" + field + "\"\\s*:\\s*([\\d.]+|null)");
        Matcher numMatcher = numPattern.matcher(json);
        if (numMatcher.find()) {
            String val = numMatcher.group(1);
            return "null".equals(val) ? null : val;
        }
        return null;
    }

    /**
     * 解析日期字符串
     */
    private Date parseDate(String dateStr) {
        try {
            // 尝试多种日期格式
            String[] formats = {"yyyy-MM-dd", "yyyy/MM/dd", "yyyy年MM月dd日"};
            for (String format : formats) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat(format);
                    return sdf.parse(dateStr);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            log.warn("日期解析失败: {}", dateStr);
        }
        return null;
    }

}
