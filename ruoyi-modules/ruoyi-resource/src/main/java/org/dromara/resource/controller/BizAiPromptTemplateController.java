package org.dromara.resource.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.resource.domain.bo.BizAiPromptTemplateBo;
import org.dromara.resource.domain.vo.BizAiPromptTemplateVo;
import org.dromara.resource.service.IBizAiPromptTemplateService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI提示词模板控制器
 *
 * @author ruoyi
 * @date 2026-02-26
 */
@Tag(description = "bid", name = "AI提示词模板管理")
@RequiredArgsConstructor
@RestController
@RequestMapping("/bid/promptTemplate")
public class BizAiPromptTemplateController extends BaseController {

    private final IBizAiPromptTemplateService bizAiPromptTemplateService;

    /**
     * 查询AI提示词模板分页列表
     */
    @Operation(summary = "查询AI提示词模板分页列表")
    @SaCheckPermission("bid:promptTemplate:list")
    @GetMapping("/list")
    public TableDataInfo<BizAiPromptTemplateVo> list(BizAiPromptTemplateBo bo, PageQuery pageQuery) {
        return bizAiPromptTemplateService.queryPageList(bo, pageQuery);
    }

    /**
     * 查询AI提示词模板详情
     */
    @Operation(summary = "查询AI提示词模板详情")
    @SaCheckPermission("bid:promptTemplate:query")
    @GetMapping("/{id}")
    public R<BizAiPromptTemplateVo> getInfo(@NotNull(message = "ID不能为空") @PathVariable Long id) {
        return R.ok(bizAiPromptTemplateService.queryById(id));
    }

    /**
     * 根据类型查询启用的模板列表
     */
    @Operation(summary = "根据类型查询启用的模板列表")
    @GetMapping("/listByType")
    public R<List<BizAiPromptTemplateVo>> listByType(@RequestParam(required = false) String templateType) {
        return R.ok(bizAiPromptTemplateService.queryByType(templateType));
    }

    /**
     * 新增AI提示词模板
     */
    @Operation(summary = "新增AI提示词模板")
    @Log(title = "AI提示词模板管理", businessType = BusinessType.INSERT)
    @SaCheckPermission("bid:promptTemplate:add")
    @PostMapping
    public R<BizAiPromptTemplateVo> add(@Validated @RequestBody BizAiPromptTemplateBo bo) {
        Boolean result = bizAiPromptTemplateService.insertByBo(bo);
        if (result) {
            return R.ok(bizAiPromptTemplateService.queryById(bo.getId()));
        }
        return R.fail("新增失败");
    }

    /**
     * 修改AI提示词模板
     */
    @Operation(summary = "修改AI提示词模板")
    @Log(title = "AI提示词模板管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("bid:promptTemplate:edit")
    @PutMapping
    public R<Void> edit(@Validated @RequestBody BizAiPromptTemplateBo bo) {
        return toAjax(bizAiPromptTemplateService.updateByBo(bo));
    }

    /**
     * 删除AI提示词模板
     */
    @Operation(summary = "删除AI提示词模板")
    @Log(title = "AI提示词模板管理", businessType = BusinessType.DELETE)
    @SaCheckPermission("bid:promptTemplate:remove")
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] ids) {
        return toAjax(bizAiPromptTemplateService.deleteWithValidByIds(List.of(ids), true));
    }

}
