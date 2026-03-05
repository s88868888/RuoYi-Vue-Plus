package org.dromara.resource.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.ai.domain.VectorSearchResult;
import org.dromara.common.ai.service.AiChatService;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.common.web.core.BaseController;
import org.dromara.resource.domain.BizBidSubmission;
import org.dromara.resource.domain.BizSubmissionChapter;
import org.dromara.resource.domain.vo.BizAiPromptTemplateVo;
import org.dromara.resource.domain.vo.BizSubmissionChapterVo;
import org.dromara.resource.mapper.BizBidSubmissionMapper;
import org.dromara.resource.mapper.BizSubmissionChapterMapper;
import org.dromara.resource.service.BidDocumentVectorService;
import org.dromara.resource.service.IBizAiPromptTemplateService;
import org.dromara.resource.service.IBizSubmissionChapterService;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static cn.dev33.satoken.SaManager.log;

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
    private final BidDocumentVectorService bidDocumentVectorService;
    private final BizSubmissionChapterMapper chapterMapper;
    private final BizBidSubmissionMapper submissionMapper;
    private final IBizAiPromptTemplateService promptTemplateService;

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
     * 修改章节类型（template/generate）
     *
     * @param id 章节ID
     * @param chapterType 章节类型
     */
    @SaCheckPermission("bid:submission:edit")
    @Log(title = "标书章节", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/type")
    public R<Void> updateChapterType(
        @NotNull(message = "章节ID不能为空") @PathVariable Long id,
        @RequestParam String chapterType) {
        chapterService.updateChapterType(id, chapterType);
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
        @RequestBody Map<String, String> body) {
        chapterService.saveChapterContent(id, body.get("content"));
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
     * 一键生成所有章节内容
     *
     * @param submissionId     投标项目ID
     * @param documentConfigId 文档配置ID
     */
    @SaCheckPermission("bid:submission:generate")
    @Log(title = "一键生成章节内容", businessType = BusinessType.UPDATE)
    @PostMapping("/generate-all")
    public R<Void> generateAllChapters(
        @RequestParam @NotNull(message = "投标项目ID不能为空") Long submissionId,
        @RequestParam @NotNull(message = "文档配置ID不能为空") Long documentConfigId) {
        chapterService.generateAllChapters(submissionId, documentConfigId);
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
     * @param body 包含 action、prompt、chapterId 字段的请求体
     */
    @PostMapping(value = "/ai/assist", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter aiAssist(@RequestBody Map<String, String> body) {
        String action = body.getOrDefault("action", "polish");
        String userText = body.getOrDefault("prompt", "");
        String chapterIdStr = body.get("chapterId");
        // 在请求线程（Sa-Token 上下文可用）提前解析租户 ID，再传入异步线程
        String tenantId = TenantHelper.getTenantId();

        SseEmitter emitter = new SseEmitter(120_000L);

        CompletableFuture.runAsync(() -> {
            // 异步线程无 Sa-Token HTTP 上下文，通过 TenantHelper.dynamic 手动注入租户
            TenantHelper.dynamic(tenantId, () -> {
                try {
                    // 1. 从数据库读取系统提示词模板
                    String templateType = "editor_" + action;
                    List<BizAiPromptTemplateVo> templates = promptTemplateService.queryByType(templateType);
                    String systemPrompt = templates.isEmpty()
                        ? "你是一位专业的标书写作助手，请根据用户指令优化内容，使用规范商务中文，保持专业性，输出HTML格式。"
                        : templates.get(0).getPromptContent();

                    // 2. RAG：通过 chapterId 查找知识库
                    StringBuilder context = new StringBuilder();
                    if (chapterIdStr != null && !chapterIdStr.isBlank()) {
                        try {
                            Long chapterId = Long.parseLong(chapterIdStr);
                            BizSubmissionChapter chapter = chapterMapper.selectById(chapterId);
                            if (chapter != null) {
                                BizBidSubmission submission = submissionMapper.selectById(chapter.getBidSubmissionId());
                                if (submission != null) {
                                    Long bidProjectId = submission.getBidProjectId();
                                    List<VectorSearchResult> results = bidDocumentVectorService
                                        .search(tenantId, bidProjectId, userText, null, 5);
                                    if (!results.isEmpty()) {
                                        context.append("【招标文件参考内容】\n");
                                        for (VectorSearchResult r : results) {
                                            // 过滤掉 URL 和临时文件名，避免 DashScope 将其当作多模态资源解析报错
                                            String content = r.getContent()
                                                .replaceAll("https?://\\S+", "[链接已省略]")
                                                .replaceAll("\\w+\\.tmp", "[文件已省略]")
                                                .replaceAll("《[^》]*\\.tmp[^》]*》", "[文件已省略]");
                                            context.append(content).append("\n---\n");
                                        }
                                        context.append("\n");
                                    }
                                }
                            }
                        } catch (NumberFormatException ignored) {
                            // chapterId 格式不合法时忽略 RAG，正常降级
                        }
                    }

                    // 3. 拼装用户消息
                    String finalUserMessage = context.isEmpty()
                        ? userText
                        : context + "【待处理文本】\n" + userText;

                    // 4. 调用 AI
                    log.info("[aiAssist] systemPrompt={}", systemPrompt);
                    log.info("[aiAssist] finalUserMessage=", finalUserMessage);
                    String result = aiChatService.chat(systemPrompt, finalUserMessage);
                    emitter.send(SseEmitter.event().name("message").data(result));
                    emitter.complete();
                } catch (Exception e) {
                    emitter.completeWithError(e);
                }
            });
        });

        return emitter;
    }

}
