package org.dromara.review.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaIgnore;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.review.domain.bo.ReviewStandardBo;
import org.dromara.review.domain.bo.ReviewStandardFocusBo;
import org.dromara.review.domain.bo.ReviewStandardRuleBo;
import org.dromara.review.domain.vo.ParsedRuleVo;
import org.dromara.review.domain.vo.ReviewKnowledgeVo;
import org.dromara.review.domain.vo.ReviewStandardFocusVo;
import org.dromara.review.domain.vo.ReviewStandardRuleExportVo;
import org.dromara.review.domain.vo.ReviewStandardRuleVo;
import org.dromara.review.domain.vo.ReviewStandardVo;
import org.dromara.review.service.IReviewStandardFocusService;
import org.dromara.review.service.IReviewStandardRuleService;
import org.dromara.review.service.IReviewStandardService;
import org.dromara.review.service.ReviewStandardParseService;
import org.dromara.system.domain.vo.SysDictDataVo;
import org.dromara.system.service.ISysDictTypeService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 审核标准
 *
 * @author LionLi
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/review/standard")
public class ReviewStandardController extends BaseController {

    private final IReviewStandardService reviewStandardService;
    private final IReviewStandardRuleService reviewStandardRuleService;
    private final IReviewStandardFocusService reviewStandardFocusService;
    private final ReviewStandardParseService reviewStandardParseService;
    private final ISysDictTypeService dictTypeService;

    /**
     * 分页查询标准列表
     */
    @GetMapping("/list")
    public TableDataInfo<ReviewStandardVo> list(ReviewStandardBo bo, PageQuery pageQuery) {
        return reviewStandardService.queryPageList(bo, pageQuery);
    }

    /**
     * Rule category dictionary for the city-renewal proxy.
     */
    @SaIgnore
    @GetMapping("/rule/categories")
    public R<List<SysDictDataVo>> ruleCategories() {
        List<SysDictDataVo> data = dictTypeService.selectDictDataByType("review_rule_category");
        return R.ok(data == null ? new ArrayList<>() : data);
    }

    /**
     * 获取标准详情（含规则列表和关联知识库）
     *
     * @param id 主键
     */
    @GetMapping("/{id}")
    public R<ReviewStandardVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable Long id) {
        return R.ok(reviewStandardService.queryById(id));
    }

    /**
     * 新增标准
     */
    @SaCheckPermission("review:standard:add")
    @Log(title = "审核标准", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Long> add(@Validated(AddGroup.class) @RequestBody ReviewStandardBo bo) {
        reviewStandardService.insertByBo(bo);
        return R.ok(bo.getId());
    }

    /**
     * 修改标准
     */
    @SaCheckPermission("review:standard:edit")
    @Log(title = "审核标准", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody ReviewStandardBo bo) {
        return toAjax(reviewStandardService.updateByBo(bo));
    }

    /**
     * 删除标准
     *
     * @param ids 主键串
     */
    @SaCheckPermission("review:standard:remove")
    @Log(title = "审核标准", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] ids) {
        return toAjax(reviewStandardService.deleteWithValidByIds(List.of(ids), true));
    }

    // ==================== 知识库关联 ====================

    /**
     * 查询标准关联的知识库列表
     */
    @SaCheckPermission("review:standard:query")
    @GetMapping("/{standardId}/knowledges")
    public R<List<ReviewKnowledgeVo>> listKnowledges(@NotNull(message = "标准ID不能为空") @PathVariable Long standardId) {
        return R.ok(reviewStandardService.queryLinkedKnowledges(standardId));
    }

    /**
     * 关联知识库
     */
    @SaCheckPermission("review:standard:edit")
    @Log(title = "标准关联知识库", businessType = BusinessType.INSERT)
    @PostMapping("/{standardId}/knowledge/{knowledgeId}")
    public R<Void> linkKnowledge(@NotNull @PathVariable Long standardId, @NotNull @PathVariable Long knowledgeId) {
        reviewStandardService.linkKnowledge(standardId, knowledgeId);
        return R.ok();
    }

    /**
     * 解除关联知识库
     */
    @SaCheckPermission("review:standard:edit")
    @Log(title = "标准解除知识库", businessType = BusinessType.DELETE)
    @DeleteMapping("/{standardId}/knowledge/{knowledgeId}")
    public R<Void> unlinkKnowledge(@NotNull @PathVariable Long standardId, @NotNull @PathVariable Long knowledgeId) {
        reviewStandardService.unlinkKnowledge(standardId, knowledgeId);
        return R.ok();
    }

    // ==================== 规则子资源 ====================

    /**
     * 分页查询标准下的规则列表
     *
     * @param standardId 标准ID
     */
    @SaCheckPermission("review:standard:query")
    @GetMapping("/{standardId}/rules")
    public TableDataInfo<ReviewStandardRuleVo> listRules(@NotNull(message = "标准ID不能为空") @PathVariable Long standardId,
                                                         ReviewStandardRuleBo bo, PageQuery pageQuery) {
        bo.setStandardId(standardId);
        return reviewStandardRuleService.queryPageList(bo, pageQuery);
    }

    /**
     * 新增规则
     */
    @SaCheckPermission("review:standard:edit")
    @Log(title = "审核标准规则", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping("/rule")
    public R<Void> addRule(@Validated(AddGroup.class) @RequestBody ReviewStandardRuleBo bo) {
        return toAjax(reviewStandardRuleService.insertByBo(bo));
    }

    /**
     * 修改规则
     */
    @SaCheckPermission("review:standard:edit")
    @Log(title = "审核标准规则", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/rule")
    public R<Void> editRule(@Validated(EditGroup.class) @RequestBody ReviewStandardRuleBo bo) {
        return toAjax(reviewStandardRuleService.updateByBo(bo));
    }

    /**
     * 删除规则
     *
     * @param ids 主键串
     */
    @SaCheckPermission("review:standard:edit")
    @Log(title = "审核标准规则", businessType = BusinessType.DELETE)
    @DeleteMapping("/rule/{ids}")
    public R<Void> removeRule(@NotEmpty(message = "主键不能为空") @PathVariable Long[] ids) {
        return toAjax(reviewStandardRuleService.deleteWithValidByIds(List.of(ids), true));
    }

    /**
     * 导出标准下的规则列表（xlsx）
     */
    @SaCheckPermission("review:standard:query")
    @PostMapping("/{standardId}/rules/export")
    public void exportRules(@NotNull @PathVariable Long standardId, HttpServletResponse response) {
        List<ReviewStandardRuleVo> list = reviewStandardRuleService.queryListByStandardId(standardId);
        List<ReviewStandardRuleExportVo> exportList = list.stream().map(r -> {
            ReviewStandardRuleExportVo vo = new ReviewStandardRuleExportVo();
            vo.setContent(r.getContent());
            vo.setSeverity(r.getSeverity());
            vo.setCategory(r.getCategory());
            vo.setWeight(r.getWeight());
            return vo;
        }).toList();
        org.dromara.common.excel.utils.ExcelUtil.exportExcel(exportList, "规则列表", ReviewStandardRuleExportVo.class, response);
    }

    // ==================== 关注列表子资源 ====================

    /**
     * 查询标准下的关注要点列表
     *
     * @param standardId 标准ID
     */
    @SaCheckPermission("review:standard:query")
    @GetMapping("/{standardId}/focus")
    public R<List<ReviewStandardFocusVo>> listFocus(@NotNull(message = "标准ID不能为空") @PathVariable Long standardId) {
        return R.ok(reviewStandardFocusService.queryListByStandardId(standardId));
    }

    /**
     * 新增关注要点
     */
    @SaCheckPermission("review:standard:edit")
    @Log(title = "审核标准关注要点", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping("/focus")
    public R<Void> addFocus(@Validated(AddGroup.class) @RequestBody ReviewStandardFocusBo bo) {
        return toAjax(reviewStandardFocusService.insertByBo(bo));
    }

    /**
     * 修改关注要点
     */
    @SaCheckPermission("review:standard:edit")
    @Log(title = "审核标准关注要点", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/focus")
    public R<Void> editFocus(@Validated(EditGroup.class) @RequestBody ReviewStandardFocusBo bo) {
        return toAjax(reviewStandardFocusService.updateByBo(bo));
    }

    /**
     * 删除关注要点
     *
     * @param ids 主键串
     */
    @SaCheckPermission("review:standard:edit")
    @Log(title = "审核标准关注要点", businessType = BusinessType.DELETE)
    @DeleteMapping("/focus/{ids}")
    public R<Void> removeFocus(@NotEmpty(message = "主键不能为空") @PathVariable Long[] ids) {
        return toAjax(reviewStandardFocusService.deleteByIds(List.of(ids)));
    }

    // ==================== 规则导入 / AI 抽取 ====================

    /**
     * 下载规则导入模板（xlsx）
     */
    @SaCheckPermission("review:standard:query")
    @PostMapping("/rule/template")
    public void downloadRuleTemplate(HttpServletResponse response) throws IOException {
        reviewStandardParseService.downloadTemplate(response);
    }

    /**
     * 上传文档（Word/PDF/TXT）调用 qwen-long 抽取规则，返回预览列表（不落库）
     */
    @SaCheckPermission("review:standard:edit")
    @PostMapping("/rule/parse-document")
    public R<List<ParsedRuleVo>> parseDocument(@NotNull(message = "ossId 不能为空") @RequestParam Long ossId) {
        return R.ok(reviewStandardParseService.parseDocument(ossId));
    }

    /**
     * 上传 Excel 模板解析规则，返回预览列表（不落库）
     */
    @SaCheckPermission("review:standard:edit")
    @PostMapping("/rule/import-preview")
    public R<List<ParsedRuleVo>> importTemplatePreview(@RequestParam("file") MultipartFile file) throws IOException {
        return R.ok(reviewStandardParseService.importTemplate(file.getInputStream()));
    }

    /**
     * 批量保存规则到指定标准（用户在前端确认编辑后调用）
     */
    @SaCheckPermission("review:standard:edit")
    @Log(title = "审核标准规则批量导入", businessType = BusinessType.INSERT)
    @PostMapping("/{standardId}/rules/batch")
    public R<Integer> batchAddRules(@NotNull @PathVariable Long standardId,
                                    @RequestBody List<ReviewStandardRuleBo> rules) {
        return R.ok(reviewStandardRuleService.batchInsert(standardId, rules));
    }
}
