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
import org.dromara.resource.domain.bo.BizBidProjectBo;
import org.dromara.resource.domain.dto.BidProjectStep1Dto;
import org.dromara.resource.domain.dto.BidProjectStep2Dto;
import org.dromara.resource.domain.vo.BizBidProjectVo;
import org.dromara.resource.service.IAiAnalysisService;
import org.dromara.resource.service.IBizBidProjectService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 招标项目控制器
 *
 * @author ruoyi
 * @date 2026-02-23
 */
@Tag(description = "bid", name = "招标项目管理")
@RequiredArgsConstructor
@RestController
@RequestMapping("/bid/project")
public class BizBidProjectController extends BaseController {

    private final IBizBidProjectService bizBidProjectService;
    private final IAiAnalysisService aiAnalysisService;

    /**
     * 查询招标项目分页列表
     */
    @Operation(summary = "查询招标项目分页列表")
    @SaCheckPermission("bid:project:list")
    @GetMapping("/list")
    public TableDataInfo<BizBidProjectVo> list(BizBidProjectBo bo, PageQuery pageQuery) {
        return bizBidProjectService.queryPageList(bo, pageQuery);
    }

    /**
     * 查询招标项目详情
     */
    @Operation(summary = "查询招标项目详情")
    @SaCheckPermission("bid:project:query")
    @GetMapping("/{id}")
    public R<BizBidProjectVo> getInfo(@NotNull(message = "ID不能为空") @PathVariable Long id) {
        return R.ok(bizBidProjectService.queryById(id));
    }

    /**
     * 新增招标项目
     */
    @Operation(summary = "新增招标项目")
    @Log(title = "招标项目管理", businessType = BusinessType.INSERT)
    @SaCheckPermission("bid:project:add")
    @PostMapping
    public R<BizBidProjectVo> add(@Validated @RequestBody BizBidProjectBo bo) {
        Boolean result = bizBidProjectService.insertByBo(bo);
        if (result) {
            return R.ok(bizBidProjectService.queryById(bo.getId()));
        }
        return R.fail("新增失败");
    }

    /**
     * 修改招标项目
     */
    @Operation(summary = "修改招标项目")
    @Log(title = "招标项目管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("bid:project:edit")
    @PutMapping
    public R<Void> edit(@Validated @RequestBody BizBidProjectBo bo) {
        return toAjax(bizBidProjectService.updateByBo(bo));
    }

    /**
     * 删除招标项目
     */
    @Operation(summary = "删除招标项目")
    @Log(title = "招标项目管理", businessType = BusinessType.DELETE)
    @SaCheckPermission("bid:project:remove")
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] ids) {
        return toAjax(bizBidProjectService.deleteWithValidByIds(List.of(ids), true));
    }

    /**
     * 第一步：保存基本信息
     */
    @Operation(summary = "保存招标项目基本信息（第一步）")
    @Log(title = "招标项目管理", businessType = BusinessType.INSERT)
    @SaCheckPermission("bid:project:add")
    @PostMapping("/step1")
    public R<BizBidProjectVo> saveStep1(@Validated @RequestBody BidProjectStep1Dto dto) {
        BizBidProjectBo bo = new BizBidProjectBo();
        bo.setId(dto.getId());
        bo.setDeptId(dto.getDeptId());
        bo.setProjectName(dto.getProjectName());
        bo.setBidOrg(dto.getBidOrg());
        bo.setProjectType(dto.getProjectType());
        bo.setBudgetAmount(dto.getBudgetAmount());
        bo.setPublishDate(dto.getPublishDate());
        bo.setDeadline(dto.getDeadline());
        bo.setMatchDegree(dto.getMatchDegree());
        bo.setStatus(dto.getStatus());
        bo.setProjectRegion(dto.getProjectRegion());
        bo.setBidMethod(dto.getBidMethod());
        bo.setContactPerson(dto.getContactPerson());
        bo.setContactPhone(dto.getContactPhone());
        bo.setProjectSource(dto.getProjectSource());
        bo.setSourceUrl(dto.getSourceUrl());
        bo.setProjectDesc(dto.getProjectDesc());
        bo.setAttachments(dto.getAttachments());
        bo.setAttachmentName(dto.getAttachmentName());
        bo.setRemark(dto.getRemark());
        bo.setAiAnalysisStatus("pending");

        if (dto.getId() != null) {
            bizBidProjectService.updateByBo(bo);
        } else {
            bizBidProjectService.insertByBo(bo);
        }

        return R.ok(bizBidProjectService.queryById(bo.getId()));
    }

    /**
     * 第二步：AI分析
     */
    @Operation(summary = "执行AI分析（第二步）")
    @Log(title = "招标项目管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("bid:project:edit")
    @PostMapping("/step2")
    public R<String> analyzeStep2(@Validated @RequestBody BidProjectStep2Dto dto) {
        // 保存提示词
        BizBidProjectBo bo = new BizBidProjectBo();
        bo.setId(dto.getProjectId());
        bo.setAiPrompt(dto.getAiPrompt());
        bizBidProjectService.updateByBo(bo);

        if (dto.getAsync()) {
            // 异步分析
            aiAnalysisService.analyzeBidProjectAsync(dto.getProjectId(), dto.getAiPrompt());
            return R.ok("AI分析任务已提交，请稍后查看结果");
        } else {
            // 同步分析
            String result = aiAnalysisService.analyzeBidProject(dto.getProjectId(), dto.getAiPrompt());
            return R.ok(result);
        }
    }

}
