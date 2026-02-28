package org.dromara.resource.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.resource.domain.bo.BizBidSubmissionBo;
import org.dromara.resource.domain.vo.BidSubmissionProgressVo;
import org.dromara.resource.domain.vo.BizBidSubmissionVo;
import org.dromara.resource.domain.vo.BizSubmissionChapterVo;
import org.dromara.resource.service.IBizBidSubmissionService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 投标项目管理
 *
 * @author ruoyi
 * @date 2026-02-26
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/bid/submission")
public class BizBidSubmissionController extends BaseController {

    private final IBizBidSubmissionService bizBidSubmissionService;

    /**
     * 查询投标项目列表
     */
    @SaCheckPermission("bid:submission:list")
    @GetMapping("/list")
    public TableDataInfo<BizBidSubmissionVo> list(BizBidSubmissionBo bo, PageQuery pageQuery) {
        return bizBidSubmissionService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出投标项目列表
     */
    @SaCheckPermission("bid:submission:export")
    @Log(title = "投标项目", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(BizBidSubmissionBo bo, HttpServletResponse response) {
        List<BizBidSubmissionVo> list = bizBidSubmissionService.queryList(bo);
        ExcelUtil.exportExcel(list, "投标项目", BizBidSubmissionVo.class, response);
    }

    /**
     * 获取投标项目详细信息
     *
     * @param id 主键
     */
    @SaCheckPermission("bid:submission:query")
    @GetMapping("/{id}")
    public R<BizBidSubmissionVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable Long id) {
        return R.ok(bizBidSubmissionService.queryById(id));
    }

    /**
     * 新增投标项目
     */
    @SaCheckPermission("bid:submission:add")
    @Log(title = "投标项目", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody BizBidSubmissionBo bo) {
        return toAjax(bizBidSubmissionService.insertByBo(bo));
    }

    /**
     * 从招标项目创建投标项目
     *
     * @param bidProjectId 招标项目ID
     * @param bo 投标项目业务对象
     */
    @SaCheckPermission("bid:submission:add")
    @Log(title = "投标项目", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping("/createFrom/{bidProjectId}")
    public R<Long> createFromBidProject(
        @NotNull(message = "招标项目ID不能为空") @PathVariable Long bidProjectId,
        @Validated(AddGroup.class) @RequestBody BizBidSubmissionBo bo) {
        Long submissionId = bizBidSubmissionService.createFromBidProject(bidProjectId, bo);
        return R.ok(submissionId);
    }

    /**
     * 修改投标项目
     */
    @SaCheckPermission("bid:submission:edit")
    @Log(title = "投标项目", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody BizBidSubmissionBo bo) {
        return toAjax(bizBidSubmissionService.updateByBo(bo));
    }

    /**
     * 删除投标项目
     *
     * @param ids 主键串
     */
    @SaCheckPermission("bid:submission:remove")
    @Log(title = "投标项目", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] ids) {
        return toAjax(bizBidSubmissionService.deleteWithValidByIds(List.of(ids), true));
    }

    /**
     * 第一步：保存公司关联和生成配置
     *
     * @param id 投标项目ID
     * @param bo 配置信息
     */
    @SaCheckPermission("bid:submission:edit")
    @Log(title = "投标项目", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/step1/saveConfig")
    public R<Void> saveStep1Config(@NotNull(message = "主键不能为空") @PathVariable Long id,
                                    @RequestBody BizBidSubmissionBo bo) {
        bo.setId(id);
        return toAjax(bizBidSubmissionService.saveStep1Config(bo));
    }

    /**
     * 第二步：生成章节结构
     *
     * @param id 投标项目ID
     */
    @SaCheckPermission("bid:submission:generate")
    @Log(title = "投标项目", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/step2/generateStructure")
    public R<Void> generateChapterStructure(@NotNull(message = "主键不能为空") @PathVariable Long id) {
        return toAjax(bizBidSubmissionService.generateChapterStructure(id));
    }

    /**
     * 第二步：获取章节树结构
     *
     * @param id 投标项目ID
     */
    @SaCheckPermission("bid:submission:query")
    @GetMapping("/{id}/step2/chapterTree")
    public R<List<BizSubmissionChapterVo>> getChapterTree(@NotNull(message = "主键不能为空") @PathVariable Long id) {
        return R.ok(bizBidSubmissionService.getChapterTree(id));
    }

    /**
     * 第二步：开始生成标书内容
     *
     * @param id 投标项目ID
     */
    @SaCheckPermission("bid:submission:generate")
    @Log(title = "投标项目", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/step2/generateContent")
    public R<Void> startContentGeneration(@NotNull(message = "主键不能为空") @PathVariable Long id) {
        return toAjax(bizBidSubmissionService.startContentGeneration(id));
    }

    /**
     * 第三步：导出标书文件
     *
     * @param id 投标项目ID
     * @param response HTTP响应
     */
    @SaCheckPermission("bid:submission:export")
    @Log(title = "投标项目", businessType = BusinessType.EXPORT)
    @GetMapping("/{id}/step3/export")
    public void exportDocument(@NotNull(message = "主键不能为空") @PathVariable Long id,
                                HttpServletResponse response) {
        bizBidSubmissionService.exportDocument(id, response);
    }

    /**
     * 获取生成进度
     *
     * @param id 投标项目ID
     */
    @SaCheckPermission("bid:submission:progress")
    @GetMapping("/{id}/progress")
    public R<BidSubmissionProgressVo> getProgress(@NotNull(message = "主键不能为空") @PathVariable Long id) {
        return R.ok(bizBidSubmissionService.getProgress(id));
    }

    /**
     * 取消生成
     *
     * @param id 投标项目ID
     */
    @SaCheckPermission("bid:submission:generate")
    @Log(title = "投标项目", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/cancel")
    public R<Void> cancelGeneration(@NotNull(message = "主键不能为空") @PathVariable Long id) {
        return toAjax(bizBidSubmissionService.cancelGeneration(id));
    }

    /**
     * 重新生成
     *
     * @param id 投标项目ID
     */
    @SaCheckPermission("bid:submission:generate")
    @Log(title = "投标项目", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/regenerate")
    public R<Void> regenerate(@NotNull(message = "主键不能为空") @PathVariable Long id) {
        return toAjax(bizBidSubmissionService.regenerate(id));
    }

}
