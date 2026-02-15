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
import org.dromara.resource.domain.bo.BizPatentMedalBo;
import org.dromara.resource.domain.vo.BizPatentMedalVo;
import org.dromara.resource.service.IBizPatentMedalService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 专利奖章控制器
 *
 * @author ruoyi
 * @date 2026-02-11
 */
@Tag(description = "resource", name = "专利奖章管理")
@RequiredArgsConstructor
@RestController
@RequestMapping("/resource/patentMedal")
public class BizPatentMedalController extends BaseController {

    private final IBizPatentMedalService bizPatentMedalService;

    /**
     * 查询专利奖章分页列表
     */
    @Operation(summary = "查询专利奖章分页列表")
    @SaCheckPermission("resource:patentMedal:list")
    @GetMapping("/list")
    public TableDataInfo<BizPatentMedalVo> list(BizPatentMedalBo bo, PageQuery pageQuery) {
        return bizPatentMedalService.queryPageList(bo, pageQuery);
    }

    /**
     * 查询专利奖章详情
     */
    @Operation(summary = "查询专利奖章详情")
    @SaCheckPermission("resource:patentMedal:query")
    @GetMapping("/{id}")
    public R<BizPatentMedalVo> getInfo(@NotNull(message = "ID不能为空") @PathVariable Long id) {
        return R.ok(bizPatentMedalService.queryById(id));
    }

    /**
     * 新增专利奖章
     */
    @Operation(summary = "新增专利奖章")
    @Log(title = "专利奖章管理", businessType = BusinessType.INSERT)
    @SaCheckPermission("resource:patentMedal:add")
    @PostMapping
    public R<BizPatentMedalVo> add(@Validated @RequestBody BizPatentMedalBo bo) {
        Boolean result = bizPatentMedalService.insertByBo(bo);
        if (result) {
            return R.ok(bizPatentMedalService.queryById(bo.getId()));
        }
        return R.fail("新增失败");
    }

    /**
     * 修改专利奖章
     */
    @Operation(summary = "修改专利奖章")
    @Log(title = "专利奖章管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("resource:patentMedal:edit")
    @PutMapping
    public R<Void> edit(@Validated @RequestBody BizPatentMedalBo bo) {
        return toAjax(bizPatentMedalService.updateByBo(bo));
    }

    /**
     * 删除专利奖章
     */
    @Operation(summary = "删除专利奖章")
    @Log(title = "专利奖章管理", businessType = BusinessType.DELETE)
    @SaCheckPermission("resource:patentMedal:remove")
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] ids) {
        return toAjax(bizPatentMedalService.deleteWithValidByIds(List.of(ids), true));
    }

}
