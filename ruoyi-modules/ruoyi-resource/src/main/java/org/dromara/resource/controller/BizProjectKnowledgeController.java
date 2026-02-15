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
import org.dromara.resource.domain.bo.BizProjectKnowledgeBo;
import org.dromara.resource.domain.vo.BizProjectKnowledgeVo;
import org.dromara.resource.service.IBizProjectKnowledgeService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 项目知识控制器
 *
 * @author ruoyi
 * @date 2026-02-12
 */
@Tag(description = "resource", name = "项目知识管理")
@RequiredArgsConstructor
@RestController
@RequestMapping("/resource/knowledge")
public class BizProjectKnowledgeController extends BaseController {

    private final IBizProjectKnowledgeService bizProjectKnowledgeService;

    /**
     * 查询项目知识分页列表
     */
    @Operation(summary = "查询项目知识分页列表")
    @SaCheckPermission("resource:knowledge:list")
    @GetMapping("/list")
    public TableDataInfo<BizProjectKnowledgeVo> list(BizProjectKnowledgeBo bo, PageQuery pageQuery) {
        return bizProjectKnowledgeService.queryPageList(bo, pageQuery);
    }

    /**
     * 查询项目知识详情
     */
    @Operation(summary = "查询项目知识详情")
    @SaCheckPermission("resource:knowledge:query")
    @GetMapping("/{id}")
    public R<BizProjectKnowledgeVo> getInfo(@NotNull(message = "ID不能为空") @PathVariable Long id) {
        return R.ok(bizProjectKnowledgeService.queryById(id));
    }

    /**
     * 新增项目知识
     */
    @Operation(summary = "新增项目知识")
    @Log(title = "项目知识管理", businessType = BusinessType.INSERT)
    @SaCheckPermission("resource:knowledge:add")
    @PostMapping
    public R<BizProjectKnowledgeVo> add(@Validated @RequestBody BizProjectKnowledgeBo bo) {
        Boolean result = bizProjectKnowledgeService.insertByBo(bo);
        if (result) {
            return R.ok(bizProjectKnowledgeService.queryById(bo.getId()));
        }
        return R.fail("新增失败");
    }

    /**
     * 修改项目知识
     */
    @Operation(summary = "修改项目知识")
    @Log(title = "项目知识管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("resource:knowledge:edit")
    @PutMapping
    public R<Void> edit(@Validated @RequestBody BizProjectKnowledgeBo bo) {
        return toAjax(bizProjectKnowledgeService.updateByBo(bo));
    }

    /**
     * 删除项目知识
     */
    @Operation(summary = "删除项目知识")
    @Log(title = "项目知识管理", businessType = BusinessType.DELETE)
    @SaCheckPermission("resource:knowledge:remove")
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] ids) {
        return toAjax(bizProjectKnowledgeService.deleteWithValidByIds(List.of(ids), true));
    }

}
