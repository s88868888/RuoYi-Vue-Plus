package org.dromara.review.graph;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import org.dromara.common.ai.dto.AiModelConfigDto;
import org.dromara.review.domain.ReviewPromptTemplate;
import org.dromara.review.domain.ReviewStandardRule;
import org.dromara.review.domain.ReviewTask;
import org.dromara.review.domain.ReviewTaskFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 审核 Graph 工作流的跨节点共享载体。
 * <p>
 * 设计动机：审核流程要在节点间传递的中间数据有十几项（任务、规则、模板、文档文本、初审结果、
 * 误判命中记录、校验轮次……）。若全部散落在 OverAllState 的 Map 里，key 管理混乱且无类型安全。
 * 因此把它们收拢到一个强类型对象，整体作为 {@link ReviewStateKeys#CONTEXT} 一个 key 放进 state，
 * 用 ReplaceStrategy 在节点间整体替换。
 * <p>
 * 注意：本对象在单次 invoke() 的同一线程内顺序流转（见方案中"同步执行保留租户上下文"的决定），
 * 不跨线程，因此用普通可变 POJO 即可，无需考虑并发。
 *
 * @author Linson
 */
@Data
public class ReviewContext {

    /** 审核任务 */
    private ReviewTask task;

    /** 任务关联的标准 ID 列表 */
    private List<Long> standardIds = new ArrayList<>();

    /** 生效的审核规则（含待修订规则，置信度已下调） */
    private List<ReviewStandardRule> rules = new ArrayList<>();

    /** 任务附件 */
    private List<ReviewTaskFile> files = new ArrayList<>();

    /** 命中的提示词模板 */
    private ReviewPromptTemplate template;

    /** 解析出的模型配置（DB 驱动，可能为 null 走 legacy） */
    private AiModelConfigDto modelConfig;

    /** RAG 检索的初始知识上下文（案例 + 模式 + 误判） */
    private String knowledgeContext = "";

    /**
     * 文档预处理（OCR/抽文）产出的纯文本缓存。
     * 第一轮初审时由 InitialReviewNode 填充；重审时复用，避免重复 OCR。
     * null=尚未预处理；""=已预处理但无文档内容（图片/纯表单场景）。
     */
    private String preprocessedDocText;

    /** 规则文本（喂给大模型的格式化规则清单） */
    private String rulesText = "";

    /** AI 原始返回文本（最近一次初审或重审的结果） */
    private String aiRawResponse = "";

    /** 解析后的初审结果 JSON（含 items / pass_status / summary 等） */
    private JSONObject reviewResult;

    /** 误判交叉核对命中的记录说明（供自校验和审计参考） */
    private List<String> misjudgeHits = new ArrayList<>();

    /** 自校验提出的疑点（依据不足、疑似误判的问题项描述） */
    private List<String> verifyDoubts = new ArrayList<>();

    /** 当前审核轮次：初审=1，每触发一次重审 +1，用于防止自校验死循环 */
    private int reviewRound = 1;

    /** 选用的模型名（用于落库审计） */
    private String modelUsed = "";

    /** 流程开始时间戳（毫秒），用于统计审核耗时 */
    private long startTimeMs;

    /**
     * dry-run 模式：跑完整审核逻辑但不落库/不写知识库/不回调/不推进度。
     * 供双引擎对比评测使用，避免污染生产数据。默认 false。
     */
    private boolean dryRun = false;
}
