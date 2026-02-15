package org.dromara.resource.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.resource.domain.bo.BizCompanyInfoBo;
import org.dromara.resource.domain.vo.BizCompanyInfoVo;
import org.dromara.resource.domain.vo.CompanyListVo;
import org.dromara.resource.service.IBizCompanyInfoService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 企业信息控制器
 *
 * @author ruoyi
 * @date 2026-02-10
 */
@Tag(description = "resource", name = "企业信息管理")
@RequiredArgsConstructor
@RestController
@RequestMapping("/resource/companyInfo")
public class BizCompanyInfoController extends BaseController {

    private final IBizCompanyInfoService bizCompanyInfoService;

    /**
     * 查询公司卡片列表
     */
    @Operation(summary = "查询公司卡片列表")
    @SaCheckPermission("resource:companyInfo:list")
    @GetMapping("/list")
    public TableDataInfo<CompanyListVo> list(
            @RequestParam(required = false) String deptName,
            @RequestParam(required = false) String status,
            PageQuery pageQuery) {
        return bizCompanyInfoService.queryCompanyList(deptName, status, pageQuery);
    }

    /**
     * 根据部门ID获取企业详细信息
     */
    @Operation(summary = "根据部门ID获取企业详细信息")
    @SaCheckPermission("resource:companyInfo:query")
    @GetMapping("/{deptId}")
    public R<BizCompanyInfoVo> getInfo(@NotNull(message = "部门ID不能为空") @PathVariable Long deptId) {
        BizCompanyInfoVo vo = bizCompanyInfoService.queryByDeptId(deptId);
        if (vo == null) {
            // 如果不存在，返回一个空对象，前端可以据此判断是否需要新建
            vo = new BizCompanyInfoVo();
            vo.setDeptId(deptId);
        }
        return R.ok(vo);
    }

    /**
     * 新增企业信息
     */
    @Operation(summary = "新增企业信息")
    @Log(title = "企业信息管理", businessType = BusinessType.INSERT)
    @SaCheckPermission("resource:companyInfo:add")
    @PostMapping
    public R<Void> add(@Validated(AddGroup.class) @RequestBody BizCompanyInfoBo bo) {
        return toAjax(bizCompanyInfoService.insertByBo(bo));
    }

    /**
     * 修改企业信息
     */
    @Operation(summary = "修改企业信息")
    @Log(title = "企业信息管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("resource:companyInfo:edit")
    @PutMapping
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody BizCompanyInfoBo bo) {
        return toAjax(bizCompanyInfoService.updateByBo(bo));
    }

    /**
     * 删除企业信息
     */
    @Operation(summary = "删除企业信息")
    @Log(title = "企业信息管理", businessType = BusinessType.DELETE)
    @SaCheckPermission("resource:companyInfo:remove")
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] ids) {
        return toAjax(bizCompanyInfoService.deleteWithValidByIds(List.of(ids), true));
    }

}
