package org.dromara.review.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.review.agent.ReviewAgent;
import org.dromara.review.domain.ReviewStandardRule;
import org.dromara.review.domain.ReviewTask;
import org.dromara.review.domain.ReviewTaskFile;
import org.dromara.review.graph.ReviewContext;
import org.dromara.review.graph.ReviewGraphSupport;
import org.dromara.review.mapper.ReviewTaskFileMapper;
import org.dromara.review.mapper.ReviewTaskMapper;
import org.dromara.review.service.ReviewRagService;

import java.util.List;
import java.util.Map;

/**
 * 准备节点：加载任务/附件/标准/规则/模板/模型配置，并做 RAG 初始知识检索。
 * <p>
 * 完全复用 {@link ReviewAgent} 已抽取的 public 加载方法，行为与 legacy execute() 的步骤 1-4 一致，
 * 仅把结果收拢进 {@link ReviewContext}。
 *
 * @author Linson
 */
@Slf4j
@RequiredArgsConstructor
public class PrepareNode implements NodeAction {

    private final ReviewAgent reviewAgent;
    private final ReviewTaskMapper taskMapper;
    private final ReviewTaskFileMapper taskFileMapper;
    private final ReviewRagService reviewRagService;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        ReviewContext ctx = ReviewGraphSupport.getContext(state);
        ReviewTask task = ctx.getTask();
        Long taskId = task.getId();

        log.info("[Graph:prepare] 开始准备审核上下文: taskId={}, name={}", taskId, task.getTaskName());

        // 标记审核中并落库（与 legacy execute() 一致，让前端能看到"审核中"状态）；dry-run 评测不改状态
        if (!ctx.isDryRun()) {
            task.setStatus("reviewing");
            taskMapper.updateById(task);
        }

        // 1. 附件
        List<ReviewTaskFile> files = taskFileMapper.selectList(
            Wrappers.<ReviewTaskFile>lambdaQuery().eq(ReviewTaskFile::getTaskId, taskId)
        );
        ctx.setFiles(files);

        // 2. 标准 + 规则
        List<Long> standardIds = reviewAgent.loadStandardIds(taskId);
        List<ReviewStandardRule> rules = reviewAgent.loadRules(standardIds);
        ctx.setStandardIds(standardIds);
        ctx.setRules(rules);

        // 3. 模板 + 模型配置
        ctx.setTemplate(reviewAgent.loadPromptTemplate(task.getTaskType()));
        ctx.setModelConfig(reviewAgent.resolveModelConfig(ctx.getTemplate()));

        // 4. RAG 初始知识上下文 + 规则文本
        String queryText = task.getFormSnapshot() != null ? task.getFormSnapshot() : task.getTaskName();
        ctx.setKnowledgeContext(reviewRagService.buildEnrichedContext(standardIds, queryText));
        ctx.setRulesText(reviewAgent.buildRulesText(rules));

        log.info("[Graph:prepare] 准备完成: 附件={}, 规则={}, 模板={}", files.size(), rules.size(),
            ctx.getTemplate() != null ? ctx.getTemplate().getId() : null);

        return ReviewGraphSupport.withMessage(ctx,
            "prepare 完成: 附件" + files.size() + " 规则" + rules.size());
    }
}
