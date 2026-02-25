# 向量数据自动同步方案

## 概述

当用户修改公司人员、产品等数据时，需要自动同步到向量库。本方案提供了异步同步机制，不会阻塞主业务流程。

## 实现方式

### 方式一：在 Service 层手动调用（推荐）

在你的业务 Service 中，当数据发生变更时，调用异步同步服务。

#### 示例：人员管理 Service

```java
package org.dromara.system.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.common.ai.service.AsyncVectorSyncService;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PersonnelServiceImpl implements IPersonnelService {

    private final AsyncVectorSyncService asyncVectorSyncService;
    private final PersonnelMapper personnelMapper;

    /**
     * 新增人员
     */
    @Override
    @Transactional
    public int insertPersonnel(Personnel personnel) {
        // 1. 保存到数据库
        int result = personnelMapper.insert(personnel);

        // 2. 异步同步到向量库
        if (result > 0) {
            syncToVectorStore(personnel);
        }

        return result;
    }

    /**
     * 修改人员
     */
    @Override
    @Transactional
    public int updatePersonnel(Personnel personnel) {
        // 1. 更新数据库
        int result = personnelMapper.updateById(personnel);

        // 2. 异步同步到向量库
        if (result > 0) {
            syncToVectorStore(personnel);
        }

        return result;
    }

    /**
     * 删除人员
     */
    @Override
    @Transactional
    public int deletePersonnel(Long personnelId) {
        Personnel personnel = personnelMapper.selectById(personnelId);
        int result = personnelMapper.deleteById(personnelId);

        // 删除时可以选择删除整个公司的向量数据，或者只删除特定文档
        // 这里简化处理，删除整个公司的数据后重新同步
        if (result > 0 && personnel != null) {
            String tenantId = LoginHelper.getTenantId();
            asyncVectorSyncService.asyncDeleteVectorData(tenantId, personnel.getCompanyId());
        }

        return result;
    }

    /**
     * 同步到向量库
     */
    private void syncToVectorStore(Personnel personnel) {
        try {
            String tenantId = LoginHelper.getTenantId();

            // 构建数据
            Map<String, Object> data = new HashMap<>();
            data.put("name", personnel.getName());
            data.put("position", personnel.getPosition());
            data.put("department", personnel.getDepartment());
            data.put("education", personnel.getEducation());
            data.put("major", personnel.getMajor());
            data.put("skills", personnel.getSkills());
            data.put("workExperience", personnel.getWorkExperience());
            data.put("profile", personnel.getProfile());

            // 异步同步
            asyncVectorSyncService.asyncSyncPersonnelInfo(tenantId, personnel.getCompanyId(), data);

        } catch (Exception e) {
            // 同步失败不影响主业务，只记录日志
            log.error("同步人员信息到向量库失败", e);
        }
    }

}
```

#### 示例：公司信息 Service

```java
package org.dromara.system.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.common.ai.service.AsyncVectorSyncService;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CompanyServiceImpl implements ICompanyService {

    private final AsyncVectorSyncService asyncVectorSyncService;
    private final CompanyMapper companyMapper;

    /**
     * 新增公司
     */
    @Override
    @Transactional
    public int insertCompany(Company company) {
        int result = companyMapper.insert(company);

        if (result > 0) {
            syncToVectorStore(company);
        }

        return result;
    }

    /**
     * 修改公司
     */
    @Override
    @Transactional
    public int updateCompany(Company company) {
        int result = companyMapper.updateById(company);

        if (result > 0) {
            syncToVectorStore(company);
        }

        return result;
    }

    /**
     * 删除公司
     */
    @Override
    @Transactional
    public int deleteCompany(Long companyId) {
        int result = companyMapper.deleteById(companyId);

        if (result > 0) {
            String tenantId = LoginHelper.getTenantId();
            asyncVectorSyncService.asyncDeleteVectorData(tenantId, companyId);
        }

        return result;
    }

    /**
     * 同步到向量库
     */
    private void syncToVectorStore(Company company) {
        try {
            String tenantId = LoginHelper.getTenantId();

            Map<String, Object> data = new HashMap<>();
            data.put("companyName", company.getCompanyName());
            data.put("creditCode", company.getCreditCode());
            data.put("legalPerson", company.getLegalPerson());
            data.put("registeredCapital", company.getRegisteredCapital());
            data.put("establishDate", company.getEstablishDate());
            data.put("businessScope", company.getBusinessScope());
            data.put("registeredAddress", company.getRegisteredAddress());
            data.put("companyProfile", company.getCompanyProfile());

            asyncVectorSyncService.asyncSyncCompanyInfo(tenantId, company.getCompanyId(), data);

        } catch (Exception e) {
            log.error("同步公司信息到向量库失败", e);
        }
    }

}
```

### 方式二：使用 Spring 事件机制

如果你想更解耦，可以使用 Spring 的事件发布订阅机制。

#### 1. 创建事件类

```java
package org.dromara.system.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.Map;

@Getter
public class VectorSyncEvent extends ApplicationEvent {

    private final String tenantId;
    private final Long companyId;
    private final String dataType;
    private final Map<String, Object> data;

    public VectorSyncEvent(Object source, String tenantId, Long companyId,
                          String dataType, Map<String, Object> data) {
        super(source);
        this.tenantId = tenantId;
        this.companyId = companyId;
        this.dataType = dataType;
        this.data = data;
    }
}
```

#### 2. 创建事件监听器

```java
package org.dromara.system.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.ai.service.AsyncVectorSyncService;
import org.dromara.system.event.VectorSyncEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class VectorSyncEventListener {

    private final AsyncVectorSyncService asyncVectorSyncService;

    @Async
    @EventListener
    public void handleVectorSyncEvent(VectorSyncEvent event) {
        String dataType = event.getDataType();
        String tenantId = event.getTenantId();
        Long companyId = event.getCompanyId();

        switch (dataType) {
            case "company":
                asyncVectorSyncService.asyncSyncCompanyInfo(tenantId, companyId, event.getData());
                break;
            case "personnel":
                asyncVectorSyncService.asyncSyncPersonnelInfo(tenantId, companyId, event.getData());
                break;
            case "product":
                asyncVectorSyncService.asyncSyncProductInfo(tenantId, companyId, event.getData());
                break;
            case "qualification":
                asyncVectorSyncService.asyncSyncQualificationInfo(tenantId, companyId, event.getData());
                break;
            case "performance":
                asyncVectorSyncService.asyncSyncPerformanceCase(tenantId, companyId, event.getData());
                break;
            case "patent":
                asyncVectorSyncService.asyncSyncPatentInfo(tenantId, companyId, event.getData());
                break;
            case "financial":
                asyncVectorSyncService.asyncSyncFinancialInfo(tenantId, companyId, event.getData());
                break;
            case "project":
                asyncVectorSyncService.asyncSyncProjectKnowledge(tenantId, companyId, event.getData());
                break;
            default:
                log.warn("未知的数据类型: {}", dataType);
        }
    }
}
```

#### 3. 在 Service 中发布事件

```java
package org.dromara.system.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.event.VectorSyncEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PersonnelServiceImpl implements IPersonnelService {

    private final ApplicationEventPublisher eventPublisher;
    private final PersonnelMapper personnelMapper;

    @Override
    @Transactional
    public int insertPersonnel(Personnel personnel) {
        int result = personnelMapper.insert(personnel);

        if (result > 0) {
            // 发布事件
            publishVectorSyncEvent(personnel);
        }

        return result;
    }

    private void publishVectorSyncEvent(Personnel personnel) {
        String tenantId = LoginHelper.getTenantId();

        Map<String, Object> data = new HashMap<>();
        data.put("name", personnel.getName());
        data.put("position", personnel.getPosition());
        data.put("department", personnel.getDepartment());
        // ... 其他字段

        VectorSyncEvent event = new VectorSyncEvent(
            this, tenantId, personnel.getCompanyId(), "personnel", data
        );

        eventPublisher.publishEvent(event);
    }
}
```

## 配置说明

### 1. 启用向量同步

在配置文件中添加：

```yaml
milvus:
  enabled: true
  # 是否启用自动同步
  auto-sync: true
```

### 2. 线程池配置

默认配置：
- 核心线程数：2
- 最大线程数：5
- 队列容量：100

如需调整，可以修改 `VectorSyncAsyncConfig` 类。

## 注意事项

1. **异步执行**：同步操作是异步的，不会阻塞主业务流程
2. **失败处理**：同步失败只记录日志，不影响主业务
3. **事务一致性**：建议在事务提交后再同步，避免事务回滚导致数据不一致
4. **批量操作**：批量导入数据时，建议使用批量同步接口
5. **删除操作**：删除数据时，需要同时删除向量库中的数据

## 批量同步

如果需要批量同步历史数据，可以创建一个定时任务或手动触发：

```java
@Service
@RequiredArgsConstructor
public class VectorBatchSyncService {

    private final CompanyMapper companyMapper;
    private final PersonnelMapper personnelMapper;
    private final AsyncVectorSyncService asyncVectorSyncService;

    /**
     * 批量同步公司数据
     */
    public void batchSyncCompanyData(String tenantId) {
        List<Company> companies = companyMapper.selectList(
            new LambdaQueryWrapper<Company>()
                .eq(Company::getTenantId, tenantId)
        );

        for (Company company : companies) {
            Map<String, Object> data = convertToMap(company);
            asyncVectorSyncService.asyncSyncCompanyInfo(tenantId, company.getCompanyId(), data);
        }
    }

    /**
     * 批量同步人员数据
     */
    public void batchSyncPersonnelData(String tenantId, Long companyId) {
        List<Personnel> personnelList = personnelMapper.selectList(
            new LambdaQueryWrapper<Personnel>()
                .eq(Personnel::getCompanyId, companyId)
        );

        for (Personnel personnel : personnelList) {
            Map<String, Object> data = convertToMap(personnel);
            asyncVectorSyncService.asyncSyncPersonnelInfo(tenantId, companyId, data);
        }
    }
}
```

## 总结

推荐使用**方式一（Service 层手动调用）**，因为：
- 代码清晰，易于理解和维护
- 可以精确控制同步时机
- 便于调试和问题排查

如果你的系统已经大量使用事件机制，可以选择**方式二（事件机制）**，实现更好的解耦。
