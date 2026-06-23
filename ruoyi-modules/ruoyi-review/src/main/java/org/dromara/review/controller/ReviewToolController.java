package org.dromara.review.controller;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.web.core.BaseController;
import org.dromara.review.domain.ReviewStandardRule;
import org.dromara.review.domain.vo.ReviewToolResultVo;
import org.dromara.review.service.IReviewToolService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI 审核工具（附件对比 / 内容审查）。
 * <p>
 * 聚合结果给前端「附件对比 / 内容审查」查看器消费；建任务复用 {@code /review/task/createAndExecute}。
 *
 * @author Linson
 * @date 2026-06-23
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/review/tool")
public class ReviewToolController extends BaseController {

    private final IReviewToolService reviewToolService;

    /**
     * 聚合工具审核结果（文件URL/searchable/OCR状态/问题明细/关注列表）。前端轮询此接口。
     */
    @GetMapping("/result/{taskId}")
    public R<ReviewToolResultVo> result(@NotNull(message = "任务ID不能为空") @PathVariable Long taskId) {
        return R.ok(reviewToolService.getToolResult(taskId));
    }

    /**
     * 保存附件对比差异清单批注（COMPARE）。
     */
    @Log(title = "工具-对比批注", businessType = BusinessType.UPDATE)
    @PostMapping("/compareNote")
    public R<Void> saveCompareNote(@RequestBody Map<String, String> body) {
        String taskId = body.get("taskId");
        if (taskId == null || taskId.isBlank()) {
            return R.fail("taskId 不能为空");
        }
        reviewToolService.saveCompareNote(Long.valueOf(taskId), body.get("noteData"));
        return R.ok();
    }

    /**
     * 保存单条问题批注（按 taskId + fieldName 定位）。
     */
    @Log(title = "工具-问题批注", businessType = BusinessType.UPDATE)
    @PostMapping("/issueNote")
    public R<Void> saveIssueNote(@RequestBody Map<String, String> body) {
        String taskId = body.get("taskId");
        if (taskId == null || taskId.isBlank()) {
            return R.fail("taskId 不能为空");
        }
        reviewToolService.saveIssueNote(Long.valueOf(taskId), body.get("fieldName"), body.get("note"));
        return R.ok();
    }

    /**
     * 查询附件 OCR 状态（查看器轮询；状态为空的 PDF 会懒触发）。
     */
    @GetMapping("/ocr/status")
    public R<Map<String, Object>> ocrStatus(@RequestParam Long ossId) {
        return R.ok(reviewToolService.getOcrStatus(ossId));
    }

    /**
     * 查询某任务实际使用的规则库（查看器「查看规则」用）。
     */
    @GetMapping("/rules/{taskId}")
    public R<List<ReviewStandardRule>> rules(@NotNull(message = "任务ID不能为空") @PathVariable Long taskId) {
        return R.ok(reviewToolService.getToolRules(taskId));
    }

    /**
     * Word(doc/docx) 转 PDF（附件对比/审查上传 Word 时，转 PDF 供查看器字符级 diff）。
     * 已是 PDF/非 Word 原样返回。返回 {ossId,url,name,converted}。
     */
    @Log(title = "工具-Word转PDF", businessType = BusinessType.OTHER)
    @PostMapping("/convertToPdf")
    public R<Map<String, Object>> convertToPdf(@RequestParam Long ossId) {
        return R.ok(reviewToolService.convertWordToPdf(ossId));
    }

    /**
     * 手动触发附件 OCR（查看器自愈）。
     */
    @PostMapping("/ocr/trigger")
    public R<Void> ocrTrigger(@RequestParam Long ossId) {
        reviewToolService.triggerOcrByOssId(ossId);
        return R.ok();
    }
}
