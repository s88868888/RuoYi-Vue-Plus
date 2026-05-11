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
import org.dromara.review.domain.bo.ReviewStandardBo;
import org.dromara.review.domain.bo.ReviewStandardRuleBo;
import org.dromara.review.domain.vo.ReviewKnowledgeVo;
import org.dromara.review.domain.vo.ReviewStandardRuleVo;
import org.dromara.review.domain.vo.ReviewStandardVo;
import org.dromara.review.service.IReviewStandardRuleService;
import org.dromara.review.service.IReviewStandardService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 审核标准
 *
 * @author LionLi
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/review/standard")
public class ReviewStandardController extends BaseController {

    private final IReviewStandardService reviewStandardService;
    private final IReviewStandardRuleService reviewStandardRuleService;

    /**
     * 分页查询标准列表
     */
    @SaCheckPermission("review:standard:list")
    @GetMapping("/list")
    public TableDataInfo<ReviewStandardVo> list(ReviewStandardBo bo, PageQuery pageQuery) {
        return reviewStandardService.queryPageList(bo, pageQuery);
    }

    /**
     * 获取标准详情（含规则列表和关联知识库）
     *
     * @param id 主键
     */
    @SaCheckPermission("review:standard:query")
    @GetMapping("/{id}")
    public R<ReviewStandardVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable Long id) {
        return R.ok(reviewStandardService.queryById(id));
    }

    /**
     * 新增标准
     */
    @SaCheckPermission("review:standard:add")
    @Log(title = "审核标准", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody ReviewStandardBo bo) {
        return toAjax(reviewStandardService.insertByBo(bo));
    }

    /**
     * 修改标准
     */
    @SaCheckPermission("review:standard:edit")
    @Log(title = "审核标准", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody ReviewStandardBo bo) {
        return toAjax(reviewStandardService.updateByBo(bo));
    }

    /**
     * 删除标准
     *
     * @param ids 主键串
     */
    @SaCheckPermission("review:standard:remove")
    @Log(title = "审核标准", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] ids) {
        return toAjax(reviewStandardService.deleteWithValidByIds(List.of(ids), true));
    }

    // ==================== 知识库关联 ====================

    /**
     * 查询标准关联的知识库列表
     */
    @SaCheckPermission("review:standard:query")
    @GetMapping("/{standardId}/knowledges")
    public R<List<ReviewKnowledgeVo>> listKnowledges(@NotNull(message = "标准ID不能为空") @PathVariable Long standardId) {
        return R.ok(reviewStandardService.queryLinkedKnowledges(standardId));
    }

    /**
     * 关联知识库
     */
    @SaCheckPermission("review:standard:edit")
    @Log(title = "标准关联知识库", businessType = BusinessType.INSERT)
    @PostMapping("/{standardId}/knowledge/{knowledgeId}")
    public R<Void> linkKnowledge(@NotNull @PathVariable Long standardId, @NotNull @PathVariable Long knowledgeId) {
        reviewStandardService.linkKnowledge(standardId, knowledgeId);
        return R.ok();
    }

    /**
     * 解除关联知识库
     */
    @SaCheckPermission("review:standard:edit")
    @Log(title = "标准解除知识库", businessType = BusinessType.DELETE)
    @DeleteMapping("/{standardId}/knowledge/{knowledgeId}")
    public R<Void> unlinkKnowledge(@NotNull @PathVariable Long standardId, @NotNull @PathVariable Long knowledgeId) {
        reviewStandardService.unlinkKnowledge(standardId, knowledgeId);
        return R.ok();
    }

    // ==================== 规则子资源 ====================

    /**
     * 分页查询标准下的规则列表
     *
     * @param standardId 标准ID
     */
    @SaCheckPermission("review:standard:query")
    @GetMapping("/{standardId}/rules")
    public TableDataInfo<ReviewStandardRuleVo> listRules(@NotNull(message = "标准ID不能为空") @PathVariable Long standardId,
                                                         ReviewStandardRuleBo bo, PageQuery pageQuery) {
        bo.setStandardId(standardId);
        return reviewStandardRuleService.queryPageList(bo, pageQuery);
    }

    /**
     * 新增规则
     */
    @SaCheckPermission("review:standard:edit")
    @Log(title = "审核标准规则", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping("/rule")
    public R<Void> addRule(@Validated(AddGroup.class) @RequestBody ReviewStandardRuleBo bo) {
        return toAjax(reviewStandardRuleService.insertByBo(bo));
    }

    /**
     * 修改规则
     */
    @SaCheckPermission("review:standard:edit")
    @Log(title = "审核标准规则", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/rule")
    public R<Void> editRule(@Validated(EditGroup.class) @RequestBody ReviewStandardRuleBo bo) {
        return toAjax(reviewStandardRuleService.updateByBo(bo));
    }

    /**
     * 删除规则
     *
     * @param ids 主键串
     */
    @SaCheckPermission("review:standard:edit")
    @Log(title = "审核标准规则", businessType = BusinessType.DELETE)
    @DeleteMapping("/rule/{ids}")
    public R<Void> removeRule(@NotEmpty(message = "主键不能为空") @PathVariable Long[] ids) {
        return toAjax(reviewStandardRuleService.deleteWithValidByIds(List.of(ids), true));
    }
}
