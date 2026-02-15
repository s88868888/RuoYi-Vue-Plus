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
import org.dromara.resource.domain.bo.BizFinanceInfoBo;
import org.dromara.resource.domain.vo.BizFinanceInfoVo;
import org.dromara.resource.service.IBizFinanceInfoService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 财务信息控制器
 *
 * @author ruoyi
 * @date 2026-02-12
 */
@Tag(description = "resource", name = "财务信息管理")
@RequiredArgsConstructor
@RestController
@RequestMapping("/resource/finance")
public class BizFinanceInfoController extends BaseController {

    private final IBizFinanceInfoService bizFinanceInfoService;

    /**
     * 查询财务信息分页列表
     */
    @Operation(summary = "查询财务信息分页列表")
    @SaCheckPermission("resource:finance:list")
    @GetMapping("/list")
    public TableDataInfo<BizFinanceInfoVo> list(BizFinanceInfoBo bo, PageQuery pageQuery) {
        return bizFinanceInfoService.queryPageList(bo, pageQuery);
    }

    /**
     * 查询财务信息详情
     */
    @Operation(summary = "查询财务信息详情")
    @SaCheckPermission("resource:finance:query")
    @GetMapping("/{id}")
    public R<BizFinanceInfoVo> getInfo(@NotNull(message = "ID不能为空") @PathVariable Long id) {
        return R.ok(bizFinanceInfoService.queryById(id));
    }

    /**
     * 新增财务信息
     */
    @Operation(summary = "新增财务信息")
    @Log(title = "财务信息管理", businessType = BusinessType.INSERT)
    @SaCheckPermission("resource:finance:add")
    @PostMapping
    public R<BizFinanceInfoVo> add(@Validated @RequestBody BizFinanceInfoBo bo) {
        Boolean result = bizFinanceInfoService.insertByBo(bo);
        if (result) {
            return R.ok(bizFinanceInfoService.queryById(bo.getId()));
        }
        return R.fail("新增失败");
    }

    /**
     * 修改财务信息
     */
    @Operation(summary = "修改财务信息")
    @Log(title = "财务信息管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("resource:finance:edit")
    @PutMapping
    public R<Void> edit(@Validated @RequestBody BizFinanceInfoBo bo) {
        return toAjax(bizFinanceInfoService.updateByBo(bo));
    }

    /**
     * 删除财务信息
     */
    @Operation(summary = "删除财务信息")
    @Log(title = "财务信息管理", businessType = BusinessType.DELETE)
    @SaCheckPermission("resource:finance:remove")
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] ids) {
        return toAjax(bizFinanceInfoService.deleteWithValidByIds(List.of(ids), true));
    }

}
