package org.dromara.resource.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.web.core.BaseController;
import org.dromara.resource.domain.bo.BizDocumentConfigBo;
import org.dromara.resource.domain.vo.BizDocumentConfigVo;
import org.dromara.resource.service.IBizDocumentConfigService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 标书配置管理
 *
 * @author ruoyi
 * @date 2026-03-01
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/bid/document/config")
public class BizDocumentConfigController extends BaseController {

    private final IBizDocumentConfigService documentConfigService;

    /**
     * 查询投标项目的配置列表
     */
    @SaCheckPermission("bid:submission:query")
    @GetMapping("/list/{submissionId}")
    public R<List<BizDocumentConfigVo>> list(@PathVariable Long submissionId) {
        List<BizDocumentConfigVo> list = documentConfigService.queryBySubmissionId(submissionId);
        return R.ok(list);
    }

    /**
     * 批量保存配置
     */
    @SaCheckPermission("bid:submission:edit")
    @Log(title = "标书配置", businessType = BusinessType.UPDATE)
    @PostMapping("/batchSave/{submissionId}")
    public R<Void> batchSave(@PathVariable Long submissionId,
                              @Validated @RequestBody List<BizDocumentConfigBo> configs) {
        documentConfigService.batchSaveConfigs(submissionId, configs);
        return R.ok();
    }

    /**
     * 添加单个配置
     */
    @SaCheckPermission("bid:submission:edit")
    @Log(title = "标书配置", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    public R<Long> add(@Validated @RequestBody BizDocumentConfigBo bo) {
        Long configId = documentConfigService.addConfig(bo);
        return R.ok(configId);
    }

    /**
     * 删除配置
     */
    @SaCheckPermission("bid:submission:edit")
    @Log(title = "标书配置", businessType = BusinessType.DELETE)
    @DeleteMapping("/{configId}")
    public R<Void> remove(@PathVariable Long configId) {
        documentConfigService.deleteConfig(configId);
        return R.ok();
    }

}
