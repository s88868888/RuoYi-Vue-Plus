package org.dromara.resource.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.ai.service.AiChatService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.SpringUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.common.sse.utils.SseMessageUtils;
import org.dromara.resource.domain.BizBidProject;
import org.dromara.resource.domain.BizBidSubmission;
import org.dromara.resource.domain.BizDocumentConfig;
import org.dromara.resource.domain.BizSubmissionChapter;
import org.dromara.resource.domain.vo.BizSubmissionChapterVo;
import org.dromara.resource.mapper.BizBidProjectMapper;
import org.dromara.resource.mapper.BizBidSubmissionMapper;
import org.dromara.resource.mapper.BizDocumentConfigMapper;
import org.dromara.resource.mapper.BizSubmissionChapterMapper;
import org.dromara.resource.service.IBizSubmissionChapterService;
import org.dromara.system.domain.SysOss;
import org.dromara.system.mapper.SysOssMapper;
import org.springframework.core.io.UrlResource;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 标书章节Service业务层处理
 *
 * @author ruoyi
 * @date 2026-02-26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BizSubmissionChapterServiceImpl implements IBizSubmissionChapterService {

    private final BizSubmissionChapterMapper baseMapper;
    private final BizBidSubmissionMapper submissionMapper;
    private final BizBidProjectMapper projectMapper;
    private final BizDocumentConfigMapper documentConfigMapper;
    private final SysOssMapper sysOssMapper;
    private final AiChatService aiChatService;

    @Override
    public BizSubmissionChapterVo queryById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    public List<BizSubmissionChapterVo> getChapterTree(Long submissionId, Long documentId) {
        LambdaQueryWrapper<BizSubmissionChapter> lqw = Wrappers.lambdaQuery();
        if (submissionId != null) {
            lqw.eq(BizSubmissionChapter::getBidSubmissionId, submissionId);
        }
        if (documentId != null) {
            lqw.eq(BizSubmissionChapter::getSubmissionDocumentId, documentId);
        }
        lqw.orderByAsc(BizSubmissionChapter::getSortOrder);
        List<BizSubmissionChapterVo> all = baseMapper.selectVoList(lqw);
        return buildTree(all, 0L);
    }

    private List<BizSubmissionChapterVo> buildTree(List<BizSubmissionChapterVo> all, Long parentId) {
        return all.stream()
            .filter(item -> parentId.equals(item.getParentId()))
            .peek(item -> item.setChildren(buildTree(all, item.getId())))
            .collect(Collectors.toList());
    }

    @Override
    public void generateChapter(Long id) {
        BizSubmissionChapter chapter = baseMapper.selectById(id);
        if (chapter == null) {
            return;
        }
        Long userId = LoginHelper.getUserId();
        String tenantId = TenantHelper.getTenantId();
        SpringUtils.getBean(IBizSubmissionChapterService.class)
            .doGenerateChapterContent(id, userId, tenantId);
    }

    @Override
    public void fillTemplate(Long id) {
        BizSubmissionChapter chapter = baseMapper.selectById(id);
        if (chapter == null) {
            return;
        }
        chapter.setGenerationStatus("generating");
        chapter.setGenerationProgress(0);
        baseMapper.updateById(chapter);
        log.info("开始填充模板章节，章节ID: {}", id);
    }

    @Override
    public void saveChapterContent(Long id, String content) {
        BizSubmissionChapter chapter = baseMapper.selectById(id);
        if (chapter == null) {
            return;
        }
        chapter.setChapterContent(content);
        chapter.setGenerationStatus("completed");
        chapter.setGenerationProgress(100);
        baseMapper.updateById(chapter);
    }

    @Override
    public void regenerateChapter(Long id) {
        BizSubmissionChapter chapter = baseMapper.selectById(id);
        if (chapter == null) {
            return;
        }
        chapter.setChapterContent(null);
        chapter.setErrorMessage(null);
        chapter.setGenerationStatus("pending");
        chapter.setGenerationProgress(0);
        baseMapper.updateById(chapter);
        log.info("清空章节内容，准备重新生成，章节ID: {}", id);
        generateChapter(id);
    }

    @Override
    @Async
    public void doGenerateChapterContent(Long chapterId, Long userId, String tenantId) {
        TenantHelper.setDynamic(tenantId);
        try {
            // 1. 查询章节信息
            BizSubmissionChapter chapter = baseMapper.selectById(chapterId);
            if (chapter == null) {
                log.warn("章节不存在，chapterId: {}", chapterId);
                return;
            }

            // 2. 更新状态为生成中，发送 SSE
            chapter.setGenerationStatus("generating");
            chapter.setGenerationProgress(0);
            chapter.setGenerationStartTime(new Date());
            baseMapper.updateById(chapter);
            SseMessageUtils.sendMessage(userId, buildChapterSseMessage("chapter_start", chapterId, "开始生成章节内容: " + chapter.getChapterTitle(), 0));

            // 3. 查询关联信息：submission → project → 招标文件 URL
            BizBidSubmission submission = submissionMapper.selectById(chapter.getBidSubmissionId());
            if (submission == null) {
                markChapterFailed(chapter, userId, "投标项目不存在");
                return;
            }
            BizBidProject project = projectMapper.selectById(submission.getBidProjectId());
            if (project == null) {
                markChapterFailed(chapter, userId, "招标项目不存在");
                return;
            }
            if (StrUtil.isBlank(project.getAttachments())) {
                markChapterFailed(chapter, userId, "招标项目没有上传招标文件");
                return;
            }

            // 获取招标文件 URL
            String[] attachmentIds = project.getAttachments().split(",");
            Long ossId = Long.parseLong(attachmentIds[0].trim());
            SysOss sysOss = sysOssMapper.selectById(ossId);
            if (sysOss == null || StrUtil.isBlank(sysOss.getUrl())) {
                markChapterFailed(chapter, userId, "招标文件URL无效");
                return;
            }
            String fileUrl = sysOss.getUrl();

            // 4. 根据章节类型选择生成策略（template: 提取原文格式，generate: AI自由生成）
            boolean isTemplateChapter = "template".equals(chapter.getChapterType());
            String content;
            String prompt;

            if (isTemplateChapter) {
                // 尝试从招标文件提取规定格式原文
                prompt = buildTemplateExtractPrompt(chapter, project, submission);
                SseMessageUtils.sendMessage(userId, buildChapterSseMessage("chapter_start", chapterId, "正在提取招标文件规定格式...", 30));
                content = aiChatService.chatWithDocumentUrl(fileUrl, prompt);

                // 提取失败时自动降级为 AI 生成
                if (StrUtil.isBlank(content)) {
                    log.warn("模板提取为空，自动降级为AI生成，chapterId: {}, title: {}", chapterId, chapter.getChapterTitle());
                    chapter.setChapterType("generate");
                    baseMapper.updateById(chapter);
                    SseMessageUtils.sendMessage(userId, buildChapterSseMessage("chapter_start", chapterId, "未提取到规定格式，改为AI生成...", 50));
                    prompt = buildChapterContentPrompt(chapter, project, submission);
                    content = aiChatService.chatWithDocumentUrl(fileUrl, prompt);
                }
            } else {
                prompt = buildChapterContentPrompt(chapter, project, submission);
                SseMessageUtils.sendMessage(userId, buildChapterSseMessage("chapter_start", chapterId, "AI正在生成章节内容...", 30));
                content = aiChatService.chatWithDocumentUrl(fileUrl, prompt);
            }

            // 6. 保存内容，更新状态
            Date endTime = new Date();
            chapter.setChapterContent(content);
            chapter.setGenerationStatus("completed");
            chapter.setGenerationProgress(100);
            chapter.setGenerationEndTime(endTime);
            if (chapter.getGenerationStartTime() != null) {
                chapter.setGenerationDuration((int) ((endTime.getTime() - chapter.getGenerationStartTime().getTime()) / 1000));
            }
            chapter.setAiModel("qwen-long-latest");
            baseMapper.updateById(chapter);

            // 7. 发送成功 SSE
            SseMessageUtils.sendMessage(userId, buildChapterSseMessage("chapter_success", chapterId, "章节内容生成完成: " + chapter.getChapterTitle(), 100));
            log.info("章节内容生成完成，chapterId: {}, title: {}", chapterId, chapter.getChapterTitle());

        } catch (Exception e) {
            log.error("生成章节内容异常，chapterId: {}", chapterId, e);
            // 更新失败状态
            try {
                BizSubmissionChapter failChapter = baseMapper.selectById(chapterId);
                if (failChapter != null) {
                    markChapterFailed(failChapter, userId, "生成失败: " + e.getMessage());
                }
            } catch (Exception ex) {
                log.error("更新失败状态异常", ex);
            }
        } finally {
            TenantHelper.clearDynamic();
        }
    }

    @Override
    public void generateAllChapters(Long submissionId, Long documentConfigId) {
        Long userId = LoginHelper.getUserId();
        String tenantId = TenantHelper.getTenantId();
        SpringUtils.getBean(IBizSubmissionChapterService.class)
            .doGenerateAllChapters(submissionId, documentConfigId, userId, tenantId);
    }

    @Override
    @Async
    public void doGenerateAllChapters(Long submissionId, Long documentConfigId, Long userId, String tenantId) {
        TenantHelper.setDynamic(tenantId);
        try {
            // 1. 查询所有叶子章节（没有子节点的章节）
            LambdaQueryWrapper<BizSubmissionChapter> lqw = Wrappers.lambdaQuery();
            lqw.eq(BizSubmissionChapter::getBidSubmissionId, submissionId);
            lqw.eq(BizSubmissionChapter::getSubmissionDocumentId, documentConfigId);
            lqw.orderByAsc(BizSubmissionChapter::getSortOrder);
            List<BizSubmissionChapter> allChapters = baseMapper.selectList(lqw);

            // 找出叶子节点（parentId 不被其他节点引用的节点）
            java.util.Set<Long> parentIds = allChapters.stream()
                .map(BizSubmissionChapter::getParentId)
                .collect(Collectors.toSet());
            List<BizSubmissionChapter> leafChapters = allChapters.stream()
                .filter(c -> !parentIds.contains(c.getId()))
                .toList();

            int total = leafChapters.size();
            if (total == 0) {
                SseMessageUtils.sendMessage(userId, buildBatchSseMessage("batch_success", "没有需要生成的章节", 0, 0, 100, null));
                return;
            }

            // 2. 发送批量开始 SSE
            SseMessageUtils.sendMessage(userId, buildBatchSseMessage("batch_start", "开始批量生成章节内容", total, 0, 0, null));

            // 3. 查询关联信息（只查一次）
            BizBidSubmission submission = submissionMapper.selectById(submissionId);
            if (submission == null) {
                SseMessageUtils.sendMessage(userId, buildBatchSseMessage("batch_error", "投标项目不存在", 0, 0, 0, null));
                return;
            }
            BizBidProject project = projectMapper.selectById(submission.getBidProjectId());
            if (project == null) {
                SseMessageUtils.sendMessage(userId, buildBatchSseMessage("batch_error", "招标项目不存在", 0, 0, 0, null));
                return;
            }
            if (StrUtil.isBlank(project.getAttachments())) {
                SseMessageUtils.sendMessage(userId, buildBatchSseMessage("batch_error", "招标项目没有上传招标文件", 0, 0, 0, null));
                return;
            }

            String[] attachmentIds = project.getAttachments().split(",");
            Long ossId = Long.parseLong(attachmentIds[0].trim());
            SysOss sysOss = sysOssMapper.selectById(ossId);
            if (sysOss == null || StrUtil.isBlank(sysOss.getUrl())) {
                SseMessageUtils.sendMessage(userId, buildBatchSseMessage("batch_error", "招标文件URL无效", 0, 0, 0, null));
                return;
            }
            String fileUrl = sysOss.getUrl();

            // 4. 逐个生成（顺序执行，避免并发限制）
            int current = 0;
            for (BizSubmissionChapter chapter : leafChapters) {
                current++;
                int progress = (int) ((current * 100.0) / total);

                try {
                    // 发送进度 SSE
                    boolean isTemplate = "template".equals(chapter.getChapterType());
                    String progressMsg = isTemplate
                        ? "正在提取格式: " + chapter.getChapterTitle()
                        : "正在生成: " + chapter.getChapterTitle();
                    SseMessageUtils.sendMessage(userId, buildBatchSseMessage("batch_progress",
                        progressMsg, total, current, progress, chapter.getId()));

                    // 更新章节状态
                    chapter.setGenerationStatus("generating");
                    chapter.setGenerationProgress(0);
                    chapter.setGenerationStartTime(new Date());
                    baseMapper.updateById(chapter);

                    // 根据章节类型选择生成策略（template: 提取原文格式，generate: AI自由生成）
                    boolean isTemplateChapter = "template".equals(chapter.getChapterType());
                    String prompt;
                    String content;
                    if (isTemplateChapter) {
                        prompt = buildTemplateExtractPrompt(chapter, project, submission);
                        content = aiChatService.chatWithDocumentUrl(fileUrl, prompt);
                        if (StrUtil.isBlank(content)) {
                            log.warn("批量生成中模板提取为空，自动降级为AI生成，chapterId: {}, title: {}", chapter.getId(), chapter.getChapterTitle());
                            chapter.setChapterType("generate");
                            baseMapper.updateById(chapter);
                            prompt = buildChapterContentPrompt(chapter, project, submission);
                            content = aiChatService.chatWithDocumentUrl(fileUrl, prompt);
                        }
                    } else {
                        prompt = buildChapterContentPrompt(chapter, project, submission);
                        content = aiChatService.chatWithDocumentUrl(fileUrl, prompt);
                    }

                    // 保存内容
                    Date endTime = new Date();
                    chapter.setChapterContent(content);
                    chapter.setGenerationStatus("completed");
                    chapter.setGenerationProgress(100);
                    chapter.setGenerationEndTime(endTime);
                    if (chapter.getGenerationStartTime() != null) {
                        chapter.setGenerationDuration((int) ((endTime.getTime() - chapter.getGenerationStartTime().getTime()) / 1000));
                    }
                    chapter.setAiModel("qwen-long-latest");
                    baseMapper.updateById(chapter);

                    // 发送单章节成功 SSE
                    SseMessageUtils.sendMessage(userId, buildBatchSseMessage("batch_chapter_success",
                        "章节生成完成: " + chapter.getChapterTitle(), total, current, progress, chapter.getId()));

                } catch (Exception e) {
                    log.error("批量生成中章节失败, chapterId: {}", chapter.getId(), e);
                    markChapterFailed(chapter, userId, "生成失败: " + e.getMessage());
                    // 单个失败不影响后续章节
                }
            }

            // 5. 全部完成
            SseMessageUtils.sendMessage(userId, buildBatchSseMessage("batch_success", "全部章节生成完成", total, total, 100, null));
            log.info("批量章节内容生成完成，submissionId: {}, total: {}", submissionId, total);

        } catch (Exception e) {
            log.error("批量生成章节内容异常", e);
            SseMessageUtils.sendMessage(userId, buildBatchSseMessage("batch_error", "批量生成异常: " + e.getMessage(), 0, 0, 0, null));
        } finally {
            TenantHelper.clearDynamic();
        }
    }

    /**
     * 标记章节为失败状态
     */
    private void markChapterFailed(BizSubmissionChapter chapter, Long userId, String errorMsg) {
        chapter.setGenerationStatus("failed");
        chapter.setGenerationProgress(0);
        chapter.setErrorMessage(errorMsg);
        baseMapper.updateById(chapter);
        SseMessageUtils.sendMessage(userId, buildChapterSseMessage("chapter_error", chapter.getId(), errorMsg, 0));
    }

    /**
     * 构建章节内容生成的 prompt
     */
    private String buildChapterContentPrompt(BizSubmissionChapter chapter, BizBidProject project, BizBidSubmission submission) {
        return String.format("""
            请基于上传的招标文件，为以下标书章节生成详细、专业的内容。

            【项目信息】
            - 项目名称：%s
            - 招标单位：%s
            - 项目类型：%s
            - 预算金额：%s
            - 项目描述：%s

            【章节信息】
            - 章节编号：%s
            - 章节标题：%s
            - 章节层级：第%d级
            - 生成说明：%s

            【生成要求】
            1. 内容必须紧密结合招标文件中的具体要求
            2. 内容充实、逻辑清晰、语言规范
            3. 使用 HTML 格式输出（可使用 <h3>/<h4>/<p>/<ul>/<ol>/<li>/<table>/<tr>/<td>/<th>/<strong>/<em> 等标签）
            4. 包含必要的表格、列表等结构化内容
            5. 针对招标文件中的评分标准重点响应
            6. 字数不少于800字
            7. 不要包含章节标题本身（标题会自动添加）
            8. 不要输出 Markdown 格式，请使用 HTML 格式

            请直接输出章节内容。
            """,
            project.getProjectName(),
            project.getBidOrg(),
            project.getProjectType(),
            project.getBudgetAmount(),
            project.getProjectDesc() != null ? project.getProjectDesc() : "无",
            chapter.getChapterNo(),
            chapter.getChapterTitle(),
            chapter.getChapterLevel(),
            chapter.getReasonDescription() != null ? chapter.getReasonDescription() : "无"
        );
    }

    /**
     * 构建模板章节原文提取 prompt
     */
    private String buildTemplateExtractPrompt(BizSubmissionChapter chapter, BizBidProject project, BizBidSubmission submission) {
        return String.format("""
            请从上传的招标文件中，提取"%s"（章节编号：%s）的规定格式原文。

            【项目信息】
            - 项目名称：%s
            - 招标单位：%s
            - 项目类型：%s
            - 预算金额：%s

            【目标章节】
            - 章节编号：%s
            - 章节标题：%s
            - 章节层级：第%d级

            【提取要求（必须严格遵守）】
            1. 必须直接复制招标文件中的原始格式内容，不得改写、扩写、润色
            2. 保留原文中的固定文本、序号、下划线、空白框、日期位、签章位
            3. 保留原有表格结构（使用 HTML table 标签输出）
            4. 若章节包含“格式一/格式二/附表/模板/范本/样式”，需完整输出对应内容
            5. 仅输出该章节的格式正文，不要输出章节标题，不要输出解释性文字
            6. 输出格式必须是 HTML（可使用 <p>/<ul>/<ol>/<li>/<table>/<tr>/<td>/<th>/<strong>/<em> 等标签）
            7. 若未找到对应规定格式，返回空字符串

            请直接输出提取结果。
            """,
            chapter.getChapterTitle(),
            chapter.getChapterNo(),
            project.getProjectName(),
            project.getBidOrg(),
            project.getProjectType(),
            project.getBudgetAmount(),
            chapter.getChapterNo(),
            chapter.getChapterTitle(),
            chapter.getChapterLevel()
        );
    }

    /**
     * 构建章节内容 SSE 消息（单章节）
     */
    private String buildChapterSseMessage(String type, Long chapterId, String message, int progress) {
        JSONObject json = new JSONObject();
        json.set("type", type);
        json.set("chapterId", chapterId);
        json.set("message", message);
        json.set("progress", progress);
        return json.toString();
    }

    /**
     * 构建批量生成 SSE 消息
     */
    private String buildBatchSseMessage(String type, String message, int total, int current, int progress, Long chapterId) {
        JSONObject json = new JSONObject();
        json.set("type", type);
        json.set("message", message);
        json.set("total", total);
        json.set("current", current);
        json.set("progress", progress);
        if (chapterId != null) {
            json.set("chapterId", chapterId);
        }
        return json.toString();
    }

    @Override
    public void updateChapterType(Long id, String chapterType) {
        if (!"template".equals(chapterType) && !"generate".equals(chapterType)) {
            throw new ServiceException("章节类型非法，仅支持 template/generate");
        }
        BizSubmissionChapter chapter = baseMapper.selectById(id);
        if (chapter == null) {
            return;
        }
        chapter.setChapterType(chapterType);
        baseMapper.updateById(chapter);
    }

    @Override
    public Boolean deleteById(Long id) {
        return baseMapper.deleteById(id) > 0;
    }

    @Override
    public void addChapter(Long submissionDocumentId, Long parentId, String chapterTitle, String chapterType, String reasonDescription) {
        BizSubmissionChapter chapter = new BizSubmissionChapter();
        chapter.setSubmissionDocumentId(submissionDocumentId);
        chapter.setParentId(parentId);
        chapter.setChapterTitle(chapterTitle);
        chapter.setChapterType(chapterType);
        chapter.setReasonDescription(reasonDescription);
        chapter.setGenerationStatus("pending");
        chapter.setGenerationProgress(0);
        chapter.setSortOrder(0);
        chapter.setChapterLevel(1);
        baseMapper.insert(chapter);
    }

    @Override
    public void generateChapterStructure(Long submissionId, Long documentConfigId) {
        // 获取当前用户ID和租户ID，传入异步方法（异步线程无安全上下文）
        Long userId = LoginHelper.getUserId();
        String tenantId = TenantHelper.getTenantId();
        // 异步执行生成任务
        SpringUtils.getBean(IBizSubmissionChapterService.class)
            .doGenerateChapterStructure(submissionId, documentConfigId, userId, tenantId);
    }

    @Override
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void doGenerateChapterStructure(Long submissionId, Long documentConfigId, Long userId, String tenantId) {
        // 在异步线程中设置租户上下文，确保数据写入正确的租户
        TenantHelper.setDynamic(tenantId);
        try {
            log.info("开始生成章节结构，投标项目ID: {}, 文档配置ID: {}", submissionId, documentConfigId);

            // 标记为生成中（进度0），刷新页面可感知状态
            updateSubmissionProgress(submissionId, 0, 2);

            // 推送开始消息
            SseMessageUtils.sendMessage(userId, buildSseMessage("start", "开始生成章节结构", 0, null));

            // 1. 获取投标项目信息
            log.info("查询投标项目，submissionId: {}", submissionId);
            updateSubmissionProgress(submissionId, 10, 2);
            SseMessageUtils.sendMessage(userId, buildSseMessage("progress", "正在加载项目信息", 10, null));

            BizBidSubmission submission = submissionMapper.selectById(submissionId);
            log.info("查询结果: {}", submission);
            if (submission == null) {
                updateSubmissionProgress(submissionId, 0, 0);
                SseMessageUtils.sendMessage(userId, buildSseMessage("error", "投标项目不存在", 0, null));
                return;
            }

            // 2. 获取文档配置
            BizDocumentConfig documentConfig = documentConfigMapper.selectById(documentConfigId);
            if (documentConfig == null) {
                updateSubmissionProgress(submissionId, 0, 0);
                SseMessageUtils.sendMessage(userId, buildSseMessage("error", "文档配置不存在", 0, null));
                return;
            }

            // 3. 获取招标项目信息
            updateSubmissionProgress(submissionId, 20, 2);
            SseMessageUtils.sendMessage(userId, buildSseMessage("progress", "正在加载招标项目信息", 20, null));
            BizBidProject project = projectMapper.selectById(submission.getBidProjectId());
            if (project == null) {
                updateSubmissionProgress(submissionId, 0, 0);
                SseMessageUtils.sendMessage(userId, buildSseMessage("error", "招标项目不存在", 0, null));
                return;
            }

            // 4. 获取招标文件附件
            updateSubmissionProgress(submissionId, 30, 2);
            SseMessageUtils.sendMessage(userId, buildSseMessage("progress", "正在加载招标文件", 30, null));
            if (StrUtil.isBlank(project.getAttachments())) {
                updateSubmissionProgress(submissionId, 0, 0);
                SseMessageUtils.sendMessage(userId, buildSseMessage("error", "招标项目没有上传招标文件", 0, null));
                return;
            }

            // 从 attachments 字段获取附件ID（可能是逗号分隔的多个ID）
            String[] attachmentIds = project.getAttachments().split(",");
            if (attachmentIds.length == 0) {
                updateSubmissionProgress(submissionId, 0, 0);
                SseMessageUtils.sendMessage(userId, buildSseMessage("error", "招标项目没有上传招标文件", 0, null));
                return;
            }

            // 获取第一个附件的文件URL
            Long ossId = Long.parseLong(attachmentIds[0].trim());
            SysOss sysOss = sysOssMapper.selectById(ossId);
            if (sysOss == null) {
                updateSubmissionProgress(submissionId, 0, 0);
                SseMessageUtils.sendMessage(userId, buildSseMessage("error", "招标文件不存在", 0, null));
                return;
            }

            String fileUrl = sysOss.getUrl();
            if (StrUtil.isBlank(fileUrl)) {
                updateSubmissionProgress(submissionId, 0, 0);
                SseMessageUtils.sendMessage(userId, buildSseMessage("error", "招标文件URL为空", 0, null));
                return;
            }

            // 5. 构建 AI 提示词
            updateSubmissionProgress(submissionId, 40, 2);
            SseMessageUtils.sendMessage(userId, buildSseMessage("progress", "正在调用AI生成章节结构", 40, null));
            String prompt = buildChapterGenerationPrompt(project, documentConfig);

            // 6. 调用 qwen-long 生成章节结构
            String aiResponse;
            try {
                aiResponse = aiChatService.chatWithDocumentUrl(fileUrl, prompt);
                updateSubmissionProgress(submissionId, 70, 2);
                SseMessageUtils.sendMessage(userId, buildSseMessage("progress", "AI生成完成，正在解析结果", 70, null));
            } catch (Exception e) {
                log.error("AI 生成章节结构失败", e);
                updateSubmissionProgress(submissionId, 0, 0);
                SseMessageUtils.sendMessage(userId, buildSseMessage("error", "AI 生成章节结构失败: " + e.getMessage(), 0, null));
                return;
            }

            // 7. 解析 AI 返回的 JSON 并插入数据库（递归插入，自动处理父子关系）
            updateSubmissionProgress(submissionId, 80, 2);
            SseMessageUtils.sendMessage(userId, buildSseMessage("progress", "正在保存章节结构", 80, null));
            parseChapterJson(aiResponse, submissionId, documentConfigId);

            log.info("章节结构保存完成，准备发送成功消息");

            // 8. 在事务提交后发送成功消息
            // 使用 TransactionSynchronizationManager 确保消息在事务提交后发送
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    log.info("事务已提交，发送成功消息");
                    // 标记章节结构已生成完成（chapter_structure_generated=1，progress=100）
                    updateSubmissionProgress(submissionId, 100, 1);
                    SseMessageUtils.sendMessage(userId, buildSseMessage("success", "章节结构生成完成", 100, null));
                }
            });

        } catch (Exception e) {
            log.error("生成章节结构异常", e);
            updateSubmissionProgress(submissionId, 0, 0);
            SseMessageUtils.sendMessage(userId, buildSseMessage("error", "生成章节结构异常: " + e.getMessage(), 0, null));
        } finally {
            TenantHelper.clearDynamic();
        }
    }

    /**
     * 更新投标项目的章节生成进度
     * @param submissionId 投标项目ID
     * @param progress 进度(0-100)
     * @param chapterStructureGenerated 章节结构状态: 0=未生成, 1=已生成, 2=生成中
     */
    private void updateSubmissionProgress(Long submissionId, int progress, int chapterStructureGenerated) {
        try {
            BizBidSubmission update = new BizBidSubmission();
            update.setId(submissionId);
            update.setGenerationProgress(progress);
            update.setChapterStructureGenerated(String.valueOf(chapterStructureGenerated));
            submissionMapper.updateById(update);
        } catch (Exception e) {
            log.warn("更新生成进度失败: {}", e.getMessage());
        }
    }

    /**
     * 构建SSE消息
     */
    private String buildSseMessage(String type, String message, int progress, String data) {
        JSONObject json = new JSONObject();
        json.set("type", type);
        json.set("message", message);
        json.set("progress", progress);
        json.set("data", data);
        return json.toString();
    }

    @Override
    public void regenerateChapterStructure(Long submissionId, Long documentConfigId) {
        log.info("重新生成章节结构，投标项目ID: {}, 文档配置ID: {}", submissionId, documentConfigId);

        // 1. 删除已有的章节
        LambdaQueryWrapper<BizSubmissionChapter> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(BizSubmissionChapter::getBidSubmissionId, submissionId);
        wrapper.eq(BizSubmissionChapter::getSubmissionDocumentId, documentConfigId);
        baseMapper.delete(wrapper);

        // 2. 重新生成
        generateChapterStructure(submissionId, documentConfigId);
    }

    /**
     * 批量更新章节排序
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateChapterSort(List<Map<String, Object>> sortItems) {
        for (Map<String, Object> item : sortItems) {
            // 支持 Number 和 String 类型的数字转换
            Long id = parseToLong(item.get("id"));
            Long parentId = parseToLong(item.get("parentId"));
            Integer sortOrder = parseToInteger(item.get("sortOrder"));
            Integer chapterLevel = parseToInteger(item.get("chapterLevel"));
            String chapterNo = item.get("chapterNo") instanceof String s ? s : null;
            if (id == null) continue;

            // 使用 LambdaUpdateWrapper 显式设置字段，绕过实体 updateStrategy
            var wrapper = Wrappers.lambdaUpdate(BizSubmissionChapter.class)
                .eq(BizSubmissionChapter::getId, id);
            if (parentId != null) wrapper.set(BizSubmissionChapter::getParentId, parentId);
            if (sortOrder != null) wrapper.set(BizSubmissionChapter::getSortOrder, sortOrder);
            if (chapterLevel != null) wrapper.set(BizSubmissionChapter::getChapterLevel, chapterLevel);
            if (chapterNo != null) wrapper.set(BizSubmissionChapter::getChapterNo, chapterNo);
            baseMapper.update(null, wrapper);
        }
    }

    /**
     * 将对象转换为 Long，支持 Number 和 String 类型
     */
    private Long parseToLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        } else if (value instanceof String s && !s.isEmpty()) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 将对象转换为 Integer，支持 Number 和 String 类型
     */
    private Integer parseToInteger(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        } else if (value instanceof String s && !s.isEmpty()) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearChapters(Long submissionId, Long documentId) {
        LambdaQueryWrapper<BizSubmissionChapter> lqw = Wrappers.lambdaQuery();
        if (submissionId != null) {
            lqw.eq(BizSubmissionChapter::getBidSubmissionId, submissionId);
        }
        if (documentId != null) {
            lqw.eq(BizSubmissionChapter::getSubmissionDocumentId, documentId);
        }
        baseMapper.delete(lqw);
    }

    /**
     */
    private String buildChapterGenerationPrompt(BizBidProject project, BizDocumentConfig documentConfig) {
        String documentTypeDesc = switch (documentConfig.getDocumentType()) {
            case "technical" -> "技术标";
            case "commercial" -> "商务标";
            case "complete" -> "完整标书";
            default -> "标书";
        };

        return String.format("""
            基于以下招标文件，生成%s的章节目录结构。

            项目信息：
            - 项目名称：%s
            - 招标单位：%s
            - 项目类型：%s
            - 预算金额：%s

            要求：
            1. 输出严格的 JSON 格式
            2. 章节层级不超过 4 层
            3. 每个父章节（level 1-2）必须包含 reasonDescription 字段，说明为什么需要这个章节（50-100字）
            4. 章节编号格式：
               - 一级章节使用中文：第一章、第二章、第三章...
               - 二级及以下使用数字：1.1、1.2、1.1.1、1.1.1.1
            5. 章节标题简洁明确
            6. 总输出控制在 7000 token 以内
            7. 3级及以下章节的 reasonDescription 可以为空字符串
            8. 每个章节必须包含 chapterType 字段，取值为 "template" 或 "generate"：
               - "template"：招标文件中已提供规定格式/固定模板的章节（例如：格式一、格式二、
                 附表、投标函格式、法定代表人授权委托书格式、开标一览表、报价表格式、
                 资格审查表、投标保证金格式等带有固定表格或固定文本的章节）
               - "generate"：需要投标人自行编写的章节（例如：技术方案、实施计划、
                 项目理解、人员配置、售后服务方案等需要根据项目情况撰写的章节）
            9. 判断 chapterType 的关键依据：如果招标文件中该章节包含"格式"、"模板"、
               "范本"、"样式"、"附表"、"按以下格式"、"参照以下格式"等字样，
               或者包含需要填写的固定表格/固定文本框架，则标记为 "template"

            JSON Schema（必须严格遵守）：
            {
              "chapters": [
                {
                  "chapterNo": "第一章",
                  "chapterTitle": "投标函及投标函附录",
                  "chapterLevel": 1,
                  "chapterType": "template",
                  "reasonDescription": "生成说明（1-2级必填，3级以下可为空）",
                  "children": [
                    {
                      "chapterNo": "1.1",
                      "chapterTitle": "投标函",
                      "chapterLevel": 2,
                      "chapterType": "template",
                      "reasonDescription": "生成说明",
                      "children": []
                    }
                  ]
                },
                {
                  "chapterNo": "第二章",
                  "chapterTitle": "技术方案",
                  "chapterLevel": 1,
                  "chapterType": "generate",
                  "reasonDescription": "生成说明",
                  "children": [
                    {
                      "chapterNo": "2.1",
                      "chapterTitle": "项目理解与需求分析",
                      "chapterLevel": 2,
                      "chapterType": "generate",
                      "reasonDescription": "生成说明",
                      "children": []
                    }
                  ]
                }
              ]
            }

            请直接返回 JSON，不要有任何其他文字说明。
            """,
            documentTypeDesc,
            project.getProjectName(),
            project.getBidOrg(),
            project.getProjectType(),
            project.getBudgetAmount()
        );
    }

    /**
     * 解析 AI 返回的 JSON 为章节列表
     */
    private void parseChapterJson(String jsonResponse, Long submissionId, Long documentConfigId) {
        try {
            // 提取 JSON 部分（去除可能的 markdown 代码块标记）
            String cleanJson = jsonResponse.trim();
            if (cleanJson.startsWith("```json")) {
                cleanJson = cleanJson.substring(7);
            }
            if (cleanJson.startsWith("```")) {
                cleanJson = cleanJson.substring(3);
            }
            if (cleanJson.endsWith("```")) {
                cleanJson = cleanJson.substring(0, cleanJson.length() - 3);
            }
            cleanJson = cleanJson.trim();

            JSONObject root = JSONUtil.parseObj(cleanJson);
            JSONArray chapters = root.getJSONArray("chapters");

            int sortOrder = 0;
            for (Object obj : chapters) {
                JSONObject chapterJson = (JSONObject) obj;
                sortOrder++;
                parseAndInsertChapterRecursive(chapterJson, submissionId, documentConfigId, 0L, sortOrder);
            }

        } catch (Exception e) {
            log.error("解析章节 JSON 失败: {}", jsonResponse, e);
            throw new ServiceException("解析 AI 返回的章节结构失败: " + e.getMessage());
        }
    }

    /**
     * 递归解析并插入章节（先插入父节点获取ID，再插入子节点）
     */
    private void parseAndInsertChapterRecursive(JSONObject chapterJson, Long submissionId, Long documentConfigId,
                                                Long parentId, int sortOrder) {
        BizSubmissionChapter chapter = new BizSubmissionChapter();
        chapter.setBidSubmissionId(submissionId);
        chapter.setSubmissionDocumentId(documentConfigId);
        chapter.setParentId(parentId);
        chapter.setChapterNo(chapterJson.getStr("chapterNo"));
        chapter.setChapterTitle(chapterJson.getStr("chapterTitle"));
        chapter.setChapterLevel(chapterJson.getInt("chapterLevel"));
        chapter.setReasonDescription(chapterJson.getStr("reasonDescription", ""));
        chapter.setSortOrder(sortOrder);
        String chapterType = chapterJson.getStr("chapterType", "generate");
        if (!"template".equals(chapterType) && !"generate".equals(chapterType)) {
            chapterType = "generate";
        }
        chapter.setChapterType(chapterType);
        chapter.setGenerationStatus("pending");
        chapter.setGenerationProgress(0);
        chapter.setAiModel("qwen-long-latest");

        // 插入当前章节，获取生成的 ID
        baseMapper.insert(chapter);
        Long currentChapterId = chapter.getId();

        // 递归处理子章节
        JSONArray children = chapterJson.getJSONArray("children");
        if (children != null && !children.isEmpty()) {
            int childSortOrder = 0;
            for (Object childObj : children) {
                JSONObject childJson = (JSONObject) childObj;
                childSortOrder++;
                // 使用当前章节的 ID 作为子章节的 parentId
                parseAndInsertChapterRecursive(childJson, submissionId, documentConfigId, currentChapterId, childSortOrder);
            }
        }
    }

    /**
     * 递归解析章节（已废弃，使用 parseAndInsertChapterRecursive 代替）
     */
    @Deprecated
    private void parseChapterRecursive(JSONObject chapterJson, Long submissionId, Long documentConfigId,
                                       Long parentId, int sortOrder, List<BizSubmissionChapter> result) {
        BizSubmissionChapter chapter = new BizSubmissionChapter();
        chapter.setBidSubmissionId(submissionId);
        chapter.setSubmissionDocumentId(documentConfigId);
        chapter.setParentId(parentId);
        chapter.setChapterNo(chapterJson.getStr("chapterNo"));
        chapter.setChapterTitle(chapterJson.getStr("chapterTitle"));
        chapter.setChapterLevel(chapterJson.getInt("chapterLevel"));
        chapter.setReasonDescription(chapterJson.getStr("reasonDescription", ""));
        chapter.setSortOrder(sortOrder);
        String chapterType = chapterJson.getStr("chapterType", "generate");
        if (!"template".equals(chapterType) && !"generate".equals(chapterType)) {
            chapterType = "generate";
        }
        chapter.setChapterType(chapterType);
        chapter.setGenerationStatus("pending");
        chapter.setGenerationProgress(0);
        chapter.setAiModel("qwen-long-latest");

        result.add(chapter);

        // 递归处理子章节
        JSONArray children = chapterJson.getJSONArray("children");
        if (children != null && !children.isEmpty()) {
            int childSortOrder = 0;
            for (Object childObj : children) {
                JSONObject childJson = (JSONObject) childObj;
                childSortOrder++;
                parseChapterRecursive(childJson, submissionId, documentConfigId, 0L, childSortOrder, result);
            }
        }
    }

}
