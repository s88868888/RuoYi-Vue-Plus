package org.dromara.resource.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.ai.service.AiChatService;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.web.core.BaseController;
import org.dromara.resource.domain.vo.BizSubmissionChapterVo;
import org.dromara.resource.service.IBizSubmissionChapterService;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 标书章节管理Controller
 *
 * @author ruoyi
 * @date 2026-02-26
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/bid/submission/chapter")
public class BizSubmissionChapterController extends BaseController {

    private final IBizSubmissionChapterService chapterService;
    private final AiChatService aiChatService;

    /**
     * 获取章节树
     *
     * @param submissionId 投标项目ID
     * @param documentId 文档ID
     */
    @SaCheckPermission("bid:submission:query")
    @GetMapping("/tree")
    public R<List<BizSubmissionChapterVo>> getChapterTree(
        @RequestParam(required = false) Long submissionId,
        @RequestParam(required = false) Long documentId) {
        return R.ok(chapterService.getChapterTree(submissionId, documentId));
    }

    /**
     * 获取章节详情
     *
     * @param id 章节ID
     */
    @SaCheckPermission("bid:submission:query")
    @GetMapping("/{id}")
    public R<BizSubmissionChapterVo> getInfo(@NotNull(message = "章节ID不能为空") @PathVariable Long id) {
        return R.ok(chapterService.queryById(id));
    }

    /**
     * 生成章节内容
     *
     * @param id 章节ID
     */
    @SaCheckPermission("bid:submission:generate")
    @Log(title = "标书章节", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/generate")
    public R<Void> generateChapter(@NotNull(message = "章节ID不能为空") @PathVariable Long id) {
        chapterService.generateChapter(id);
        return R.ok();
    }

    /**
     * 填充模板章节
     *
     * @param id 章节ID
     */
    @SaCheckPermission("bid:submission:generate")
    @Log(title = "标书章节", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/fill")
    public R<Void> fillTemplate(@NotNull(message = "章节ID不能为空") @PathVariable Long id) {
        chapterService.fillTemplate(id);
        return R.ok();
    }

    /**
     * 保存章节内容
     *
     * @param id 章节ID
     * @param content 章节内容
     */
    @SaCheckPermission("bid:submission:edit")
    @Log(title = "标书章节", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> saveChapter(
        @RequestParam Long id,
        @RequestParam String content) {
        chapterService.saveChapterContent(id, content);
        return R.ok();
    }

    /**
     * 重新生成章节
     *
     * @param id 章节ID
     */
    @SaCheckPermission("bid:submission:generate")
    @Log(title = "标书章节", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/regenerate")
    public R<Void> regenerateChapter(@NotNull(message = "章节ID不能为空") @PathVariable Long id) {
        chapterService.regenerateChapter(id);
        return R.ok();
    }

    /**
     * 删除章节
     *
     * @param id 章节ID
     */
    @SaCheckPermission("bid:submission:remove")
    @Log(title = "标书章节", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public R<Void> deleteChapter(@NotNull(message = "章节ID不能为空") @PathVariable Long id) {
        return toAjax(chapterService.deleteById(id));
    }

    /**
     * 清空文档下所有章节
     *
     * @param submissionId 投标项目ID
     * @param documentId   文档配置ID
     */
    @SaCheckPermission("bid:submission:remove")
    @Log(title = "清空章节目录", businessType = BusinessType.DELETE)
    @DeleteMapping("/clear")
    public R<Void> clearChapters(
        @RequestParam @NotNull(message = "投标项目ID不能为空") Long submissionId,
        @RequestParam @NotNull(message = "文档配置ID不能为空") Long documentId) {
        chapterService.clearChapters(submissionId, documentId);
        return R.ok();
    }

    /**
     * 添加章节
     *
     * @param submissionDocumentId 文档ID
     * @param parentId 父章节ID
     * @param chapterTitle 章节标题
     * @param chapterType 章节类型
     * @param reasonDescription 原因说明
     */
    @SaCheckPermission("bid:submission:add")
    @Log(title = "标书章节", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> addChapter(
        @RequestParam Long submissionDocumentId,
        @RequestParam Long parentId,
        @RequestParam String chapterTitle,
        @RequestParam String chapterType,
        @RequestParam(required = false) String reasonDescription) {
        chapterService.addChapter(submissionDocumentId, parentId, chapterTitle, chapterType, reasonDescription);
        return R.ok();
    }

    /**
     * AI 生成章节结构
     *
     * @param submissionId 投标项目ID
     * @param documentConfigId 文档配置ID
     */
    @SaCheckPermission("bid:submission:generate")
    @Log(title = "AI生成章节结构", businessType = BusinessType.INSERT)
    @PostMapping("/generate-structure")
    public R<Void> generateStructure(
        @RequestParam @NotNull(message = "投标项目ID不能为空") Long submissionId,
        @RequestParam @NotNull(message = "文档配置ID不能为空") Long documentConfigId) {
        chapterService.generateChapterStructure(submissionId, documentConfigId);
        return R.ok();
    }

    /**
     * 重新生成章节结构
     *
     * @param submissionId 投标项目ID
     * @param documentConfigId 文档配置ID
     */
    @SaCheckPermission("bid:submission:generate")
    @Log(title = "重新生成章节结构", businessType = BusinessType.UPDATE)
    @PostMapping("/regenerate-structure")
    public R<Void> regenerateStructure(
        @RequestParam @NotNull(message = "投标项目ID不能为空") Long submissionId,
        @RequestParam @NotNull(message = "文档配置ID不能为空") Long documentConfigId) {
        chapterService.regenerateChapterStructure(submissionId, documentConfigId);
        return R.ok();
    }

    /**
     * 批量更新章节排序
     *
     * @param sortItems 排序列表，每项包含 id、parentId、sortOrder
     */
    @SaCheckPermission("bid:submission:edit")
    @Log(title = "标书章节排序", businessType = BusinessType.UPDATE)
    @PutMapping("/sort")
    public R<Void> updateSort(@RequestBody List<Map<String, Object>> sortItems) {
        chapterService.updateChapterSort(sortItems);
        return R.ok();
    }

    /**
     * AI 辅助写作接口（供 AiEditor 富文本编辑器调用，SSE 流式返回）
     *
     * @param body 包含 prompt 字段的请求体
     */
    @PostMapping(value = "/ai/assist", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter aiAssist(@RequestBody Map<String, String> body) {
        String prompt = body.getOrDefault("prompt", "");
        SseEmitter emitter = new SseEmitter(60_000L);

        // 异步调用 AI，避免阻塞 HTTP 线程
        CompletableFuture.runAsync(() -> {
            try {
                String result = aiChatService.chat(
                    "你是一位专业的标书写作助手，请根据用户指令优化或续写内容，使用规范的商务中文，保持专业性。",
                    prompt
                );
                emitter.send(SseEmitter.event().name("message").data(result));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

}
