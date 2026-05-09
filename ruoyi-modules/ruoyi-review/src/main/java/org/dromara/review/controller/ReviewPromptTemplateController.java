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
import org.dromara.review.domain.bo.ReviewPromptTemplateBo;
import org.dromara.review.domain.vo.ReviewPromptTemplateVo;
import org.dromara.review.service.IReviewPromptTemplateService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 审核提示词模板
 *
 * @author LionLi
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/review/prompt")
public class ReviewPromptTemplateController extends BaseController {

    private final IReviewPromptTemplateService reviewPromptTemplateService;

    /**
     * 按类型查询模板列表
     *
     * @param type 模板类型
     */
    @SaCheckPermission("review:prompt:list")
    @GetMapping("/list")
    public R<List<ReviewPromptTemplateVo>> list(@RequestParam(required = false) String type) {
        return R.ok(reviewPromptTemplateService.queryListByType(type));
    }

    /**
     * 获取模板详情
     *
     * @param id 主键
     */
    @SaCheckPermission("review:prompt:query")
    @GetMapping("/{id}")
    public R<ReviewPromptTemplateVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable Long id) {
        return R.ok(reviewPromptTemplateService.queryById(id));
    }

    /**
     * 新增模板
     */
    @SaCheckPermission("review:prompt:add")
    @Log(title = "审核提示词模板", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody ReviewPromptTemplateBo bo) {
        return toAjax(reviewPromptTemplateService.insertByBo(bo));
    }

    /**
     * 修改模板
     */
    @SaCheckPermission("review:prompt:edit")
    @Log(title = "审核提示词模板", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody ReviewPromptTemplateBo bo) {
        return toAjax(reviewPromptTemplateService.updateByBo(bo));
    }

    /**
     * 删除模板
     *
     * @param ids 主键串
     */
    @SaCheckPermission("review:prompt:remove")
    @Log(title = "审核提示词模板", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] ids) {
        return toAjax(reviewPromptTemplateService.deleteWithValidByIds(List.of(ids), true));
    }
}
