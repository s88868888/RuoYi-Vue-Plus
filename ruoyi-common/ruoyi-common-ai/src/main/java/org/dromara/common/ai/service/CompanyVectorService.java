package org.dromara.common.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.ai.domain.VectorDocument;
import org.dromara.common.ai.domain.VectorSearchResult;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 公司数据向量化服务
 *
 * @author ruoyi
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyVectorService {

    private final MilvusVectorStoreService vectorStoreService;

    /**
     * 初始化集合
     */
    public void initCollection() {
        String collectionName = vectorStoreService.getCollectionName();
        vectorStoreService.createCollection(collectionName);
    }

    /**
     * 添加公司信息到向量库
     */
    public boolean addCompanyInfo(String tenantId, Long companyId, Map<String, Object> companyData) {
        try {
            String collectionName = vectorStoreService.getCollectionName();

            // 构建文档内容
            StringBuilder content = new StringBuilder();
            content.append("公司信息：");
            if (companyData.containsKey("companyName")) {
                content.append("公司名称：").append(companyData.get("companyName")).append("；");
            }
            if (companyData.containsKey("creditCode")) {
                content.append("统一社会信用代码：").append(companyData.get("creditCode")).append("；");
            }
            if (companyData.containsKey("legalPerson")) {
                content.append("法定代表人：").append(companyData.get("legalPerson")).append("；");
            }
            if (companyData.containsKey("registeredCapital")) {
                content.append("注册资本：").append(companyData.get("registeredCapital")).append("；");
            }
            if (companyData.containsKey("establishDate")) {
                content.append("成立日期：").append(companyData.get("establishDate")).append("；");
            }
            if (companyData.containsKey("businessScope")) {
                content.append("经营范围：").append(companyData.get("businessScope")).append("；");
            }

            VectorDocument document = VectorDocument.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .companyId(companyId)
                .docType("company_info")
                .content(content.toString())
                .metadata(companyData)
                .createTime(System.currentTimeMillis())
                .build();

            return vectorStoreService.insertDocument(collectionName, document);

        } catch (Exception e) {
            log.error("添加公司信息到向量库失败", e);
            return false;
        }
    }

    /**
     * 添加人员信息到向量库
     */
    public boolean addPersonnelInfo(String tenantId, Long companyId, Map<String, Object> personnelData) {
        try {
            String collectionName = vectorStoreService.getCollectionName();

            StringBuilder content = new StringBuilder();
            content.append("人员信息：");
            if (personnelData.containsKey("name")) {
                content.append("姓名：").append(personnelData.get("name")).append("；");
            }
            if (personnelData.containsKey("position")) {
                content.append("职位：").append(personnelData.get("position")).append("；");
            }
            if (personnelData.containsKey("department")) {
                content.append("部门：").append(personnelData.get("department")).append("；");
            }
            if (personnelData.containsKey("education")) {
                content.append("学历：").append(personnelData.get("education")).append("；");
            }
            if (personnelData.containsKey("skills")) {
                content.append("技能：").append(personnelData.get("skills")).append("；");
            }

            VectorDocument document = VectorDocument.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .companyId(companyId)
                .docType("personnel_info")
                .content(content.toString())
                .metadata(personnelData)
                .createTime(System.currentTimeMillis())
                .build();

            return vectorStoreService.insertDocument(collectionName, document);

        } catch (Exception e) {
            log.error("添加人员信息到向量库失败", e);
            return false;
        }
    }

    /**
     * 添加产品信息到向量库
     */
    public boolean addProductInfo(String tenantId, Long companyId, Map<String, Object> productData) {
        try {
            String collectionName = vectorStoreService.getCollectionName();

            StringBuilder content = new StringBuilder();
            content.append("产品信息：");
            if (productData.containsKey("productName")) {
                content.append("产品名称：").append(productData.get("productName")).append("；");
            }
            if (productData.containsKey("category")) {
                content.append("产品类别：").append(productData.get("category")).append("；");
            }
            if (productData.containsKey("description")) {
                content.append("产品描述：").append(productData.get("description")).append("；");
            }
            if (productData.containsKey("features")) {
                content.append("产品特点：").append(productData.get("features")).append("；");
            }

            VectorDocument document = VectorDocument.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .companyId(companyId)
                .docType("product_info")
                .content(content.toString())
                .metadata(productData)
                .createTime(System.currentTimeMillis())
                .build();

            return vectorStoreService.insertDocument(collectionName, document);

        } catch (Exception e) {
            log.error("添加产品信息到向量库失败", e);
            return false;
        }
    }

    /**
     * 添加资质信息到向量库
     */
    public boolean addQualificationInfo(String tenantId, Long companyId, Map<String, Object> qualificationData) {
        try {
            String collectionName = vectorStoreService.getCollectionName();

            StringBuilder content = new StringBuilder();
            content.append("资质信息：");
            if (qualificationData.containsKey("qualificationType")) {
                content.append("资质类型：").append(qualificationData.get("qualificationType")).append("；");
            }
            if (qualificationData.containsKey("qualificationName")) {
                content.append("资质名称：").append(qualificationData.get("qualificationName")).append("；");
            }
            if (qualificationData.containsKey("certificateNo")) {
                content.append("证书编号：").append(qualificationData.get("certificateNo")).append("；");
            }
            if (qualificationData.containsKey("issueDate")) {
                content.append("发证日期：").append(qualificationData.get("issueDate")).append("；");
            }
            if (qualificationData.containsKey("validUntil")) {
                content.append("有效期至：").append(qualificationData.get("validUntil")).append("；");
            }

            VectorDocument document = VectorDocument.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .companyId(companyId)
                .docType("qualification")
                .content(content.toString())
                .metadata(qualificationData)
                .createTime(System.currentTimeMillis())
                .build();

            return vectorStoreService.insertDocument(collectionName, document);

        } catch (Exception e) {
            log.error("添加资质信息到向量库失败", e);
            return false;
        }
    }

    /**
     * 添加业绩案例到向量库
     */
    public boolean addPerformanceCase(String tenantId, Long companyId, Map<String, Object> performanceData) {
        try {
            String collectionName = vectorStoreService.getCollectionName();

            StringBuilder content = new StringBuilder();
            content.append("业绩案例：");
            if (performanceData.containsKey("projectName")) {
                content.append("项目名称：").append(performanceData.get("projectName")).append("；");
            }
            if (performanceData.containsKey("client")) {
                content.append("客户：").append(performanceData.get("client")).append("；");
            }
            if (performanceData.containsKey("projectAmount")) {
                content.append("项目金额：").append(performanceData.get("projectAmount")).append("；");
            }
            if (performanceData.containsKey("completionDate")) {
                content.append("完成日期：").append(performanceData.get("completionDate")).append("；");
            }
            if (performanceData.containsKey("description")) {
                content.append("项目描述：").append(performanceData.get("description")).append("；");
            }

            VectorDocument document = VectorDocument.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .companyId(companyId)
                .docType("performance")
                .content(content.toString())
                .metadata(performanceData)
                .createTime(System.currentTimeMillis())
                .build();

            return vectorStoreService.insertDocument(collectionName, document);

        } catch (Exception e) {
            log.error("添加业绩案例到向量库失败", e);
            return false;
        }
    }

    /**
     * 添加专利信息到向量库
     */
    public boolean addPatentInfo(String tenantId, Long companyId, Map<String, Object> patentData) {
        try {
            String collectionName = vectorStoreService.getCollectionName();

            StringBuilder content = new StringBuilder();
            content.append("专利信息：");
            if (patentData.containsKey("patentName")) {
                content.append("专利名称：").append(patentData.get("patentName")).append("；");
            }
            if (patentData.containsKey("patentType")) {
                content.append("专利类型：").append(patentData.get("patentType")).append("；");
            }
            if (patentData.containsKey("patentNo")) {
                content.append("专利号：").append(patentData.get("patentNo")).append("；");
            }
            if (patentData.containsKey("applicationDate")) {
                content.append("申请日期：").append(patentData.get("applicationDate")).append("；");
            }
            if (patentData.containsKey("abstract")) {
                content.append("摘要：").append(patentData.get("abstract")).append("；");
            }

            VectorDocument document = VectorDocument.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .companyId(companyId)
                .docType("patent")
                .content(content.toString())
                .metadata(patentData)
                .createTime(System.currentTimeMillis())
                .build();

            return vectorStoreService.insertDocument(collectionName, document);

        } catch (Exception e) {
            log.error("添加专利信息到向量库失败", e);
            return false;
        }
    }

    /**
     * 添加财务信息到向量库
     */
    public boolean addFinancialInfo(String tenantId, Long companyId, Map<String, Object> financialData) {
        try {
            String collectionName = vectorStoreService.getCollectionName();

            StringBuilder content = new StringBuilder();
            content.append("财务信息：");
            if (financialData.containsKey("year")) {
                content.append("年度：").append(financialData.get("year")).append("；");
            }
            if (financialData.containsKey("revenue")) {
                content.append("营业收入：").append(financialData.get("revenue")).append("；");
            }
            if (financialData.containsKey("profit")) {
                content.append("净利润：").append(financialData.get("profit")).append("；");
            }
            if (financialData.containsKey("assets")) {
                content.append("总资产：").append(financialData.get("assets")).append("；");
            }

            VectorDocument document = VectorDocument.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .companyId(companyId)
                .docType("financial")
                .content(content.toString())
                .metadata(financialData)
                .createTime(System.currentTimeMillis())
                .build();

            return vectorStoreService.insertDocument(collectionName, document);

        } catch (Exception e) {
            log.error("添加财务信息到向量库失败", e);
            return false;
        }
    }

    /**
     * 添加项目知识到向量库
     */
    public boolean addProjectKnowledge(String tenantId, Long companyId, Map<String, Object> projectData) {
        try {
            String collectionName = vectorStoreService.getCollectionName();

            StringBuilder content = new StringBuilder();
            content.append("项目知识：");
            if (projectData.containsKey("title")) {
                content.append("标题：").append(projectData.get("title")).append("；");
            }
            if (projectData.containsKey("content")) {
                content.append("内容：").append(projectData.get("content")).append("；");
            }
            if (projectData.containsKey("tags")) {
                content.append("标签：").append(projectData.get("tags")).append("；");
            }

            VectorDocument document = VectorDocument.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .companyId(companyId)
                .docType("project")
                .content(content.toString())
                .metadata(projectData)
                .createTime(System.currentTimeMillis())
                .build();

            return vectorStoreService.insertDocument(collectionName, document);

        } catch (Exception e) {
            log.error("添加项目知识到向量库失败", e);
            return false;
        }
    }

    /**
     * 添加竞争公司信息到向量库
     */
    public boolean addCompetitorInfo(String tenantId, Long companyId, Map<String, Object> competitorData) {
        try {
            String collectionName = vectorStoreService.getCollectionName();

            StringBuilder content = new StringBuilder();
            content.append("竞争公司：");
            if (competitorData.containsKey("companyName")) {
                content.append("公司名称：").append(competitorData.get("companyName")).append("；");
            }
            if (competitorData.containsKey("companyType")) {
                content.append("公司类型：").append(competitorData.get("companyType")).append("；");
            }
            if (competitorData.containsKey("businessScope")) {
                content.append("主营业务：").append(competitorData.get("businessScope")).append("；");
            }
            if (competitorData.containsKey("competitorLevel")) {
                content.append("竞争级别：").append(competitorData.get("competitorLevel")).append("；");
            }
            if (competitorData.containsKey("strengths")) {
                content.append("竞争优势：").append(competitorData.get("strengths")).append("；");
            }
            if (competitorData.containsKey("weaknesses")) {
                content.append("竞争劣势：").append(competitorData.get("weaknesses")).append("；");
            }
            if (competitorData.containsKey("mainProducts")) {
                content.append("主要产品/服务：").append(competitorData.get("mainProducts")).append("；");
            }

            VectorDocument document = VectorDocument.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .companyId(companyId)
                .docType("competitor")
                .content(content.toString())
                .metadata(competitorData)
                .createTime(System.currentTimeMillis())
                .build();

            return vectorStoreService.insertDocument(collectionName, document);

        } catch (Exception e) {
            log.error("添加竞争公司信息到向量库失败", e);
            return false;
        }
    }

    /**
     * 搜索公司相关信息
     */
    public List<VectorSearchResult> searchCompanyData(String tenantId, Long companyId,
                                                     String query, String docType, int topK) {
        String collectionName = vectorStoreService.getCollectionName();
        return vectorStoreService.search(collectionName, query, tenantId, companyId, docType, topK);
    }

    /**
     * 搜索租户下指定公司的所有相关信息（不限文档类型）
     */
    public List<VectorSearchResult> searchCompanyAllData(String tenantId, Long companyId,
                                                         String query, int topK) {
        String collectionName = vectorStoreService.getCollectionName();
        return vectorStoreService.search(collectionName, query, tenantId, companyId, null, topK);
    }

    /**
     * 删除公司所有数据
     */
    public boolean deleteCompanyData(String tenantId, Long companyId) {
        String collectionName = vectorStoreService.getCollectionName();
        return vectorStoreService.deleteByCompany(collectionName, tenantId, companyId);
    }

}
