package org.dromara.resource.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.resource.domain.BizPersonnelCertificate;
import org.dromara.resource.domain.BizPersonnelProject;
import org.dromara.resource.domain.bo.BizPersonnelBo;
import org.dromara.resource.domain.bo.BizPersonnelCertificateBo;
import org.dromara.resource.domain.bo.BizPersonnelProjectBo;
import org.dromara.resource.domain.vo.BizPersonnelCertificateVo;
import org.dromara.resource.domain.vo.BizPersonnelProjectVo;
import org.dromara.resource.domain.vo.BizPersonnelVo;
import org.dromara.resource.mapper.BizPersonnelCertificateMapper;
import org.dromara.resource.mapper.BizPersonnelProjectMapper;
import org.dromara.resource.service.IBizPersonnelService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 人员信息控制器
 *
 * @author ruoyi
 * @date 2026-02-11
 */
@Tag(description = "resource", name = "人员信息管理")
@RequiredArgsConstructor
@RestController
@RequestMapping("/resource/personnel")
public class BizPersonnelController extends BaseController {

    private final IBizPersonnelService bizPersonnelService;
    private final BizPersonnelCertificateMapper certificateMapper;
    private final BizPersonnelProjectMapper projectMapper;

    // ==================== 人员基本信息 ====================

    /**
     * 查询人员信息分页列表
     */
    @Operation(summary = "查询人员信息分页列表")
    @SaCheckPermission("resource:personnel:list")
    @GetMapping("/list")
    public TableDataInfo<BizPersonnelVo> list(BizPersonnelBo bo, PageQuery pageQuery) {
        return bizPersonnelService.queryPageList(bo, pageQuery);
    }

    /**
     * 根据部门ID查询人员列表
     */
    @Operation(summary = "根据部门ID查询人员列表")
    @SaCheckPermission("resource:personnel:list")
    @GetMapping("/byDept/{deptId}")
    public R<List<BizPersonnelVo>> getByDeptId(@NotNull(message = "部门ID不能为空") @PathVariable Long deptId) {
        return R.ok(bizPersonnelService.queryByDeptId(deptId));
    }

    /**
     * 查询人员信息详情
     */
    @Operation(summary = "查询人员信息详情")
    @SaCheckPermission("resource:personnel:query")
    @GetMapping("/{id}")
    public R<BizPersonnelVo> getInfo(@NotNull(message = "ID不能为空") @PathVariable Long id) {
        return R.ok(bizPersonnelService.queryById(id));
    }

    /**
     * 新增人员信息
     */
    @Operation(summary = "新增人员信息")
    @Log(title = "人员信息管理", businessType = BusinessType.INSERT)
    @SaCheckPermission("resource:personnel:add")
    @PostMapping
    public R<BizPersonnelVo> add(@Validated @RequestBody BizPersonnelBo bo) {
        Boolean result = bizPersonnelService.insertByBo(bo);
        if (result) {
            return R.ok(bizPersonnelService.queryById(bo.getId()));
        }
        return R.fail("新增失败");
    }

    /**
     * 修改人员信息
     */
    @Operation(summary = "修改人员信息")
    @Log(title = "人员信息管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("resource:personnel:edit")
    @PutMapping
    public R<Void> edit(@Validated @RequestBody BizPersonnelBo bo) {
        return toAjax(bizPersonnelService.updateByBo(bo));
    }

    /**
     * 删除人员信息
     */
    @Operation(summary = "删除人员信息")
    @Log(title = "人员信息管理", businessType = BusinessType.DELETE)
    @SaCheckPermission("resource:personnel:remove")
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] ids) {
        return toAjax(bizPersonnelService.deleteWithValidByIds(List.of(ids), true));
    }

    // ==================== 资格证书 ====================

    /**
     * 查询人员资格证书列表
     */
    @Operation(summary = "查询人员资格证书列表")
    @SaCheckPermission("resource:personnel:query")
    @GetMapping("/certificate/{personnelId}")
    public R<List<BizPersonnelCertificateVo>> getCertificateList(@PathVariable Long personnelId) {
        LambdaQueryWrapper<BizPersonnelCertificate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizPersonnelCertificate::getPersonnelId, personnelId);
        wrapper.orderByDesc(BizPersonnelCertificate::getCreateTime);
        return R.ok(certificateMapper.selectVoList(wrapper));
    }

    /**
     * 新增资格证书
     */
    @Operation(summary = "新增资格证书")
    @Log(title = "人员资格证书", businessType = BusinessType.INSERT)
    @SaCheckPermission("resource:personnel:edit")
    @PostMapping("/certificate")
    public R<Void> addCertificate(@Validated @RequestBody BizPersonnelCertificateBo bo) {
        BizPersonnelCertificate entity = MapstructUtils.convert(bo, BizPersonnelCertificate.class);
        return toAjax(certificateMapper.insert(entity));
    }

    /**
     * 修改资格证书
     */
    @Operation(summary = "修改资格证书")
    @Log(title = "人员资格证书", businessType = BusinessType.UPDATE)
    @SaCheckPermission("resource:personnel:edit")
    @PutMapping("/certificate")
    public R<Void> editCertificate(@Validated @RequestBody BizPersonnelCertificateBo bo) {
        BizPersonnelCertificate entity = MapstructUtils.convert(bo, BizPersonnelCertificate.class);
        return toAjax(certificateMapper.updateById(entity));
    }

    /**
     * 删除资格证书
     */
    @Operation(summary = "删除资格证书")
    @Log(title = "人员资格证书", businessType = BusinessType.DELETE)
    @SaCheckPermission("resource:personnel:edit")
    @DeleteMapping("/certificate/{ids}")
    public R<Void> removeCertificate(@NotEmpty(message = "主键不能为空") @PathVariable Long[] ids) {
        return toAjax(certificateMapper.deleteByIds(List.of(ids)));
    }

    // ==================== 项目经验 ====================

    /**
     * 查询人员项目经验列表
     */
    @Operation(summary = "查询人员项目经验列表")
    @SaCheckPermission("resource:personnel:query")
    @GetMapping("/project/{personnelId}")
    public R<List<BizPersonnelProjectVo>> getProjectList(@PathVariable Long personnelId) {
        LambdaQueryWrapper<BizPersonnelProject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizPersonnelProject::getPersonnelId, personnelId);
        wrapper.orderByDesc(BizPersonnelProject::getCreateTime);
        return R.ok(projectMapper.selectVoList(wrapper));
    }

    /**
     * 新增项目经验
     */
    @Operation(summary = "新增项目经验")
    @Log(title = "人员项目经验", businessType = BusinessType.INSERT)
    @SaCheckPermission("resource:personnel:edit")
    @PostMapping("/project")
    public R<Void> addProject(@Validated @RequestBody BizPersonnelProjectBo bo) {
        BizPersonnelProject entity = MapstructUtils.convert(bo, BizPersonnelProject.class);
        return toAjax(projectMapper.insert(entity));
    }

    /**
     * 修改项目经验
     */
    @Operation(summary = "修改项目经验")
    @Log(title = "人员项目经验", businessType = BusinessType.UPDATE)
    @SaCheckPermission("resource:personnel:edit")
    @PutMapping("/project")
    public R<Void> editProject(@Validated @RequestBody BizPersonnelProjectBo bo) {
        BizPersonnelProject entity = MapstructUtils.convert(bo, BizPersonnelProject.class);
        return toAjax(projectMapper.updateById(entity));
    }

    /**
     * 删除项目经验
     */
    @Operation(summary = "删除项目经验")
    @Log(title = "人员项目经验", businessType = BusinessType.DELETE)
    @SaCheckPermission("resource:personnel:edit")
    @DeleteMapping("/project/{ids}")
    public R<Void> removeProject(@NotEmpty(message = "主键不能为空") @PathVariable Long[] ids) {
        return toAjax(projectMapper.deleteByIds(List.of(ids)));
    }

}
