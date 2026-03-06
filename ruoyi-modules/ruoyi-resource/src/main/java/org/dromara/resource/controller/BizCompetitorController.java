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
import org.dromara.resource.domain.bo.BizCompetitorBo;
import org.dromara.resource.domain.vo.BizCompetitorVo;
import org.dromara.resource.service.IBizCompetitorService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 竞争公司控制器
 *
 * @author ruoyi
 * @date 2026-03-06
 */
@Tag(description = "resource", name = "竞争公司管理")
@RequiredArgsConstructor
@RestController
@RequestMapping("/resource/competitor")
public class BizCompetitorController extends BaseController {

    private final IBizCompetitorService bizCompetitorService;

    @Operation(summary = "查询竞争公司分页列表")
    @SaCheckPermission("resource:competitor:list")
    @GetMapping("/list")
    public TableDataInfo<BizCompetitorVo> list(BizCompetitorBo bo, PageQuery pageQuery) {
        return bizCompetitorService.queryPageList(bo, pageQuery);
    }

    @Operation(summary = "查询竞争公司详情")
    @SaCheckPermission("resource:competitor:query")
    @GetMapping("/{id}")
    public R<BizCompetitorVo> getInfo(@NotNull(message = "ID不能为空") @PathVariable Long id) {
        return R.ok(bizCompetitorService.queryById(id));
    }

    @Operation(summary = "新增竞争公司")
    @Log(title = "竞争公司管理", businessType = BusinessType.INSERT)
    @SaCheckPermission("resource:competitor:add")
    @PostMapping
    public R<BizCompetitorVo> add(@Validated @RequestBody BizCompetitorBo bo) {
        Boolean result = bizCompetitorService.insertByBo(bo);
        if (result) {
            return R.ok(bizCompetitorService.queryById(bo.getId()));
        }
        return R.fail("新增失败");
    }

    @Operation(summary = "修改竞争公司")
    @Log(title = "竞争公司管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("resource:competitor:edit")
    @PutMapping
    public R<Void> edit(@Validated @RequestBody BizCompetitorBo bo) {
        return toAjax(bizCompetitorService.updateByBo(bo));
    }

    @Operation(summary = "删除竞争公司")
    @Log(title = "竞争公司管理", businessType = BusinessType.DELETE)
    @SaCheckPermission("resource:competitor:remove")
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] ids) {
        return toAjax(bizCompetitorService.deleteWithValidByIds(List.of(ids), true));
    }

}
