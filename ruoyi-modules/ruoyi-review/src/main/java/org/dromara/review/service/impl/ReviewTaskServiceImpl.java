package org.dromara.review.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import cn.hutool.core.bean.BeanUtil;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.review.domain.ReviewResultItem;
import org.dromara.review.domain.ReviewStandard;
import org.dromara.review.domain.ReviewTask;
import org.dromara.review.domain.ReviewTaskFile;
import org.dromara.review.domain.ReviewTaskStandard;
import org.dromara.review.domain.bo.ReviewTaskBo;
import org.dromara.review.domain.vo.ReviewResultItemVo;
import org.dromara.review.domain.vo.ReviewTaskVo;
import org.dromara.review.agent.ReviewAgent;
import org.dromara.review.graph.ReviewGraphAgent;
import org.springframework.beans.factory.annotation.Value;
import org.dromara.review.domain.ReviewKnowledgeMisjudgment;
import org.dromara.review.domain.ReviewStandardKnowledge;
import org.dromara.review.domain.ReviewStandardRule;
import org.dromara.review.mapper.ReviewKnowledgeMisjudgmentMapper;
import org.dromara.review.mapper.ReviewResultItemMapper;
import org.dromara.review.mapper.ReviewStandardKnowledgeMapper;
import org.dromara.review.mapper.ReviewStandardRuleMapper;
import org.dromara.review.service.ReviewRagService;
import org.dromara.review.mapper.ReviewStandardMapper;
import org.dromara.review.mapper.ReviewTaskFileMapper;
import org.dromara.review.mapper.ReviewTaskMapper;
import org.dromara.review.mapper.ReviewTaskStandardMapper;
import org.dromara.review.service.IReviewTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.Map;

/**
 * 审核任务Service业务层处理
 *
 * @author ruoyi
 * @date 2026-05-09
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ReviewTaskServiceImpl implements IReviewTaskService {

    private final ReviewTaskMapper baseMapper;
    private final ReviewTaskStandardMapper reviewTaskStandardMapper;
    private final ReviewTaskFileMapper reviewTaskFileMapper;
    private final ReviewStandardMapper reviewStandardMapper;
    private final ReviewResultItemMapper reviewResultItemMapper;
    private final ReviewKnowledgeMisjudgmentMapper misjudgmentMapper;
    private final ReviewStandardKnowledgeMapper standardKnowledgeMapper;
    private final ReviewStandardRuleMapper standardRuleMapper;
    private final ReviewAgent reviewAgent;
    private final ReviewGraphAgent reviewGraphAgent;
    private final ReviewRagService reviewRagService;

    /**
     * 审核引擎灰度开关：graph=新 Graph agentic 工作流（误判对比+自校验），legacy=原单次调用。
     * 默认 legacy，确认 graph 稳定后改默认值或按需切换。配置项 review.engine。
     */
    @Value("${review.engine:legacy}")
    private String reviewEngine;

    /**
     * 通过 ApplicationContext 拿到自身代理对象，用于绕过 @Async 在内部调用时失效的问题。
     * 用 setter + @Autowired 而不是构造器注入，避免循环依赖。
     */
    private ApplicationContext applicationContext;

    @Autowired
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 查询审核任务详情（附带文件列表和标准名称）
     */
    @Override
    public ReviewTaskVo queryById(Long id) {
        ReviewTaskVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            // 查询关联标准名称拼接
            List<ReviewTaskStandard> taskStandards = reviewTaskStandardMapper.selectList(
                Wrappers.<ReviewTaskStandard>lambdaQuery()
                    .eq(ReviewTaskStandard::getTaskId, id)
            );
            if (CollUtil.isNotEmpty(taskStandards)) {
                List<Long> standardIds = taskStandards.stream()
                    .map(ReviewTaskStandard::getStandardId)
                    .collect(Collectors.toList());
                List<ReviewStandard> standards = reviewStandardMapper.selectByIds(standardIds);
                String standardNames = standards.stream()
                    .map(ReviewStandard::getName)
                    .collect(Collectors.joining(","));
                vo.setStandardNames(standardNames);
            }

            // 查询附件列表
            List<ReviewTaskFile> files = reviewTaskFileMapper.selectList(
                Wrappers.<ReviewTaskFile>lambdaQuery().eq(ReviewTaskFile::getTaskId, id)
            );
            if (CollUtil.isNotEmpty(files)) {
                vo.setFiles(files.stream().map(f -> {
                    ReviewTaskVo.ReviewTaskFileVo fv = new ReviewTaskVo.ReviewTaskFileVo();
                    fv.setId(f.getId());
                    fv.setOssId(f.getOssId());
                    fv.setFileName(f.getFileName());
                    fv.setFileType(f.getFileType());
                    fv.setFilePath(f.getFilePath());
                    fv.setFileSize(f.getFileSize());
                    return fv;
                }).collect(Collectors.toList()));
            }
        }
        return vo;
    }

    /**
     * 查询审核任务分页列表
     */
    @Override
    public TableDataInfo<ReviewTaskVo> queryPageList(ReviewTaskBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<ReviewTask> lqw = buildQueryWrapper(bo);
        Page<ReviewTaskVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);

        // 批量填充 standardNames
        List<ReviewTaskVo> records = result.getRecords();
        if (CollUtil.isNotEmpty(records)) {
            List<Long> taskIds = records.stream().map(ReviewTaskVo::getId).collect(Collectors.toList());
            List<ReviewTaskStandard> allTs = reviewTaskStandardMapper.selectList(
                Wrappers.<ReviewTaskStandard>lambdaQuery().in(ReviewTaskStandard::getTaskId, taskIds)
            );
            // 收集所有标准ID
            List<Long> allStandardIds = allTs.stream().map(ReviewTaskStandard::getStandardId).distinct().collect(Collectors.toList());
            Map<Long, String> standardNameMap = new HashMap<>();
            if (CollUtil.isNotEmpty(allStandardIds)) {
                List<ReviewStandard> standards = reviewStandardMapper.selectByIds(allStandardIds);
                standardNameMap = standards.stream().collect(Collectors.toMap(ReviewStandard::getId, ReviewStandard::getName, (a, b) -> a));
            }
            // 按 taskId 分组
            Map<Long, List<ReviewTaskStandard>> tsMap = allTs.stream().collect(Collectors.groupingBy(ReviewTaskStandard::getTaskId));
            Map<Long, String> finalStandardNameMap = standardNameMap;
            for (ReviewTaskVo vo : records) {
                List<ReviewTaskStandard> tsList = tsMap.getOrDefault(vo.getId(), List.of());
                String names = tsList.stream()
                    .map(ts -> finalStandardNameMap.getOrDefault(ts.getStandardId(), ""))
                    .filter(n -> !n.isEmpty())
                    .collect(Collectors.joining(", "));
                vo.setStandardNames(names);
            }
        }

        return TableDataInfo.build(result);
    }

    private LambdaQueryWrapper<ReviewTask> buildQueryWrapper(ReviewTaskBo bo) {
        LambdaQueryWrapper<ReviewTask> lqw = Wrappers.lambdaQuery();
        lqw.like(StringUtils.isNotBlank(bo.getTaskName()), ReviewTask::getTaskName, bo.getTaskName());
        lqw.eq(StringUtils.isNotBlank(bo.getTaskType()), ReviewTask::getTaskType, bo.getTaskType());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), ReviewTask::getStatus, bo.getStatus());
        lqw.eq(StringUtils.isNotBlank(bo.getPassStatus()), ReviewTask::getPassStatus, bo.getPassStatus());
        lqw.orderByDesc(ReviewTask::getCreateTime);
        return lqw;
    }

    /**
     * 创建审核任务（含关联标准、保存附件、自动触发AI审核）
     *
     * 同来源(sourceId+sourceType)已存在记录时不再 INSERT，而是 UPDATE 原行：
     *   version+1、清理旧附件/标准/结果明细、状态重置后重跑审核。
     * 这样列表里「审核次数」始终对应同一文档的累计次数，不会因为多次重审产生多行。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTask(ReviewTaskBo bo) {
        // 1. 命中同来源旧任务 → 走"复用同一行"分支
        ReviewTask existing = null;
        if (StringUtils.isNotBlank(bo.getSourceId()) && StringUtils.isNotBlank(bo.getSourceType())) {
            existing = baseMapper.selectOne(
                Wrappers.<ReviewTask>lambdaQuery()
                    .eq(ReviewTask::getSourceId, bo.getSourceId())
                    .eq(ReviewTask::getSourceType, bo.getSourceType())
                    .orderByDesc(ReviewTask::getCreateTime)
                    .last("LIMIT 1")
            );
        }

        if (existing != null) {
            Long taskId = existing.getId();
            // 清掉这条任务的旧子表数据（标准关联 / 附件 / 结果明细）
            reviewTaskStandardMapper.delete(
                Wrappers.<ReviewTaskStandard>lambdaQuery().eq(ReviewTaskStandard::getTaskId, taskId)
            );
            reviewTaskFileMapper.delete(
                Wrappers.<ReviewTaskFile>lambdaQuery().eq(ReviewTaskFile::getTaskId, taskId)
            );
            reviewResultItemMapper.delete(
                Wrappers.<ReviewResultItem>lambdaQuery().eq(ReviewResultItem::getTaskId, taskId)
            );

            // 把新提交的字段拷到老行上
            BeanUtil.copyProperties(bo, existing, "id", "version", "parentTaskId", "createTime", "createBy");
            existing.setFormSnapshot(bo.getFormSnapshot());
            existing.setVersion((existing.getVersion() == null ? 1 : existing.getVersion()) + 1);
            // 重审重置状态与统计
            existing.setStatus("pending");
            existing.setPassStatus(null);
            existing.setScore(null);
            existing.setReviewDuration(null);
            existing.setAiSummary(null);
            existing.setResultJson(null);
            existing.setResultMarkdown(null);
            existing.setTotalRules(null);
            existing.setPassCount(null);
            existing.setErrorCount(null);
            existing.setWarningCount(null);
            existing.setInfoCount(null);
            existing.setMisjudgedCount(0);
            baseMapper.updateById(existing);

            // 重新写关联标准 & 附件
            insertStandards(taskId, bo.getStandardIds());
            insertFiles(taskId, bo.getFiles());

            // 触发审核（外部系统集成场景）
            triggerExecuteIfExternal(bo, taskId);
            return taskId;
        }

        // 2. 全新任务 → 正常 INSERT
        ReviewTask task = BeanUtil.toBean(bo, ReviewTask.class);
        task.setFormSnapshot(bo.getFormSnapshot());

        // 显式传 parentTaskId 时（手工"重新审核"按钮等场景）按链路递增 version
        if (bo.getParentTaskId() != null) {
            task.setParentTaskId(bo.getParentTaskId());
            ReviewTask parentTask = baseMapper.selectById(bo.getParentTaskId());
            if (parentTask != null && parentTask.getVersion() != null) {
                task.setVersion(parentTask.getVersion() + 1);
            }
        }

        baseMapper.insert(task);
        insertStandards(task.getId(), bo.getStandardIds());
        insertFiles(task.getId(), bo.getFiles());
        triggerExecuteIfExternal(bo, task.getId());
        return task.getId();
    }

    private void insertStandards(Long taskId, List<Long> standardIds) {
        if (CollUtil.isNotEmpty(standardIds)) {
            for (Long standardId : standardIds) {
                ReviewTaskStandard taskStandard = new ReviewTaskStandard();
                taskStandard.setTaskId(taskId);
                taskStandard.setStandardId(standardId);
                reviewTaskStandardMapper.insert(taskStandard);
            }
        }
    }

    private void insertFiles(Long taskId, List<ReviewTaskBo.TaskFileBo> files) {
        if (CollUtil.isNotEmpty(files)) {
            int sortOrder = 1;
            for (ReviewTaskBo.TaskFileBo fileBo : files) {
                ReviewTaskFile taskFile = new ReviewTaskFile();
                taskFile.setTaskId(taskId);
                taskFile.setOssId(fileBo.getOssId());
                taskFile.setFileName(fileBo.getFileName());
                taskFile.setFileType(fileBo.getFileType());
                taskFile.setFilePath(fileBo.getFilePath());
                taskFile.setFileSize(fileBo.getFileSize());
                taskFile.setSortOrder(sortOrder++);
                reviewTaskFileMapper.insert(taskFile);
            }
        }
    }

    private void triggerExecuteIfExternal(ReviewTaskBo bo, Long taskId) {
        // autoExecute=true 时通过 ApplicationContext 拿代理触发异步审核（不会阻塞调用方）
        // 没传 autoExecute 时保持旧行为：调用方需要自己调 POST /task/{id}/execute
        if (Boolean.TRUE.equals(bo.getAutoExecute())) {
            try {
                IReviewTaskService self = applicationContext.getBean(IReviewTaskService.class);
                self.executeReview(taskId);
                log.info("[ReviewTask] autoExecute=true，已异步触发审核 taskId={}", taskId);
            } catch (Exception e) {
                log.warn("[ReviewTask] autoExecute 触发失败 taskId={}, err={}（不影响任务创建，可手动调 execute 重试）",
                    taskId, e.getMessage());
            }
        }
    }

    /**
     * 修改审核任务
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(ReviewTaskBo bo) {
        ReviewTask update = BeanUtil.toBean(bo, ReviewTask.class);
        return baseMapper.updateById(update) > 0;
    }

    /**
     * 校验并批量删除审核任务信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        return baseMapper.deleteByIds(ids) > 0;
    }

    /**
     * 触发AI审核（通用，根据任务类型和附件自动选择AI模型）
     */
    @Async
    @Override
    public void executeReview(Long taskId) {
        if ("graph".equalsIgnoreCase(reviewEngine)) {
            log.info("[ReviewTask] 使用 Graph 审核引擎 taskId={}", taskId);
            reviewGraphAgent.execute(taskId);
        } else {
            reviewAgent.execute(taskId);
        }
    }

    /**
     * 标记误判 → 写入误判记录 → 异步向量化学习
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markMisjudgment(Long resultItemId, String reason) {
        ReviewResultItem item = reviewResultItemMapper.selectById(resultItemId);
        if (item == null) return;

        // 1. 更新结果明细的误判标记
        item.setMisjudged("1");
        item.setMisjudgmentReason(reason);
        reviewResultItemMapper.updateById(item);

        // 2. 查找关联的知识库（通过任务 → 标准 → 知识库）
        List<ReviewTaskStandard> taskStandards = reviewTaskStandardMapper.selectList(
            Wrappers.<ReviewTaskStandard>lambdaQuery().eq(ReviewTaskStandard::getTaskId, item.getTaskId())
        );
        Long knowledgeId = null;
        if (CollUtil.isNotEmpty(taskStandards)) {
            List<Long> standardIds = taskStandards.stream().map(ReviewTaskStandard::getStandardId).collect(Collectors.toList());
            List<ReviewStandard> standards = reviewStandardMapper.selectByIds(standardIds);
            // 取第一个非系统标准关联的知识库，如果没有就取任意一个
            for (ReviewStandard s : standards) {
                knowledgeId = findFirstKnowledgeId(s.getId());
                if (knowledgeId != null) break;
            }
        }

        // 3. 写入误判记录表
        if (knowledgeId != null) {
            ReviewKnowledgeMisjudgment mj = new ReviewKnowledgeMisjudgment();
            mj.setKnowledgeId(knowledgeId);
            mj.setTaskId(item.getTaskId());
            mj.setRuleId(item.getRuleId());
            mj.setFieldName(item.getFieldName());
            mj.setAiJudgment(item.getMatchStatus() + ": " + item.getDescription());
            mj.setCorrectJudgment("人工判定为误判");
            mj.setReason(reason);
            mj.setIsLearned("0");
            misjudgmentMapper.insert(mj);

            // 4. 异步向量化，下次审核时 RAG 可检索到
            reviewRagService.indexMisjudgment(mj.getId());

            // 5. 更新规则误判计数 + 阈值预警
            if (item.getRuleId() != null) {
                ReviewStandardRule rule = standardRuleMapper.selectById(item.getRuleId());
                if (rule != null) {
                    rule.setMissCount(rule.getMissCount() + 1);
                    checkRuleHealth(rule);
                    standardRuleMapper.updateById(rule);
                }
            }

            log.info("误判标记+学习完成: resultItemId={}, knowledgeId={}, reason={}", resultItemId, knowledgeId, reason);
        } else {
            log.info("误判标记完成（无关联知识库，未写入学习记录）: resultItemId={}", resultItemId);
        }
    }

    /**
     * 规则健康度检测
     * <p>
     * 触发条件（同时满足）：
     * - 命中次数 >= 10（样本量足够）
     * - 误判率 > 20%
     * <p>
     * 触发后：
     * - 规则 status 改为 '2'（待修订）
     * - 置信度下调为实际准确率
     * - 打印预警日志
     */
    private void checkRuleHealth(ReviewStandardRule rule) {
        int hit = rule.getHitCount();
        int miss = rule.getMissCount();
        if (hit < 10) return;

        double missRate = (double) miss / hit;
        if (missRate > 0.20) {
            if (!"2".equals(rule.getStatus())) {
                rule.setStatus("2");
                log.warn("[规则预警] 规则误判率过高，已标记为待修订: ruleId={}, content={}, hitCount={}, missCount={}, missRate={}%",
                    rule.getId(), rule.getContent(), hit, miss, String.format("%.1f", missRate * 100));
            }
            // 下调置信度为实际准确率
            double accuracy = (1.0 - missRate) * 100;
            rule.setConfidence(java.math.BigDecimal.valueOf(accuracy).setScale(2, java.math.RoundingMode.HALF_UP));
        }
    }

    private Long findFirstKnowledgeId(Long standardId) {
        List<ReviewStandardKnowledge> skList = standardKnowledgeMapper.selectList(
            Wrappers.<ReviewStandardKnowledge>lambdaQuery().eq(ReviewStandardKnowledge::getStandardId, standardId).last("LIMIT 1")
        );
        return skList.isEmpty() ? null : skList.get(0).getKnowledgeId();
    }

    @Override
    public List<ReviewResultItemVo> queryResultItems(Long taskId) {
        return reviewResultItemMapper.selectVoList(
            Wrappers.<ReviewResultItem>lambdaQuery()
                .eq(ReviewResultItem::getTaskId, taskId)
                .orderByAsc(ReviewResultItem::getSortOrder)
        );
    }

}
