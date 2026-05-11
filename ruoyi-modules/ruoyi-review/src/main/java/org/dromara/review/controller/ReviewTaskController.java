package org.dromara.review.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.review.domain.bo.ReviewTaskBo;
import org.dromara.review.domain.vo.ReviewResultItemVo;
import org.dromara.review.domain.vo.ReviewTaskVo;
import org.dromara.review.service.IReviewTaskService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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

    /**
     * 分页查询任务列表
     */
    @SaCheckPermission("review:task:list")
    @GetMapping("/list")
    public TableDataInfo<ReviewTaskVo> list(ReviewTaskBo bo, PageQuery pageQuery) {
        return reviewTaskService.queryPageList(bo, pageQuery);
    }

    /**
     * 获取任务详情（含文件列表、标准名称、审核结果）
     *
     * @param id 主键
     */
    @SaCheckPermission("review:task:query")
    @GetMapping("/{id}")
    public R<ReviewTaskVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable Long id) {
        return R.ok(reviewTaskService.queryById(id));
    }

    /**
     * 创建审核任务
     */
    @SaCheckPermission("review:task:add")
    @Log(title = "审核任务", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Long> add(@Validated(AddGroup.class) @RequestBody ReviewTaskBo bo) {
        return R.ok(reviewTaskService.createTask(bo));
    }

    /**
     * 触发AI审核
     *
     * @param id 任务ID
     */
    @SaCheckPermission("review:task:edit")
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
    @SaCheckPermission("review:task:edit")
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
    @SaCheckPermission("review:task:remove")
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
    @SaCheckPermission("review:task:query")
    @GetMapping("/{id}/results")
    public R<List<ReviewResultItemVo>> listResults(@NotNull(message = "任务ID不能为空") @PathVariable Long id) {
        return R.ok(reviewTaskService.queryResultItems(id));
    }
}
