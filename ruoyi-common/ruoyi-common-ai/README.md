# Milvus 向量数据库集成说明

## 概述

本模块集成了 Milvus 向量数据库和 Spring AI Alibaba，用于存储和检索公司相关的各类资源数据。支持多租户隔离，可以根据租户和公司进行数据的向量化存储和智能检索。

## 功能特性

- ✅ 支持多租户数据隔离
- ✅ 支持多种文档类型（公司信息、人员信息、产品信息、资质、业绩案例、专利、财务信息、项目知识）
- ✅ 自动向量化文本内容
- ✅ 基于语义的相似度搜索
- ✅ 支持按租户、公司、文档类型过滤

## 配置说明

### 1. 添加依赖

在需要使用向量数据库的模块中添加依赖：

```xml
<dependency>
    <groupId>org.dromara</groupId>
    <artifactId>ruoyi-common-ai</artifactId>
</dependency>
```

### 2. 配置文件

在 `application.yml` 或 `application-milvus.yml` 中添加配置：

```yaml
# Milvus 向量数据库配置
milvus:
  enabled: true
  host: 192.168.169.205
  port: 19530
  database: default
  connect-timeout: 10000
  keep-alive-time: 55000
  dimension: 1536
  collection-prefix: company_

# Spring AI Alibaba 配置
spring:
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY:your-api-key-here}
      embedding:
        model: text-embedding-v1
        enabled: true
```

### 3. 获取 DashScope API Key

1. 访问阿里云 DashScope 控制台：https://dashscope.console.aliyun.com/
2. 创建 API Key
3. 将 API Key 配置到环境变量 `DASHSCOPE_API_KEY` 或直接写入配置文件

## 使用示例

### 1. 初始化向量集合

```java
@Autowired
private CompanyVectorService companyVectorService;

// 初始化集合（首次使用时调用）
companyVectorService.initCollection();
```

### 2. 添加公司信息到向量库

```java
Map<String, Object> companyData = new HashMap<>();
companyData.put("companyName", "某某科技有限公司");
companyData.put("creditCode", "91110000XXXXXXXXXX");
companyData.put("legalPerson", "张三");
companyData.put("registeredCapital", "1000万元");
companyData.put("establishDate", "2020-01-01");
companyData.put("businessScope", "软件开发、技术咨询...");

String tenantId = "000000"; // 租户ID
Long companyId = 1L; // 公司ID

companyVectorService.addCompanyInfo(tenantId, companyId, companyData);
```

### 3. 添加其他类型数据

```java
// 添加人员信息
Map<String, Object> personnelData = new HashMap<>();
personnelData.put("name", "李四");
personnelData.put("position", "技术总监");
personnelData.put("department", "研发部");
personnelData.put("education", "硕士");
personnelData.put("skills", "Java、Python、AI");
companyVectorService.addPersonnelInfo(tenantId, companyId, personnelData);

// 添加产品信息
Map<String, Object> productData = new HashMap<>();
productData.put("productName", "智能管理系统");
productData.put("category", "企业管理软件");
productData.put("description", "基于AI的智能企业管理系统");
productData.put("features", "智能分析、自动化流程、数据可视化");
companyVectorService.addProductInfo(tenantId, companyId, productData);

// 添加资质信息
Map<String, Object> qualificationData = new HashMap<>();
qualificationData.put("qualificationType", "软件企业认定");
qualificationData.put("qualificationName", "软件企业证书");
qualificationData.put("certificateNo", "软企证字第XXXX号");
qualificationData.put("issueDate", "2021-06-01");
qualificationData.put("validUntil", "2024-06-01");
companyVectorService.addQualificationInfo(tenantId, companyId, qualificationData);

// 添加业绩案例
Map<String, Object> performanceData = new HashMap<>();
performanceData.put("projectName", "某大型企业管理系统项目");
performanceData.put("client", "某世界500强企业");
performanceData.put("projectAmount", "500万元");
performanceData.put("completionDate", "2023-12-31");
performanceData.put("description", "为客户开发了完整的企业管理系统...");
companyVectorService.addPerformanceCase(tenantId, companyId, performanceData);

// 添加专利信息
Map<String, Object> patentData = new HashMap<>();
patentData.put("patentName", "一种智能数据分析方法");
patentData.put("patentType", "发明专利");
patentData.put("patentNo", "ZL202110XXXXXX.X");
patentData.put("applicationDate", "2021-03-15");
patentData.put("abstract", "本发明提供了一种基于机器学习的智能数据分析方法...");
companyVectorService.addPatentInfo(tenantId, companyId, patentData);

// 添加财务信息
Map<String, Object> financialData = new HashMap<>();
financialData.put("year", "2023");
financialData.put("revenue", "5000万元");
financialData.put("profit", "800万元");
financialData.put("assets", "1.2亿元");
companyVectorService.addFinancialInfo(tenantId, companyId, financialData);

// 添加项目知识
Map<String, Object> projectData = new HashMap<>();
projectData.put("title", "微服务架构最佳实践");
projectData.put("content", "在项目中我们采用了Spring Cloud微服务架构...");
projectData.put("tags", "微服务,Spring Cloud,架构");
companyVectorService.addProjectKnowledge(tenantId, companyId, projectData);
```

### 4. 搜索公司相关信息

```java
// 搜索所有类型的数据
List<VectorSearchResult> results = companyVectorService.searchCompanyData(
    tenantId,      // 租户ID
    companyId,     // 公司ID
    "公司的软件开发能力如何？", // 查询文本
    null,          // 文档类型（null表示搜索所有类型）
    10             // 返回前10条结果
);

// 只搜索特定类型的数据
List<VectorSearchResult> personnelResults = companyVectorService.searchCompanyData(
    tenantId,
    companyId,
    "有哪些技术专家？",
    "personnel_info", // 只搜索人员信息
    5
);

// 处理搜索结果
for (VectorSearchResult result : results) {
    System.out.println("文档ID: " + result.getId());
    System.out.println("文档类型: " + result.getDocType());
    System.out.println("内容: " + result.getContent());
    System.out.println("相似度分数: " + result.getScore());
    System.out.println("元数据: " + result.getMetadata());
}
```

### 5. 删除公司数据

```java
// 删除某个公司的所有向量数据
companyVectorService.deleteCompanyData(tenantId, companyId);
```

## API 接口

### 初始化集合
```
POST /system/company/vector/init
```

### 添加数据
```
POST /system/company/vector/company/{companyId}      # 添加公司信息
POST /system/company/vector/personnel/{companyId}    # 添加人员信息
POST /system/company/vector/product/{companyId}      # 添加产品信息
POST /system/company/vector/qualification/{companyId} # 添加资质信息
POST /system/company/vector/performance/{companyId}  # 添加业绩案例
POST /system/company/vector/patent/{companyId}       # 添加专利信息
POST /system/company/vector/financial/{companyId}    # 添加财务信息
POST /system/company/vector/project/{companyId}      # 添加项目知识
```

### 搜索数据
```
GET /system/company/vector/search?companyId={companyId}&query={query}&docType={docType}&topK={topK}
```

参数说明：
- `companyId`: 公司ID（必填）
- `query`: 查询文本（必填）
- `docType`: 文档类型（可选，不填则搜索所有类型）
- `topK`: 返回结果数量（可选，默认10）

### 删除数据
```
DELETE /system/company/vector/{companyId}
```

## 文档类型说明

| 文档类型 | 说明 |
|---------|------|
| company_info | 公司基本信息 |
| personnel_info | 人员信息 |
| product_info | 产品信息 |
| qualification | 资质信息 |
| performance | 业绩案例 |
| patent | 专利信息 |
| financial | 财务信息 |
| project | 项目知识 |

## 注意事项

1. **API Key 安全**：不要将 DashScope API Key 直接写入代码或配置文件，建议使用环境变量
2. **向量维度**：DashScope text-embedding-v1 模型的向量维度为 1536，不要修改此配置
3. **数据隔离**：系统自动根据租户ID和公司ID进行数据隔离，确保多租户数据安全
4. **首次使用**：首次使用前需要调用 `initCollection()` 初始化向量集合
5. **性能优化**：批量插入数据时建议使用 `insertDocuments()` 方法而不是循环调用 `insertDocument()`

## 常见问题

### 1. 连接 Milvus 失败
- 检查 Milvus 服务是否正常运行
- 检查网络连接和防火墙设置
- 确认配置的 host 和 port 是否正确

### 2. 向量生成失败
- 检查 DashScope API Key 是否正确
- 确认账户余额是否充足
- 检查网络是否能访问阿里云服务

### 3. 搜索结果为空
- 确认数据是否已成功插入
- 检查租户ID和公司ID是否正确
- 尝试调整查询文本或增加 topK 值

## 技术架构

```
┌─────────────────────────────────────────────────────────┐
│                    应用层                                │
│  CompanyVectorController / 业务Service                  │
└─────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────┐
│                  向量服务层                              │
│  CompanyVectorService (业务封装)                        │
│  MilvusVectorStoreService (向量存储)                    │
└─────────────────────────────────────────────────────────┘
                           ↓
┌──────────────────────┬──────────────────────────────────┐
│   Spring AI Alibaba  │         Milvus SDK               │
│  (DashScope Embedding)│    (向量数据库客户端)            │
└──────────────────────┴──────────────────────────────────┘
                           ↓
┌──────────────────────┬──────────────────────────────────┐
│   阿里云 DashScope   │      Milvus 向量数据库           │
│   (文本向量化服务)    │    (向量存储和检索)              │
└──────────────────────┴──────────────────────────────────┘
```

## 后续扩展

1. **支持更多文档类型**：可以根据业务需要添加更多文档类型
2. **批量导入**：开发批量导入功能，支持从数据库批量同步数据到向量库
3. **定时同步**：实现定时任务，自动同步数据库变更到向量库
4. **混合检索**：结合传统数据库查询和向量检索，提供更精准的搜索结果
5. **AI 对话**：基于向量检索结果，接入大语言模型实现智能问答

## 参考文档

- [Milvus 官方文档](https://milvus.io/docs)
- [Spring AI Alibaba 文档](https://sca.aliyun.com/docs/2023/user-guide/ai/overview/)
- [阿里云 DashScope 文档](https://help.aliyun.com/zh/dashscope/)
