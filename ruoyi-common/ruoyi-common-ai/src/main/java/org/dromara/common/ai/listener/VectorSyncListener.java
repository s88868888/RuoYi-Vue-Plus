package org.dromara.common.ai.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.ai.service.CompanyVectorService;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 向量数据同步监听器
 * 用于在数据变更时自动同步到向量库
 *
 * @author ruoyi
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorSyncListener {

    private final CompanyVectorService companyVectorService;

    /**
     * 同步公司信息到向量库
     */
    public void syncCompanyInfo(String tenantId, Long companyId, Map<String, Object> data) {
        try {
            // 先删除旧数据
            companyVectorService.deleteCompanyData(tenantId, companyId);
            // 添加新数据
            companyVectorService.addCompanyInfo(tenantId, companyId, data);
            log.info("同步公司信息到向量库成功: tenantId={}, companyId={}", tenantId, companyId);
        } catch (Exception e) {
            log.error("同步公司信息到向量库失败", e);
        }
    }

    /**
     * 同步人员信息到向量库
     */
    public void syncPersonnelInfo(String tenantId, Long companyId, Map<String, Object> data) {
        try {
            companyVectorService.addPersonnelInfo(tenantId, companyId, data);
            log.info("同步人员信息到向量库成功: tenantId={}, companyId={}", tenantId, companyId);
        } catch (Exception e) {
            log.error("同步人员信息到向量库失败", e);
        }
    }

    /**
     * 同步产品信息到向量库
     */
    public void syncProductInfo(String tenantId, Long companyId, Map<String, Object> data) {
        try {
            companyVectorService.addProductInfo(tenantId, companyId, data);
            log.info("同步产品信息到向量库成功: tenantId={}, companyId={}", tenantId, companyId);
        } catch (Exception e) {
            log.error("同步产品信息到向量库失败", e);
        }
    }

    /**
     * 同步资质信息到向量库
     */
    public void syncQualificationInfo(String tenantId, Long companyId, Map<String, Object> data) {
        try {
            companyVectorService.addQualificationInfo(tenantId, companyId, data);
            log.info("同步资质信息到向量库成功: tenantId={}, companyId={}", tenantId, companyId);
        } catch (Exception e) {
            log.error("同步资质信息到向量库失败", e);
        }
    }

    /**
     * 同步业绩案例到向量库
     */
    public void syncPerformanceCase(String tenantId, Long companyId, Map<String, Object> data) {
        try {
            companyVectorService.addPerformanceCase(tenantId, companyId, data);
            log.info("同步业绩案例到向量库成功: tenantId={}, companyId={}", tenantId, companyId);
        } catch (Exception e) {
            log.error("同步业绩案例到向量库失败", e);
        }
    }

    /**
     * 同步专利信息到向量库
     */
    public void syncPatentInfo(String tenantId, Long companyId, Map<String, Object> data) {
        try {
            companyVectorService.addPatentInfo(tenantId, companyId, data);
            log.info("同步专利信息到向量库成功: tenantId={}, companyId={}", tenantId, companyId);
        } catch (Exception e) {
            log.error("同步专利信息到向量库失败", e);
        }
    }

    /**
     * 同步财务信息到向量库
     */
    public void syncFinancialInfo(String tenantId, Long companyId, Map<String, Object> data) {
        try {
            companyVectorService.addFinancialInfo(tenantId, companyId, data);
            log.info("同步财务信息到向量库成功: tenantId={}, companyId={}", tenantId, companyId);
        } catch (Exception e) {
            log.error("同步财务信息到向量库失败", e);
        }
    }

    /**
     * 同步项目知识到向量库
     */
    public void syncProjectKnowledge(String tenantId, Long companyId, Map<String, Object> data) {
        try {
            companyVectorService.addProjectKnowledge(tenantId, companyId, data);
            log.info("同步项目知识到向量库成功: tenantId={}, companyId={}", tenantId, companyId);
        } catch (Exception e) {
            log.error("同步项目知识到向量库失败", e);
        }
    }

    /**
     * 同步竞争公司信息到向量库
     */
    public void syncCompetitorInfo(String tenantId, Long companyId, Map<String, Object> data) {
        try {
            companyVectorService.addCompetitorInfo(tenantId, companyId, data);
            log.info("同步竞争公司信息到向量库成功: tenantId={}, companyId={}", tenantId, companyId);
        } catch (Exception e) {
            log.error("同步竞争公司信息到向量库失败", e);
        }
    }

    /**
     * 删除向量数据
     */
    public void deleteVectorData(String tenantId, Long companyId) {
        try {
            companyVectorService.deleteCompanyData(tenantId, companyId);
            log.info("删除向量数据成功: tenantId={}, companyId={}", tenantId, companyId);
        } catch (Exception e) {
            log.error("删除向量数据失败", e);
        }
    }

}
