package org.dromara.common.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.ai.listener.VectorSyncListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 异步向量数据同步服务
 * 用于在数据变更时异步同步到向量库，避免阻塞主业务
 *
 * @author ruoyi
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncVectorSyncService {

    private final VectorSyncListener vectorSyncListener;

    /**
     * 异步同步公司信息
     */
    @Async("vectorSyncExecutor")
    public void asyncSyncCompanyInfo(String tenantId, Long companyId, Map<String, Object> data) {
        vectorSyncListener.syncCompanyInfo(tenantId, companyId, data);
    }

    /**
     * 异步同步人员信息
     */
    @Async("vectorSyncExecutor")
    public void asyncSyncPersonnelInfo(String tenantId, Long companyId, Map<String, Object> data) {
        vectorSyncListener.syncPersonnelInfo(tenantId, companyId, data);
    }

    /**
     * 异步同步产品信息
     */
    @Async("vectorSyncExecutor")
    public void asyncSyncProductInfo(String tenantId, Long companyId, Map<String, Object> data) {
        vectorSyncListener.syncProductInfo(tenantId, companyId, data);
    }

    /**
     * 异步同步资质信息
     */
    @Async("vectorSyncExecutor")
    public void asyncSyncQualificationInfo(String tenantId, Long companyId, Map<String, Object> data) {
        vectorSyncListener.syncQualificationInfo(tenantId, companyId, data);
    }

    /**
     * 异步同步业绩案例
     */
    @Async("vectorSyncExecutor")
    public void asyncSyncPerformanceCase(String tenantId, Long companyId, Map<String, Object> data) {
        vectorSyncListener.syncPerformanceCase(tenantId, companyId, data);
    }

    /**
     * 异步同步专利信息
     */
    @Async("vectorSyncExecutor")
    public void asyncSyncPatentInfo(String tenantId, Long companyId, Map<String, Object> data) {
        vectorSyncListener.syncPatentInfo(tenantId, companyId, data);
    }

    /**
     * 异步同步财务信息
     */
    @Async("vectorSyncExecutor")
    public void asyncSyncFinancialInfo(String tenantId, Long companyId, Map<String, Object> data) {
        vectorSyncListener.syncFinancialInfo(tenantId, companyId, data);
    }

    /**
     * 异步同步项目知识
     */
    @Async("vectorSyncExecutor")
    public void asyncSyncProjectKnowledge(String tenantId, Long companyId, Map<String, Object> data) {
        vectorSyncListener.syncProjectKnowledge(tenantId, companyId, data);
    }

    /**
     * 异步同步竞争公司信息
     */
    @Async("vectorSyncExecutor")
    public void asyncSyncCompetitorInfo(String tenantId, Long companyId, Map<String, Object> data) {
        vectorSyncListener.syncCompetitorInfo(tenantId, companyId, data);
    }

    /**
     * 异步删除向量数据
     */
    @Async("vectorSyncExecutor")
    public void asyncDeleteVectorData(String tenantId, Long companyId) {
        vectorSyncListener.deleteVectorData(tenantId, companyId);
    }

}
