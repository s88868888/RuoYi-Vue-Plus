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
import org.dromara.common.web.core.BaseController;
import org.dromara.review.domain.bo.ReviewModelConfigBo;
import org.dromara.review.domain.vo.ReviewModelConfigTestVo;
import org.dromara.review.domain.vo.ReviewModelConfigVo;
import org.dromara.review.service.IReviewModelConfigService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 模型配置
 *
 * @author Linson
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/review/modelConfig")
public class ReviewModelConfigController extends BaseController {

    private final IReviewModelConfigService modelConfigService;

    /** 列表查询，可按 provider / enabled / purpose 过滤 */
    @GetMapping("/list")
    public R<List<ReviewModelConfigVo>> list(
        @RequestParam(required = false) String provider,
        @RequestParam(required = false) String enabled,
        @RequestParam(required = false) String purpose) {
        return R.ok(modelConfigService.queryList(provider, enabled, purpose));
    }

    /** 详情 */
    @GetMapping("/{id}")
    public R<ReviewModelConfigVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable Long id) {
        return R.ok(modelConfigService.queryById(id));
    }

    /** 新增 */
    @SaCheckPermission("review:modelConfig:add")
    @Log(title = "AI模型配置", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<Void> add(@Validated(AddGroup.class) @RequestBody ReviewModelConfigBo bo) {
        return toAjax(modelConfigService.insertByBo(bo));
    }

    /** 修改 */
    @SaCheckPermission("review:modelConfig:edit")
    @Log(title = "AI模型配置", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody ReviewModelConfigBo bo) {
        return toAjax(modelConfigService.updateByBo(bo));
    }

    /** 删除 */
    @SaCheckPermission("review:modelConfig:remove")
    @Log(title = "AI模型配置", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] ids) {
        return toAjax(modelConfigService.deleteWithValidByIds(List.of(ids), true));
    }

    /**
     * 测试连接：不落库，按当前表单参数对目标服务跑一次最小调用。
     * - chat 通道：发 ping 看返回
     * - paddleocr：1x1 PNG 跑一次 OCR
     * - qwen-vl-ocr：1x1 PNG 走 chatWithImage
     */
    @SaCheckPermission("review:modelConfig:edit")
    @PostMapping("/test")
    public R<ReviewModelConfigTestVo> testConnection(@RequestBody ReviewModelConfigBo bo) {
        return R.ok(modelConfigService.testConnection(bo));
    }
}
