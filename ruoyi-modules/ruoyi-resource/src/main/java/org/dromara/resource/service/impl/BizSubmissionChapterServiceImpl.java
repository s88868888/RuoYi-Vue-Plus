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
import org.dromara.common.sse.utils.SseMessageUtils;
import org.dromara.resource.domain.BizBidProject;
import org.dromara.resource.domain.BizBidProjectAttachment;
import org.dromara.resource.domain.BizBidSubmission;
import org.dromara.resource.domain.BizDocumentConfig;
import org.dromara.resource.domain.BizSubmissionChapter;
import org.dromara.resource.domain.vo.BizSubmissionChapterVo;
import org.dromara.resource.mapper.BizBidProjectAttachmentMapper;
import org.dromara.resource.mapper.BizBidProjectMapper;
import org.dromara.resource.mapper.BizBidSubmissionMapper;
import org.dromara.resource.mapper.BizDocumentConfigMapper;
import org.dromara.resource.mapper.BizSubmissionChapterMapper;
import org.dromara.resource.service.IBizSubmissionChapterService;
import org.dromara.system.domain.SysOss;
import org.dromara.system.mapper.SysOssMapper;
import org.springframework.core.io.UrlResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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
    private final BizBidProjectAttachmentMapper attachmentMapper;
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
        chapter.setGenerationStatus("generating");
        chapter.setGenerationProgress(0);
        baseMapper.updateById(chapter);
        log.info("开始生成章节内容，章节ID: {}", id);
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
        chapter.setGenerationStatus("generating");
        chapter.setGenerationProgress(0);
        chapter.setChapterContent(null);
        chapter.setErrorMessage(null);
        baseMapper.updateById(chapter);
        log.info("开始重新生成章节内容，章节ID: {}", id);
    }

    @Override
    public Boolean deleteById(Long id) {
        return baseMapper.deleteById(id) > 0;
    }

    @Override
    public void addChapter(Long submissionDocumentId, Long parentId, String chapterTitle, String chapterType) {
        BizSubmissionChapter chapter = new BizSubmissionChapter();
        chapter.setSubmissionDocumentId(submissionDocumentId);
        chapter.setParentId(parentId);
        chapter.setChapterTitle(chapterTitle);
        chapter.setChapterType(chapterType);
        chapter.setGenerationStatus("pending");
        chapter.setGenerationProgress(0);
        chapter.setSortOrder(0);
        chapter.setChapterLevel(1);
        baseMapper.insert(chapter);
    }

    @Override
    public void generateChapterStructure(Long submissionId, Long documentConfigId) {
        // 获取当前用户ID
        Long userId = LoginHelper.getUserId();
        // 异步执行生成任务
        SpringUtils.getBean(IBizSubmissionChapterService.class)
            .doGenerateChapterStructure(submissionId, documentConfigId, userId);
    }

    @Override
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void doGenerateChapterStructure(Long submissionId, Long documentConfigId, Long userId) {
        try {
            log.info("开始生成章节结构，投标项目ID: {}, 文档配置ID: {}", submissionId, documentConfigId);

            // 推送开始消息
            SseMessageUtils.sendMessage(userId, buildSseMessage("start", "开始生成章节结构", 0, null));

            // 1. 获取投标项目信息
            log.info("查询投标项目，submissionId: {}", submissionId);
            SseMessageUtils.sendMessage(userId, buildSseMessage("progress", "正在加载项目信息", 10, null));

            BizBidSubmission submission = submissionMapper.selectById(submissionId);
            log.info("查询结果: {}", submission);
            if (submission == null) {
                SseMessageUtils.sendMessage(userId, buildSseMessage("error", "投标项目不存在", 0, null));
                return;
            }

            // 2. 获取文档配置
            BizDocumentConfig documentConfig = documentConfigMapper.selectById(documentConfigId);
            if (documentConfig == null) {
                SseMessageUtils.sendMessage(userId, buildSseMessage("error", "文档配置不存在", 0, null));
                return;
            }

            // 3. 获取招标项目信息
            SseMessageUtils.sendMessage(userId, buildSseMessage("progress", "正在加载招标项目信息", 20, null));
            BizBidProject project = projectMapper.selectById(submission.getBidProjectId());
            if (project == null) {
                SseMessageUtils.sendMessage(userId, buildSseMessage("error", "招标项目不存在", 0, null));
                return;
            }

            // 4. 获取招标文件附件
            SseMessageUtils.sendMessage(userId, buildSseMessage("progress", "正在加载招标文件", 30, null));
            if (StrUtil.isBlank(project.getAttachments())) {
                SseMessageUtils.sendMessage(userId, buildSseMessage("error", "招标项目没有上传招标文件", 0, null));
                return;
            }

            // 从 attachments 字段获取附件ID（可能是逗号分隔的多个ID）
            String[] attachmentIds = project.getAttachments().split(",");
            if (attachmentIds.length == 0) {
                SseMessageUtils.sendMessage(userId, buildSseMessage("error", "招标项目没有上传招标文件", 0, null));
                return;
            }

            // 获取第一个附件的文件URL
            Long ossId = Long.parseLong(attachmentIds[0].trim());
            SysOss sysOss = sysOssMapper.selectById(ossId);
            if (sysOss == null) {
                SseMessageUtils.sendMessage(userId, buildSseMessage("error", "招标文件不存在", 0, null));
                return;
            }

            String fileUrl = sysOss.getUrl();
            if (StrUtil.isBlank(fileUrl)) {
                SseMessageUtils.sendMessage(userId, buildSseMessage("error", "招标文件URL为空", 0, null));
                return;
            }

            // 5. 构建 AI 提示词
            SseMessageUtils.sendMessage(userId, buildSseMessage("progress", "正在调用AI生成章节结构", 40, null));
            String prompt = buildChapterGenerationPrompt(project, documentConfig);

            // 6. 调用 qwen-long 生成章节结构
            String aiResponse;
            try {
                aiResponse = aiChatService.chatWithDocumentUrl(fileUrl, prompt);
                SseMessageUtils.sendMessage(userId, buildSseMessage("progress", "AI生成完成，正在解析结果", 70, null));
            } catch (Exception e) {
                log.error("AI 生成章节结构失败", e);
                SseMessageUtils.sendMessage(userId, buildSseMessage("error", "AI 生成章节结构失败: " + e.getMessage(), 0, null));
                return;
            }

            // 7. 解析 AI 返回的 JSON 并插入数据库（递归插入，自动处理父子关系）
            SseMessageUtils.sendMessage(userId, buildSseMessage("progress", "正在保存章节结构", 80, null));
            parseChapterJson(aiResponse, submissionId, documentConfigId);

            // 8. 推送完成消息（不需要返回数据，前端会自动重新加载）
            SseMessageUtils.sendMessage(userId, buildSseMessage("success", "章节结构生成完成", 100, null));

        } catch (Exception e) {
            log.error("生成章节结构异常", e);
            SseMessageUtils.sendMessage(userId, buildSseMessage("error", "生成章节结构异常: " + e.getMessage(), 0, null));
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
     * 构建章节生成提示词
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

            JSON Schema（必须严格遵守）：
            {
              "chapters": [
                {
                  "chapterNo": "第一章",
                  "chapterTitle": "章节标题",
                  "chapterLevel": 1,
                  "reasonDescription": "生成说明（1-2级必填，3级以下可为空）",
                  "children": [
                    {
                      "chapterNo": "1.1",
                      "chapterTitle": "子章节标题",
                      "chapterLevel": 2,
                      "reasonDescription": "生成说明",
                      "children": [
                        {
                          "chapterNo": "1.1.1",
                          "chapterTitle": "三级章节标题",
                          "chapterLevel": 3,
                          "reasonDescription": "",
                          "children": []
                        }
                      ]
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
        chapter.setChapterType("generate");
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
        chapter.setChapterType("generate");
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
