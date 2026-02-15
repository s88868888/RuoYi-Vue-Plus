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
import org.dromara.resource.domain.bo.BizQualificationBo;
import org.dromara.resource.domain.vo.BizQualificationVo;
import org.dromara.resource.service.IBizQualificationService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 企业资质控制器
 *
 * @author ruoyi
 * @date 2026-02-11
 */
@Tag(description = "resource", name = "企业资质管理")
@RequiredArgsConstructor
@RestController
@RequestMapping("/resource/qualification")
public class BizQualificationController extends BaseController {

    private final IBizQualificationService bizQualificationService;

    /**
     * 查询企业资质分页列表
     */
    @Operation(summary = "查询企业资质分页列表")
    @SaCheckPermission("resource:qualification:list")
    @GetMapping("/list")
    public TableDataInfo<BizQualificationVo> list(BizQualificationBo bo, PageQuery pageQuery) {
        return bizQualificationService.queryPageList(bo, pageQuery);
    }

    /**
     * 查询企业资质详情
     */
    @Operation(summary = "查询企业资质详情")
    @SaCheckPermission("resource:qualification:query")
    @GetMapping("/{id}")
    public R<BizQualificationVo> getInfo(@NotNull(message = "ID不能为空") @PathVariable Long id) {
        return R.ok(bizQualificationService.queryById(id));
    }

    /**
     * 新增企业资质
     */
    @Operation(summary = "新增企业资质")
    @Log(title = "企业资质管理", businessType = BusinessType.INSERT)
    @SaCheckPermission("resource:qualification:add")
    @PostMapping
    public R<BizQualificationVo> add(@Validated @RequestBody BizQualificationBo bo) {
        Boolean result = bizQualificationService.insertByBo(bo);
        if (result) {
            return R.ok(bizQualificationService.queryById(bo.getId()));
        }
        return R.fail("新增失败");
    }

    /**
     * 修改企业资质
     */
    @Operation(summary = "修改企业资质")
    @Log(title = "企业资质管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("resource:qualification:edit")
    @PutMapping
    public R<Void> edit(@Validated @RequestBody BizQualificationBo bo) {
        return toAjax(bizQualificationService.updateByBo(bo));
    }

    /**
     * 删除企业资质
     */
    @Operation(summary = "删除企业资质")
    @Log(title = "企业资质管理", businessType = BusinessType.DELETE)
    @SaCheckPermission("resource:qualification:remove")
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] ids) {
        return toAjax(bizQualificationService.deleteWithValidByIds(List.of(ids), true));
    }

}
