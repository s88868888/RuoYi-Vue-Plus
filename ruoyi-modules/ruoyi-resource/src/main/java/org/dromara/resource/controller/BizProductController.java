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
import org.dromara.resource.domain.bo.BizProductBo;
import org.dromara.resource.domain.vo.BizProductVo;
import org.dromara.resource.service.IBizProductService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 产品信息控制器
 *
 * @author ruoyi
 * @date 2026-02-11
 */
@Tag(description = "resource", name = "产品信息管理")
@RequiredArgsConstructor
@RestController
@RequestMapping("/resource/product")
public class BizProductController extends BaseController {

    private final IBizProductService bizProductService;

    /**
     * 查询产品信息分页列表
     */
    @Operation(summary = "查询产品信息分页列表")
    @SaCheckPermission("resource:product:list")
    @GetMapping("/list")
    public TableDataInfo<BizProductVo> list(BizProductBo bo, PageQuery pageQuery) {
        return bizProductService.queryPageList(bo, pageQuery);
    }

    /**
     * 查询产品信息详情
     */
    @Operation(summary = "查询产品信息详情")
    @SaCheckPermission("resource:product:query")
    @GetMapping("/{id}")
    public R<BizProductVo> getInfo(@NotNull(message = "ID不能为空") @PathVariable Long id) {
        return R.ok(bizProductService.queryById(id));
    }

    /**
     * 新增产品信息
     */
    @Operation(summary = "新增产品信息")
    @Log(title = "产品信息管理", businessType = BusinessType.INSERT)
    @SaCheckPermission("resource:product:add")
    @PostMapping
    public R<BizProductVo> add(@Validated @RequestBody BizProductBo bo) {
        Boolean result = bizProductService.insertByBo(bo);
        if (result) {
            return R.ok(bizProductService.queryById(bo.getId()));
        }
        return R.fail("新增失败");
    }

    /**
     * 修改产品信息
     */
    @Operation(summary = "修改产品信息")
    @Log(title = "产品信息管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("resource:product:edit")
    @PutMapping
    public R<Void> edit(@Validated @RequestBody BizProductBo bo) {
        return toAjax(bizProductService.updateByBo(bo));
    }

    /**
     * 删除产品信息
     */
    @Operation(summary = "删除产品信息")
    @Log(title = "产品信息管理", businessType = BusinessType.DELETE)
    @SaCheckPermission("resource:product:remove")
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] ids) {
        return toAjax(bizProductService.deleteWithValidByIds(List.of(ids), true));
    }

}
