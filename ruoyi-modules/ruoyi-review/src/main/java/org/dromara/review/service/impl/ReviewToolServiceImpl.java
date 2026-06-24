package org.dromara.review.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.oss.core.OssClient;
import org.dromara.common.oss.entity.UploadResult;
import org.dromara.common.oss.factory.OssFactory;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.review.domain.ReviewResultItem;
import org.dromara.review.domain.ReviewStandardFocus;
import org.dromara.review.domain.ReviewStandardRule;
import org.dromara.review.domain.ReviewTask;
import org.dromara.review.domain.ReviewTaskFile;
import org.dromara.review.domain.ReviewTaskStandard;
import org.dromara.review.domain.vo.ReviewToolResultVo;
import org.dromara.review.mapper.ReviewResultItemMapper;
import org.dromara.review.mapper.ReviewStandardFocusMapper;
import org.dromara.review.mapper.ReviewStandardRuleMapper;
import org.dromara.review.mapper.ReviewTaskFileMapper;
import org.dromara.review.mapper.ReviewTaskMapper;
import org.dromara.review.mapper.ReviewTaskStandardMapper;
import org.dromara.review.service.IReviewToolService;
import org.dromara.review.service.ReviewOcrAsyncService;
import org.dromara.system.domain.vo.SysOssVo;
import org.dromara.system.service.ISysOssService;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AI 审核工具结果聚合服务实现。
 *
 * @author Linson
 * @date 2026-06-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewToolServiceImpl implements IReviewToolService {

    private final ReviewTaskMapper taskMapper;
    private final ReviewTaskFileMapper taskFileMapper;
    private final ReviewResultItemMapper resultItemMapper;
    private final ReviewTaskStandardMapper taskStandardMapper;
    private final ReviewStandardRuleMapper standardRuleMapper;
    private final ReviewStandardFocusMapper standardFocusMapper;
    private final ReviewOcrAsyncService ocrAsyncService;
    private final ISysOssService ossService;

    @Override
    public ReviewToolResultVo getToolResult(Long taskId) {
        ReviewTask task = taskMapper.selectById(taskId);
        if (task == null) {
            return null;
        }
        ReviewToolResultVo vo = new ReviewToolResultVo();
        vo.setId(String.valueOf(task.getId()));
        vo.setReviewTaskId(task.getId());
        vo.setStatus(normalizeStatus(task.getStatus()));
        vo.setPassStatus(task.getPassStatus());
        vo.setScore(task.getScore());
        vo.setErrorCount(task.getErrorCount());
        vo.setWarningCount(task.getWarningCount());
        vo.setInfoCount(task.getInfoCount());
        vo.setAiSummary(task.getAiSummary());
        vo.setNoteData(task.getNoteData());
        vo.setRedactData(task.getRedactData());
        vo.setCreatetime(task.getCreateTime());
        vo.setUpdatetime(task.getUpdateTime());
        if ("FAIL".equals(vo.getStatus())) {
            vo.setErrorMsg(StringUtils.isNotBlank(task.getAiSummary()) ? task.getAiSummary() : "AI 审核执行失败");
        }

        // ===== 文件：COMPARE 双文件(A基准/B对比)，AUDIT 单文件(B被审文档) =====
        List<ReviewTaskFile> files = taskFileMapper.selectList(
            Wrappers.<ReviewTaskFile>lambdaQuery()
                .eq(ReviewTaskFile::getTaskId, taskId)
                .orderByAsc(ReviewTaskFile::getSortOrder));
        // 视图类型以文件数为准：taskType 同时承担"提示词模板类型"，可能是 Document_Review 等自定义值，
        // 仅凭关键字判定会落空 → 双文件=对比，单文件=内容审查。
        vo.setReviewtype(toReviewType(task.getTaskType(), files.size()));
        boolean isCompare = "COMPARE".equals(vo.getReviewtype());
        ReviewTaskFile signFile;
        if (isCompare) {
            ReviewTaskFile baseFile = !files.isEmpty() ? files.get(0) : null;
            signFile = files.size() >= 2 ? files.get(1) : null;
            if (baseFile != null) {
                vo.setPrintPdfUrl(fileUrl(baseFile));
                vo.setPrintSearchableUrl(searchableUrl(baseFile));
            }
        } else {
            // AUDIT：单文档即 B 侧
            signFile = !files.isEmpty() ? files.get(0) : null;
        }
        if (signFile != null) {
            vo.setSignFileUrl(fileUrl(signFile));
            vo.setSignFileName(signFile.getFileName());
            vo.setSignSearchableUrl(searchableUrl(signFile));
            vo.setSignOssId(signFile.getOssId());
            vo.setSignOcrStatus(StringUtils.isNotBlank(signFile.getOcrStatus()) ? signFile.getOcrStatus() : "NONE");
        }

        // ===== 懒触发 OCR：对未跑过的 PDF 文件发起异步生成 searchable PDF =====
        for (ReviewTaskFile f : files) {
            if (StringUtils.isBlank(f.getOcrStatus()) && isPdf(f)) {
                ocrAsyncService.processFileAsync(f.getId());
                if (signFile != null && Objects.equals(f.getId(), signFile.getId())) {
                    vo.setSignOcrStatus("RUNNING");
                }
            }
        }

        // ===== 问题明细：enrich ruleContent/checkMethod =====
        List<ReviewResultItem> items = resultItemMapper.selectList(
            Wrappers.<ReviewResultItem>lambdaQuery()
                .eq(ReviewResultItem::getTaskId, taskId)
                .orderByAsc(ReviewResultItem::getSortOrder));
        Set<Long> ruleIds = items.stream()
            .map(ReviewResultItem::getRuleId)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, ReviewStandardRule> ruleMap = ruleIds.isEmpty()
            ? Map.of()
            : standardRuleMapper.selectByIds(ruleIds).stream()
                .collect(Collectors.toMap(ReviewStandardRule::getId, r -> r, (a, b) -> a));
        List<ReviewToolResultVo.IssueItem> issues = new ArrayList<>();
        for (ReviewResultItem it : items) {
            ReviewToolResultVo.IssueItem ii = new ReviewToolResultVo.IssueItem();
            ii.setFieldName(it.getFieldName());
            ii.setFieldLabel(it.getFieldLabel());
            ii.setFormValue(it.getFormValue());
            ii.setExtractedValue(it.getExtractedValue());
            ii.setMatchStatus(it.getMatchStatus());
            ii.setSeverity(it.getSeverity());
            ii.setConfidence(it.getConfidence());
            ii.setLocation(it.getLocation());
            ii.setDescription(it.getDescription());
            ii.setSuggestion(it.getSuggestion());
            ii.setNote(it.getNote());
            ReviewStandardRule rule = it.getRuleId() == null ? null : ruleMap.get(it.getRuleId());
            if (rule != null) {
                ii.setRuleContent(rule.getContent());
                ii.setCheckMethod(rule.getCheckMethod());
            }
            issues.add(ii);
        }
        vo.setIssues(issues);
        vo.setTotalIssues(issues.size());

        // ===== 关注列表(focusItems) / 关注要点(focusKeywords) / 关注分类(focusCategories) =====
        vo.setFocusItems(parseFocusItems(task.getFocusData()));
        List<Long> standardIds = taskStandardMapper.selectList(
                Wrappers.<ReviewTaskStandard>lambdaQuery().eq(ReviewTaskStandard::getTaskId, taskId))
            .stream().map(ReviewTaskStandard::getStandardId).distinct().collect(Collectors.toList());
        vo.setFocusKeywords(loadFocusKeywords(standardIds));
        vo.setFocusCategories(deriveFocusCategories(vo.getFocusItems()));

        return vo;
    }

    @Override
    public void saveCompareNote(Long taskId, String noteData) {
        ReviewTask patch = new ReviewTask();
        patch.setId(taskId);
        patch.setNoteData(noteData);
        taskMapper.updateById(patch);
    }

    @Override
    public void saveIssueNote(Long taskId, String fieldName, String note) {
        // 按 taskId + fieldName 定位结果明细，写入用户批注（同字段多条则一并更新）
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ReviewResultItem> luw =
            Wrappers.<ReviewResultItem>lambdaUpdate()
                .eq(ReviewResultItem::getTaskId, taskId)
                .eq(StringUtils.isNotBlank(fieldName), ReviewResultItem::getFieldName, fieldName)
                .set(ReviewResultItem::getNote, note);
        resultItemMapper.update(null, luw);
    }

    @Override
    public void saveRedactData(Long taskId, String redactData) {
        ReviewTask patch = new ReviewTask();
        patch.setId(taskId);
        patch.setRedactData(redactData);
        taskMapper.updateById(patch);
    }

    @Override
    public void triggerOcrByOssId(Long ossId) {
        if (ossId == null) {
            return;
        }
        ReviewTaskFile file = taskFileMapper.selectOne(
            Wrappers.<ReviewTaskFile>lambdaQuery()
                .eq(ReviewTaskFile::getOssId, ossId)
                .last("LIMIT 1"));
        if (file != null) {
            ocrAsyncService.processFileAsync(file.getId());
        }
    }

    @Override
    public Map<String, Object> getOcrStatus(Long ossId) {
        Map<String, Object> data = new java.util.HashMap<>();
        if (ossId == null) {
            data.put("ocrStatus", "NONE");
            return data;
        }
        ReviewTaskFile file = taskFileMapper.selectOne(
            Wrappers.<ReviewTaskFile>lambdaQuery()
                .eq(ReviewTaskFile::getOssId, ossId)
                .orderByDesc(ReviewTaskFile::getId)
                .last("LIMIT 1"));
        if (file == null) {
            data.put("ocrStatus", "NONE");
            return data;
        }
        // 状态为空的 PDF：懒触发一次，本轮先返回 RUNNING
        if (StringUtils.isBlank(file.getOcrStatus()) && isPdf(file)) {
            ocrAsyncService.processFileAsync(file.getId());
            data.put("ocrStatus", "RUNNING");
            return data;
        }
        data.put("ocrStatus", StringUtils.isNotBlank(file.getOcrStatus()) ? file.getOcrStatus() : "NONE");
        if (StringUtils.isNotBlank(file.getSearchableUrl())) {
            data.put("searchableUrl", file.getSearchableUrl());
        }
        return data;
    }

    @Override
    public List<ReviewStandardRule> getToolRules(Long taskId) {
        List<Long> standardIds = taskStandardMapper.selectList(
                Wrappers.<ReviewTaskStandard>lambdaQuery().eq(ReviewTaskStandard::getTaskId, taskId))
            .stream().map(ReviewTaskStandard::getStandardId).distinct().collect(Collectors.toList());
        if (CollUtil.isEmpty(standardIds)) {
            return new ArrayList<>();
        }
        return standardRuleMapper.selectList(
            Wrappers.<ReviewStandardRule>lambdaQuery()
                .in(ReviewStandardRule::getStandardId, standardIds)
                .ne(ReviewStandardRule::getStatus, "1")
                .orderByAsc(ReviewStandardRule::getSortOrder));
    }

    @Override
    public Map<String, Object> convertWordToPdf(Long ossId) {
        Map<String, Object> result = new java.util.HashMap<>();
        SysOssVo oss = TenantHelper.ignore(() -> ossService.getById(ossId));
        if (oss == null) {
            throw new RuntimeException("源文件不存在: ossId=" + ossId);
        }
        String name = StringUtils.isNotBlank(oss.getOriginalName()) ? oss.getOriginalName() : oss.getFileName();
        String lower = (name == null ? "" : name.toLowerCase());
        // 已是 PDF 或非 Word：原样返回，不转换
        if (lower.endsWith(".pdf") || !(lower.endsWith(".doc") || lower.endsWith(".docx"))) {
            result.put("ossId", ossId);
            result.put("url", oss.getUrl());
            result.put("name", name);
            result.put("converted", false);
            return result;
        }
        try {
            // 1. 下载 Word 字节
            byte[] src;
            OssClient storage = OssFactory.instance(oss.getService());
            Path tmp = storage.fileDownload(oss.getFileName());
            try {
                src = Files.readAllBytes(tmp);
            } finally {
                try { Files.deleteIfExists(tmp); } catch (Exception ignore) {}
            }
            // 2. Word → PDF（aspose-words 社区版，无水印）
            byte[] pdfBytes;
            try (ByteArrayInputStream in = new ByteArrayInputStream(src);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                com.aspose.words.Document doc = new com.aspose.words.Document(in);
                doc.save(out, com.aspose.words.SaveFormat.PDF);
                pdfBytes = out.toByteArray();
            }
            // 3. 传回 OSS（uploadSuffix 不建 sys_oss 行，前端按 filePath URL 用；引擎下载走 filePath 兜底）
            UploadResult up = OssFactory.instance().uploadSuffix(pdfBytes, ".pdf", "application/pdf");
            String pdfName = stripExt(name) + ".pdf";
            result.put("ossId", null);
            result.put("url", up.getUrl());
            result.put("name", pdfName);
            result.put("converted", true);
            log.info("[ReviewTool] Word→PDF 成功 ossId={} → {}", ossId, up.getUrl());
            return result;
        } catch (Exception e) {
            log.error("[ReviewTool] Word→PDF 失败 ossId={}: {}", ossId, e.getMessage(), e);
            throw new RuntimeException("Word 转 PDF 失败: " + e.getMessage());
        }
    }

    private String stripExt(String name) {
        if (name == null) return "document";
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    // ===================== helpers =====================

    /**
     * 引擎 taskType → 查看器审核类型 COMPARE/AUDIT。
     * taskType 同时是提示词模板类型，可能是 BCXY_COMPARE / CONTENT_AUDIT / Document_Review 等任意值，
     * 故先按关键字判定，命不中再以文件数兜底：双文件=对比，单/零文件=内容审查。
     */
    private String toReviewType(String taskType, int fileCount) {
        if (taskType != null) {
            String t = taskType.toUpperCase();
            if (t.contains("COMPARE")) {
                return "COMPARE";
            }
            if (t.contains("AUDIT")) {
                return "AUDIT";
            }
        }
        // 关键字未命中：按文件数判定（对比必有基准+对比两份，内容审查只有一份）
        return fileCount >= 2 ? "COMPARE" : "AUDIT";
    }

    /** 引擎状态 pending/reviewing/completed/failed → 城更口径 PENDING/RUNNING/SUCCESS/FAIL */
    private String normalizeStatus(String status) {
        if (status == null) {
            return "PENDING";
        }
        return switch (status.toLowerCase()) {
            case "completed" -> "SUCCESS";
            case "failed" -> "FAIL";
            case "reviewing" -> "RUNNING";
            default -> "PENDING";
        };
    }

    /** 文件可访问 URL：优先 filePath（外部URL），否则用文件名（前端拼 OSS 前缀） */
    private String fileUrl(ReviewTaskFile f) {
        if (f == null) {
            return null;
        }
        return StringUtils.isNotBlank(f.getFilePath()) ? f.getFilePath() : f.getFileName();
    }

    private String searchableUrl(ReviewTaskFile f) {
        return f == null ? null : f.getSearchableUrl();
    }

    private boolean isPdf(ReviewTaskFile f) {
        String type = f.getFileType();
        if (type != null && type.toLowerCase().contains("pdf")) {
            return true;
        }
        String name = f.getFileName();
        return name != null && name.toLowerCase().endsWith(".pdf");
    }

    /** 解析 task.focusData（fastjson snake_case 数组）→ FocusItem 列表 */
    private List<ReviewToolResultVo.FocusItem> parseFocusItems(String focusData) {
        List<ReviewToolResultVo.FocusItem> result = new ArrayList<>();
        if (StringUtils.isBlank(focusData)) {
            return result;
        }
        try {
            JSONArray arr = JSON.parseArray(focusData);
            for (int i = 0; i < arr.size(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (o == null) {
                    continue;
                }
                ReviewToolResultVo.FocusItem fi = new ReviewToolResultVo.FocusItem();
                fi.setKeyword(o.getString("keyword"));
                fi.setCategory(o.getString("category"));
                fi.setFieldLabel(firstNonBlank(o.getString("field_label"), o.getString("fieldLabel")));
                fi.setExtractedValue(firstNonBlank(o.getString("extracted_value"), o.getString("extractedValue")));
                fi.setLocation(o.getString("location"));
                BigDecimal conf = o.getBigDecimal("confidence");
                fi.setConfidence(conf);
                result.add(fi);
            }
        } catch (Exception e) {
            log.warn("[ReviewTool] 解析 focusData 失败: {}", e.getMessage());
        }
        return result;
    }

    /** 所选标准的关注要点（review_standard_focus，启用项） */
    private List<ReviewToolResultVo.FocusKeyword> loadFocusKeywords(List<Long> standardIds) {
        List<ReviewToolResultVo.FocusKeyword> result = new ArrayList<>();
        if (CollUtil.isEmpty(standardIds)) {
            return result;
        }
        List<ReviewStandardFocus> focuses = standardFocusMapper.selectList(
            Wrappers.<ReviewStandardFocus>lambdaQuery()
                .in(ReviewStandardFocus::getStandardId, standardIds)
                .ne(ReviewStandardFocus::getStatus, "1")
                .orderByAsc(ReviewStandardFocus::getSortOrder));
        Set<String> seen = new LinkedHashSet<>();
        for (ReviewStandardFocus f : focuses) {
            if (StringUtils.isBlank(f.getKeyword()) || !seen.add(f.getKeyword().trim())) {
                continue;
            }
            ReviewToolResultVo.FocusKeyword fk = new ReviewToolResultVo.FocusKeyword();
            fk.setKeyword(f.getKeyword().trim());
            fk.setCategory(null); // review_standard_focus 无分类列
            result.add(fk);
        }
        return result;
    }

    /** 关注分类：从 AI 提取的 focusItems 里取去重分类（前端过滤按钮用） */
    private List<String> deriveFocusCategories(List<ReviewToolResultVo.FocusItem> focusItems) {
        if (CollUtil.isEmpty(focusItems)) {
            return new ArrayList<>();
        }
        return focusItems.stream()
            .map(ReviewToolResultVo.FocusItem::getCategory)
            .filter(StringUtils::isNotBlank)
            .distinct()
            .collect(Collectors.toList());
    }

    private String firstNonBlank(String a, String b) {
        return StringUtils.isNotBlank(a) ? a : b;
    }
}
