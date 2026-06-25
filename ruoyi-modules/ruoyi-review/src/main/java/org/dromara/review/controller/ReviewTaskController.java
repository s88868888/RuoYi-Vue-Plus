package org.dromara.review.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.review.domain.bo.ReviewTaskBo;
import org.dromara.review.domain.vo.ReviewEngineCompareVo;
import org.dromara.review.domain.vo.ReviewResultItemVo;
import org.dromara.review.domain.vo.ReviewTaskVo;
import org.dromara.review.service.IReviewProjectService;
import org.dromara.review.service.IReviewTaskService;
import org.dromara.review.service.ReviewEngineCompareService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;
import java.util.Map;

/**
 * 审核任务
 *
 * @author LionLi
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/review/task")
public class ReviewTaskController extends BaseController {

    private final IReviewTaskService reviewTaskService;
    private final ReviewEngineCompareService reviewEngineCompareService;
    private final IReviewProjectService reviewProjectService;

    /**
     * 分页查询任务列表
     */
    @GetMapping("/list")
    public TableDataInfo<ReviewTaskVo> list(ReviewTaskBo bo, PageQuery pageQuery) {
        return reviewTaskService.queryPageList(bo, pageQuery);
    }

    /**
     * 获取任务详情（含文件列表、标准名称、审核结果）
     *
     * @param id 主键
     */
    @GetMapping("/{id}")
    public R<ReviewTaskVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable Long id) {
        return R.ok(reviewTaskService.queryById(id));
    }

    /**
     * 创建审核任务
     */
    @Log(title = "审核任务", businessType = BusinessType.INSERT)
    @PostMapping()
    public R<Long> add(@Validated(AddGroup.class) @RequestBody ReviewTaskBo bo) {
        return R.ok(reviewTaskService.createTask(bo));
    }

    /**
     * 一键创建并立即异步执行审核（外部系统接入推荐入口）
     * <p>
     * 等价于 add(bo) 时强制 autoExecute=true，省一次 RPC。
     * 接口立刻返回 taskId，AI 审核在后台异步跑，完成后通过 callbackUrl 回调。
     */
    @Log(title = "创建并执行审核", businessType = BusinessType.INSERT)
    @PostMapping("/createAndExecute")
    public R<Long> createAndExecute(@Validated(AddGroup.class) @RequestBody ReviewTaskBo bo) {
        bo.setAutoExecute(true);
        return R.ok(reviewTaskService.createTask(bo));
    }

    /**
     * 触发AI审核
     *
     * @param id 任务ID
     */
    @Log(title = "执行AI审核", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/execute")
    public R<Void> execute(@NotNull(message = "任务ID不能为空") @PathVariable Long id) {
        reviewTaskService.executeReview(id);
        return R.ok();
    }

    /**
     * 标记误判
     *
     * @param id           任务ID
     * @param resultItemId 审核结果项ID
     * @param body         请求体（含reason字段）
     */
    @Log(title = "标记误判", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/misjudgment/{resultItemId}")
    public R<Void> markMisjudgment(@NotNull(message = "任务ID不能为空") @PathVariable Long id,
                                   @NotNull(message = "审核结果项ID不能为空") @PathVariable Long resultItemId,
                                   @RequestBody Map<String, String> body) {
        reviewTaskService.markMisjudgment(resultItemId, body.get("reason"));
        return R.ok();
    }

    /**
     * 删除任务
     *
     * @param ids 主键串
     */
    @Log(title = "审核任务", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] ids) {
        return toAjax(reviewTaskService.deleteWithValidByIds(List.of(ids), true));
    }

    /**
     * 查询任务的审核结果明细列表
     *
     * @param id 任务ID
     */
    @GetMapping("/{id}/results")
    public R<List<ReviewResultItemVo>> listResults(@NotNull(message = "任务ID不能为空") @PathVariable Long id) {
        return R.ok(reviewTaskService.queryResultItems(id));
    }

    /**
     * 双引擎(legacy vs graph)审核对比评测。
     * <p>
     * 对传入的任务分别用两种引擎 dry-run（只算不落库，不污染生产数据），返回指标差异报告。
     * 用于量化 graph agentic 工作流（误判核对 + 自校验）相比 legacy 的准确率差异。
     *
     * @param taskIds 待对比的任务ID列表
     */
    @SaCheckPermission("review:task:list")
    @Log(title = "审核引擎对比", businessType = BusinessType.OTHER)
    @PostMapping("/compareEngines")
    public R<List<ReviewEngineCompareVo>> compareEngines(
        @NotEmpty(message = "任务ID列表不能为空") @RequestBody List<Long> taskIds) {
        return R.ok(reviewEngineCompareService.compare(taskIds));
    }

    /**
     * 导出选中任务为「工程包」zip（附件原件 + 审核结果/关注/规则快照/脱敏框，自包含可离线复看）。
     *
     * @param taskIds  勾选的任务ID列表
     * @param response 直接写 zip 流
     */
    @SaCheckPermission("review:task:list")
    @Log(title = "导出审核工程包", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(@NotEmpty(message = "请选择要导出的任务") @RequestBody List<Long> taskIds,
                       HttpServletResponse response) {
        reviewProjectService.exportProject(taskIds, response);
    }

    /**
     * 导入工程包 zip，在当前库重新落库为全新任务（新 id、附件重传 OSS）。
     *
     * @param file 上传的工程包 zip
     * @return 新建的任务ID列表
     */
    @SaCheckPermission("review:task:list")
    @Log(title = "导入审核工程包", businessType = BusinessType.IMPORT)
    @PostMapping(value = "/import", consumes = "multipart/form-data")
    public R<List<Long>> importProject(@RequestPart("file") MultipartFile file) {
        return R.ok("导入成功", reviewProjectService.importProject(file));
    }
}
