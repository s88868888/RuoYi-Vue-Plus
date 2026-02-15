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
import org.dromara.resource.domain.bo.BizPerformanceBo;
import org.dromara.resource.domain.vo.BizPerformanceVo;
import org.dromara.resource.service.IBizPerformanceService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 业绩案例控制器
 *
 * @author ruoyi
 * @date 2026-02-11
 */
@Tag(description = "resource", name = "业绩案例管理")
@RequiredArgsConstructor
@RestController
@RequestMapping("/resource/performance")
public class BizPerformanceController extends BaseController {

    private final IBizPerformanceService bizPerformanceService;

    @Operation(summary = "查询业绩案例分页列表")
    @SaCheckPermission("resource:performance:list")
    @GetMapping("/list")
    public TableDataInfo<BizPerformanceVo> list(BizPerformanceBo bo, PageQuery pageQuery) {
        return bizPerformanceService.queryPageList(bo, pageQuery);
    }

    @Operation(summary = "查询业绩案例详情")
    @SaCheckPermission("resource:performance:query")
    @GetMapping("/{id}")
    public R<BizPerformanceVo> getInfo(@NotNull(message = "ID不能为空") @PathVariable Long id) {
        return R.ok(bizPerformanceService.queryById(id));
    }

    @Operation(summary = "新增业绩案例")
    @Log(title = "业绩案例管理", businessType = BusinessType.INSERT)
    @SaCheckPermission("resource:performance:add")
    @PostMapping
    public R<BizPerformanceVo> add(@Validated @RequestBody BizPerformanceBo bo) {
        Boolean result = bizPerformanceService.insertByBo(bo);
        if (result) {
            return R.ok(bizPerformanceService.queryById(bo.getId()));
        }
        return R.fail("新增失败");
    }

    @Operation(summary = "修改业绩案例")
    @Log(title = "业绩案例管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("resource:performance:edit")
    @PutMapping
    public R<Void> edit(@Validated @RequestBody BizPerformanceBo bo) {
        return toAjax(bizPerformanceService.updateByBo(bo));
    }

    @Operation(summary = "删除业绩案例")
    @Log(title = "业绩案例管理", businessType = BusinessType.DELETE)
    @SaCheckPermission("resource:performance:remove")
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] ids) {
        return toAjax(bizPerformanceService.deleteWithValidByIds(List.of(ids), true));
    }

}
