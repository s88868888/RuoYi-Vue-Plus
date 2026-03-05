package org.dromara.resource.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.web.core.BaseController;
import org.dromara.resource.domain.vo.BizSubmissionDocumentVo;
import org.dromara.resource.service.IBizSubmissionDocumentService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 标书文档版本控制器
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/bid/document")
public class BizSubmissionDocumentController extends BaseController {

    private final IBizSubmissionDocumentService submissionDocumentService;

    /**
     * 查询投标项目最新版本文档列表
     */
    @SaCheckPermission("bid:submission:query")
    @GetMapping("/latest/{submissionId}")
    public R<List<BizSubmissionDocumentVo>> latestBySubmissionId(
        @NotNull(message = "投标项目ID不能为空") @PathVariable Long submissionId) {
        return R.ok(submissionDocumentService.listLatestBySubmissionId(submissionId));
    }

    /**
     * 查询投标项目所有版本文档列表
     */
    @SaCheckPermission("bid:submission:query")
    @GetMapping("/all/{submissionId}")
    public R<List<BizSubmissionDocumentVo>> allBySubmissionId(
        @NotNull(message = "投标项目ID不能为空") @PathVariable Long submissionId) {
        return R.ok(submissionDocumentService.listAllBySubmissionId(submissionId));
    }

    /**
     * 查询某文档配置下历史版本
     */
    @SaCheckPermission("bid:submission:query")
    @GetMapping("/versions/{documentConfigId}")
    public R<List<BizSubmissionDocumentVo>> versionsByConfigId(
        @NotNull(message = "文档配置ID不能为空") @PathVariable Long documentConfigId) {
        return R.ok(submissionDocumentService.listVersionsByConfigId(documentConfigId));
    }

    /**
     * 将所有章节合并为一份完整文档并保存为新版本
     */
    @SaCheckPermission("bid:submission:edit")
    @Log(title = "归纳完整标书版本", businessType = BusinessType.INSERT)
    @PostMapping("/saveAllVersion/{submissionId}")
    public R<BizSubmissionDocumentVo> saveAllVersion(
        @NotNull(message = "投标项目ID不能为空") @PathVariable Long submissionId) {
        return R.ok(submissionDocumentService.saveAllVersion(submissionId));
    }

    /**
     * 保存当前章节快照为新版本
     */
    @SaCheckPermission("bid:submission:edit")
    @Log(title = "保存标书版本", businessType = BusinessType.INSERT)
    @PostMapping("/saveVersion/{documentConfigId}")
    public R<BizSubmissionDocumentVo> saveVersion(
        @NotNull(message = "文档配置ID不能为空") @PathVariable Long documentConfigId,
        @NotNull(message = "投标项目ID不能为空") @RequestParam Long submissionId) {
        return R.ok(submissionDocumentService.saveVersion(documentConfigId, submissionId));
    }

    /**
     * 导出文档
     */
    @SaCheckPermission("bid:submission:export")
    @Log(title = "导出标书文档", businessType = BusinessType.EXPORT)
    @GetMapping("/export/{documentId}")
    public void exportDocument(
        @NotNull(message = "文档ID不能为空") @PathVariable Long documentId,
        @NotBlank(message = "导出格式不能为空") @RequestParam String format,
        HttpServletResponse response) {
        submissionDocumentService.exportDocument(documentId, format, response);
    }
}
