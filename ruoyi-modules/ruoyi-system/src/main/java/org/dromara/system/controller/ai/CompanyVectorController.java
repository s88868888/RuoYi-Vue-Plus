package org.dromara.system.controller.ai;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.ai.domain.VectorSearchResult;
import org.dromara.common.ai.service.CompanyVectorService;
import org.dromara.common.core.domain.R;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 公司向量数据管理
 *
 * @author ruoyi
 */
@Slf4j
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/company/vector")
public class CompanyVectorController extends BaseController {

    private final CompanyVectorService companyVectorService;

    /**
     * 初始化向量集合
     */
    @SaCheckPermission("system:company:vector:init")
    @PostMapping("/init")
    public R<Void> initCollection() {
        companyVectorService.initCollection();
        return R.ok();
    }

    /**
     * 添加公司信息到向量库
     */
    @SaCheckPermission("system:company:vector:add")
    @PostMapping("/company/{companyId}")
    public R<Void> addCompanyInfo(@PathVariable Long companyId, @RequestBody Map<String, Object> companyData) {
        String tenantId = LoginHelper.getTenantId();
        boolean success = companyVectorService.addCompanyInfo(tenantId, companyId, companyData);
        return success ? R.ok() : R.fail("添加公司信息失败");
    }

    /**
     * 添加人员信息到向量库
     */
    @SaCheckPermission("system:company:vector:add")
    @PostMapping("/personnel/{companyId}")
    public R<Void> addPersonnelInfo(@PathVariable Long companyId, @RequestBody Map<String, Object> personnelData) {
        String tenantId = LoginHelper.getTenantId();
        boolean success = companyVectorService.addPersonnelInfo(tenantId, companyId, personnelData);
        return success ? R.ok() : R.fail("添加人员信息失败");
    }

    /**
     * 添加产品信息到向量库
     */
    @SaCheckPermission("system:company:vector:add")
    @PostMapping("/product/{companyId}")
    public R<Void> addProductInfo(@PathVariable Long companyId, @RequestBody Map<String, Object> productData) {
        String tenantId = LoginHelper.getTenantId();
        boolean success = companyVectorService.addProductInfo(tenantId, companyId, productData);
        return success ? R.ok() : R.fail("添加产品信息失败");
    }

    /**
     * 添加资质信息到向量库
     */
    @SaCheckPermission("system:company:vector:add")
    @PostMapping("/qualification/{companyId}")
    public R<Void> addQualificationInfo(@PathVariable Long companyId, @RequestBody Map<String, Object> qualificationData) {
        String tenantId = LoginHelper.getTenantId();
        boolean success = companyVectorService.addQualificationInfo(tenantId, companyId, qualificationData);
        return success ? R.ok() : R.fail("添加资质信息失败");
    }

    /**
     * 添加业绩案例到向量库
     */
    @SaCheckPermission("system:company:vector:add")
    @PostMapping("/performance/{companyId}")
    public R<Void> addPerformanceCase(@PathVariable Long companyId, @RequestBody Map<String, Object> performanceData) {
        String tenantId = LoginHelper.getTenantId();
        boolean success = companyVectorService.addPerformanceCase(tenantId, companyId, performanceData);
        return success ? R.ok() : R.fail("添加业绩案例失败");
    }

    /**
     * 添加专利信息到向量库
     */
    @SaCheckPermission("system:company:vector:add")
    @PostMapping("/patent/{companyId}")
    public R<Void> addPatentInfo(@PathVariable Long companyId, @RequestBody Map<String, Object> patentData) {
        String tenantId = LoginHelper.getTenantId();
        boolean success = companyVectorService.addPatentInfo(tenantId, companyId, patentData);
        return success ? R.ok() : R.fail("添加专利信息失败");
    }

    /**
     * 添加财务信息到向量库
     */
    @SaCheckPermission("system:company:vector:add")
    @PostMapping("/financial/{companyId}")
    public R<Void> addFinancialInfo(@PathVariable Long companyId, @RequestBody Map<String, Object> financialData) {
        String tenantId = LoginHelper.getTenantId();
        boolean success = companyVectorService.addFinancialInfo(tenantId, companyId, financialData);
        return success ? R.ok() : R.fail("添加财务信息失败");
    }

    /**
     * 添加项目知识到向量库
     */
    @SaCheckPermission("system:company:vector:add")
    @PostMapping("/project/{companyId}")
    public R<Void> addProjectKnowledge(@PathVariable Long companyId, @RequestBody Map<String, Object> projectData) {
        String tenantId = LoginHelper.getTenantId();
        boolean success = companyVectorService.addProjectKnowledge(tenantId, companyId, projectData);
        return success ? R.ok() : R.fail("添加项目知识失败");
    }

    /**
     * 搜索公司相关信息
     */
    @SaCheckPermission("system:company:vector:search")
    @GetMapping("/search")
    public R<List<VectorSearchResult>> searchCompanyData(
        @RequestParam Long companyId,
        @RequestParam String query,
        @RequestParam(required = false) String docType,
        @RequestParam(defaultValue = "10") Integer topK) {

        String tenantId = LoginHelper.getTenantId();
        List<VectorSearchResult> results = companyVectorService.searchCompanyData(
            tenantId, companyId, query, docType, topK);
        return R.ok(results);
    }

    /**
     * 删除公司所有向量数据
     */
    @SaCheckPermission("system:company:vector:delete")
    @DeleteMapping("/{companyId}")
    public R<Void> deleteCompanyData(@PathVariable Long companyId) {
        String tenantId = LoginHelper.getTenantId();
        boolean success = companyVectorService.deleteCompanyData(tenantId, companyId);
        return success ? R.ok() : R.fail("删除公司数据失败");
    }

}
