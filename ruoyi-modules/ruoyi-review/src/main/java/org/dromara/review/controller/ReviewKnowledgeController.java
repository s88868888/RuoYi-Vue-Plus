package org.dromara.review.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
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
import org.dromara.review.domain.ReviewKnowledgeCase;
import org.dromara.review.domain.ReviewKnowledgeMisjudgment;
import org.dromara.review.domain.ReviewKnowledgePattern;
import org.dromara.review.domain.bo.ReviewKnowledgeBo;
import org.dromara.review.domain.vo.ReviewKnowledgeVo;
import org.dromara.review.domain.vo.ReviewStandardVo;
import org.dromara.review.service.IReviewKnowledgeService;
import org.dromara.review.service.ReviewRagService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 审核知识库
 *
 * @author LionLi
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/review/knowledge")
public class ReviewKnowledgeController extends BaseController {

    private final IReviewKnowledgeService reviewKnowledgeService;
    private final ReviewRagService reviewRagService;

    /**
     * 分页查询知识库列表
     */
    @SaCheckPermission("review:knowledge:list")
    @GetMapping("/list")
    public TableDataInfo<ReviewKnowledgeVo> list(ReviewKnowledgeBo bo, PageQuery pageQuery) {
        return reviewKnowledgeService.queryPageList(bo, pageQuery);
    }

    /**
     * 获取知识库详情
     *
     * @param id 主键
     */
    @SaCheckPermission("review:knowledge:query")
    @GetMapping("/{id}")
    public R<ReviewKnowledgeVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable Long id) {
        return R.ok(reviewKnowledgeService.queryById(id));
    }

    /**
     * 新增知识库
     */
    @SaCheckPermission("review:knowledge:add")
    @Log(title = "审核知识库", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody ReviewKnowledgeBo bo) {
        return toAjax(reviewKnowledgeService.insertByBo(bo));
    }

    /**
     * 修改知识库
     */
    @SaCheckPermission("review:knowledge:edit")
    @Log(title = "审核知识库", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody ReviewKnowledgeBo bo) {
        return toAjax(reviewKnowledgeService.updateByBo(bo));
    }

    /**
     * 删除知识库
     *
     * @param ids 主键串
     */
    @SaCheckPermission("review:knowledge:remove")
    @Log(title = "审核知识库", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] ids) {
        return toAjax(reviewKnowledgeService.deleteWithValidByIds(List.of(ids), true));
    }

    // ==================== 子资源 ====================

    /**
     * 查询知识库关联的标准列表
     */
    @SaCheckPermission("review:knowledge:query")
    @GetMapping("/{knowledgeId}/standards")
    public R<List<ReviewStandardVo>> listStandards(@NotNull(message = "知识库ID不能为空") @PathVariable Long knowledgeId) {
        return R.ok(reviewKnowledgeService.queryLinkedStandards(knowledgeId));
    }

    /**
     * 查询知识库下的案例列表
     */
    @SaCheckPermission("review:knowledge:query")
    @GetMapping("/{knowledgeId}/cases")
    public R<List<ReviewKnowledgeCase>> listCases(@NotNull(message = "知识库ID不能为空") @PathVariable Long knowledgeId) {
        return R.ok(reviewKnowledgeService.queryCases(knowledgeId));
    }

    /**
     * 查询知识库下的问题模式列表
     */
    @SaCheckPermission("review:knowledge:query")
    @GetMapping("/{knowledgeId}/patterns")
    public R<List<ReviewKnowledgePattern>> listPatterns(@NotNull(message = "知识库ID不能为空") @PathVariable Long knowledgeId) {
        return R.ok(reviewKnowledgeService.queryPatterns(knowledgeId));
    }

    /**
     * 查询知识库下的误判记录列表
     */
    @SaCheckPermission("review:knowledge:query")
    @GetMapping("/{knowledgeId}/misjudgments")
    public R<List<ReviewKnowledgeMisjudgment>> listMisjudgments(@NotNull(message = "知识库ID不能为空") @PathVariable Long knowledgeId) {
        return R.ok(reviewKnowledgeService.queryMisjudgments(knowledgeId));
    }

    /**
     * 同步知识库到向量库（Milvus）
     */
    @SaCheckPermission("review:knowledge:edit")
    @Log(title = "同步知识库到向量库", businessType = BusinessType.UPDATE)
    @PostMapping("/{knowledgeId}/sync-vector")
    public R<Void> syncToVector(@NotNull(message = "知识库ID不能为空") @PathVariable Long knowledgeId) {
        reviewRagService.syncKnowledgeToVector(knowledgeId);
        return R.ok();
    }
}
