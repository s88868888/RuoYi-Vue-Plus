package org.dromara.resource.service.agent;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.ai.service.MilvusVectorStoreService;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.ai.domain.VectorSearchResult;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 公司信息检索Agent
 * 从Milvus向量数据库检索公司信息
 *
 * @author ruoyi
 * @date 2026-02-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompanyInfoRetrievalAgent {

    private final MilvusVectorStoreService milvusVectorStoreService;

    /**
     * 检索公司信息
     *
     * @param companyId 公司ID
     * @param placeholders 需要填充的占位符列表
     * @return 占位符对应的值
     */
    public Map<String, String> retrieveCompanyInfo(Long companyId, List<String> placeholders) {
        log.info("开始检索公司信息，companyId: {}, placeholders: {}", companyId, placeholders);

        Map<String, String> result = new HashMap<>();

        try {
            // 构建集合名称（每个公司一个集合）
            String collectionName = "company_" + companyId;

            // 遍历每个占位符，从向量库检索对应信息
            for (String placeholder : placeholders) {
                String fieldName = extractFieldName(placeholder);
                String query = buildQuery(fieldName);

                // 从Milvus检索（使用简化方法）
                List<VectorSearchResult> searchResults = milvusVectorStoreService.search(
                    collectionName,
                    query,
                    null,   // tenantId
                    companyId,  // companyId
                    null,   // docType
                    1       // topK: 只取最相关的1条
                );

                if (!searchResults.isEmpty()) {
                    String value = searchResults.get(0).getContent();
                    result.put(placeholder, value);
                    log.debug("检索到占位符 {} 的值: {}", placeholder, value);
                } else {
                    log.warn("未检索到占位符 {} 的值", placeholder);
                    result.put(placeholder, "");
                }
            }

            log.info("公司信息检索完成，检索到{}个字段", result.size());
            return result;

        } catch (Exception e) {
            log.error("检索公司信息失败", e);
            throw new RuntimeException("检索公司信息失败: " + e.getMessage());
        }
    }

    /**
     * 从占位符提取字段名
     * 例如：{{company_name}} -> company_name
     */
    private String extractFieldName(String placeholder) {
        return placeholder.replaceAll("[{}]", "").trim();
    }

    /**
     * 构建检索查询
     */
    private String buildQuery(String fieldName) {
        // 将字段名转换为自然语言查询
        Map<String, String> fieldQueryMap = new HashMap<>();
        fieldQueryMap.put("company_name", "公司名称是什么");
        fieldQueryMap.put("register_capital", "注册资本是多少");
        fieldQueryMap.put("establish_date", "公司成立日期");
        fieldQueryMap.put("legal_person", "法定代表人是谁");
        fieldQueryMap.put("business_scope", "公司经营范围");
        fieldQueryMap.put("company_address", "公司地址");
        fieldQueryMap.put("contact_phone", "联系电话");
        fieldQueryMap.put("company_intro", "公司简介");
        fieldQueryMap.put("qualification", "公司资质证书");
        fieldQueryMap.put("employee_count", "员工人数");

        return fieldQueryMap.getOrDefault(fieldName, fieldName);
    }

    /**
     * 批量检索多个公司的信息
     */
    public Map<Long, Map<String, String>> batchRetrieve(
        List<Long> companyIds,
        List<String> placeholders) {

        Map<Long, Map<String, String>> result = new HashMap<>();

        for (Long companyId : companyIds) {
            Map<String, String> companyInfo = retrieveCompanyInfo(companyId, placeholders);
            result.put(companyId, companyInfo);
        }

        return result;
    }

}
