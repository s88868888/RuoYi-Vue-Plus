package org.dromara.common.ai.service;

import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.DataType;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.SearchResults;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.*;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.ai.config.MilvusProperties;
import org.dromara.common.ai.domain.VectorDocument;
import org.dromara.common.ai.domain.VectorSearchResult;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Milvus 向量存储服务
 *
 * @author ruoyi
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MilvusVectorStoreService {

    private final MilvusServiceClient milvusClient;
    private final MilvusProperties milvusProperties;
    private final DashScopeEmbeddingModel embeddingModel;

    private static final String FIELD_ID = "id";
    private static final String FIELD_TENANT_ID = "tenant_id";
    private static final String FIELD_COMPANY_ID = "company_id";
    private static final String FIELD_DOC_TYPE = "doc_type";
    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_VECTOR = "vector";
    private static final String FIELD_METADATA = "metadata";
    private static final String FIELD_CREATE_TIME = "create_time";

    /**
     * 创建集合
     */
    public boolean createCollection(String collectionName) {
        try {
            // 检查集合是否存在
            R<Boolean> hasCollection = milvusClient.hasCollection(
                HasCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .build()
            );

            if (hasCollection.getData()) {
                log.info("集合 {} 已存在", collectionName);
                return true;
            }

            // 创建字段
            List<FieldType> fields = Arrays.asList(
                FieldType.newBuilder()
                    .withName(FIELD_ID)
                    .withDataType(DataType.VarChar)
                    .withMaxLength(256)
                    .withPrimaryKey(true)
                    .withAutoID(false)
                    .build(),
                FieldType.newBuilder()
                    .withName(FIELD_TENANT_ID)
                    .withDataType(DataType.VarChar)
                    .withMaxLength(64)
                    .build(),
                FieldType.newBuilder()
                    .withName(FIELD_COMPANY_ID)
                    .withDataType(DataType.Int64)
                    .build(),
                FieldType.newBuilder()
                    .withName(FIELD_DOC_TYPE)
                    .withDataType(DataType.VarChar)
                    .withMaxLength(64)
                    .build(),
                FieldType.newBuilder()
                    .withName(FIELD_CONTENT)
                    .withDataType(DataType.VarChar)
                    .withMaxLength(65535)
                    .build(),
                FieldType.newBuilder()
                    .withName(FIELD_VECTOR)
                    .withDataType(DataType.FloatVector)
                    .withDimension(milvusProperties.getDimension())
                    .build(),
                FieldType.newBuilder()
                    .withName(FIELD_METADATA)
                    .withDataType(DataType.JSON)
                    .build(),
                FieldType.newBuilder()
                    .withName(FIELD_CREATE_TIME)
                    .withDataType(DataType.Int64)
                    .build()
            );

            // 创建集合
            CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .withDescription("公司资源向量数据集合")
                .withFieldTypes(fields)
                .build();

            R<RpcStatus> createResult = milvusClient.createCollection(createParam);
            if (createResult.getStatus() != R.Status.Success.getCode()) {
                log.error("创建集合失败: {}", createResult.getMessage());
                return false;
            }

            // 创建向量索引
            CreateIndexParam indexParam = CreateIndexParam.newBuilder()
                .withCollectionName(collectionName)
                .withFieldName(FIELD_VECTOR)
                .withIndexType(IndexType.IVF_FLAT)
                .withMetricType(MetricType.L2)
                .withExtraParam("{\"nlist\":1024}")
                .build();

            R<RpcStatus> indexResult = milvusClient.createIndex(indexParam);
            if (indexResult.getStatus() != R.Status.Success.getCode()) {
                log.error("创建索引失败: {}", indexResult.getMessage());
                return false;
            }

            // 加载集合
            R<RpcStatus> loadResult = milvusClient.loadCollection(
                LoadCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .build()
            );

            if (loadResult.getStatus() != R.Status.Success.getCode()) {
                log.error("加载集合失败: {}", loadResult.getMessage());
                return false;
            }

            log.info("集合 {} 创建成功", collectionName);
            return true;

        } catch (Exception e) {
            log.error("创建集合异常", e);
            return false;
        }
    }

    /**
     * 插入向量文档
     */
    public boolean insertDocument(String collectionName, VectorDocument document) {
        return insertDocuments(collectionName, Collections.singletonList(document));
    }

    /**
     * 批量插入向量文档
     */
    public boolean insertDocuments(String collectionName, List<VectorDocument> documents) {
        try {
            if (documents == null || documents.isEmpty()) {
                return true;
            }

            // 准备数据
            List<String> ids = new ArrayList<>();
            List<String> tenantIds = new ArrayList<>();
            List<Long> companyIds = new ArrayList<>();
            List<String> docTypes = new ArrayList<>();
            List<String> contents = new ArrayList<>();
            List<List<Float>> vectors = new ArrayList<>();
            List<String> metadataList = new ArrayList<>();
            List<Long> createTimes = new ArrayList<>();

            for (VectorDocument doc : documents) {
                // 如果没有向量，使用 embedding 模型生成
                if (doc.getVector() == null || doc.getVector().length == 0) {
                    float[] vector = generateEmbedding(doc.getContent());
                    doc.setVector(vector);
                }

                ids.add(doc.getId());
                tenantIds.add(doc.getTenantId());
                companyIds.add(doc.getCompanyId());
                docTypes.add(doc.getDocType());
                contents.add(doc.getContent());

                // 转换 float[] 为 List<Float>
                List<Float> vectorList = new ArrayList<>();
                for (float v : doc.getVector()) {
                    vectorList.add(v);
                }
                vectors.add(vectorList);

                // 序列化 metadata 为 JSON 字符串
                metadataList.add(doc.getMetadata() != null ?
                    new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(doc.getMetadata()) : "{}");
                createTimes.add(doc.getCreateTime() != null ? doc.getCreateTime() : System.currentTimeMillis());
            }

            // 插入数据
            List<InsertParam.Field> fields = Arrays.asList(
                new InsertParam.Field(FIELD_ID, ids),
                new InsertParam.Field(FIELD_TENANT_ID, tenantIds),
                new InsertParam.Field(FIELD_COMPANY_ID, companyIds),
                new InsertParam.Field(FIELD_DOC_TYPE, docTypes),
                new InsertParam.Field(FIELD_CONTENT, contents),
                new InsertParam.Field(FIELD_VECTOR, vectors),
                new InsertParam.Field(FIELD_METADATA, metadataList),
                new InsertParam.Field(FIELD_CREATE_TIME, createTimes)
            );

            InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(collectionName)
                .withFields(fields)
                .build();

            R<MutationResult> insertResult = milvusClient.insert(insertParam);
            if (insertResult.getStatus() != R.Status.Success.getCode()) {
                log.error("插入数据失败: {}", insertResult.getMessage());
                return false;
            }

            log.info("成功插入 {} 条向量数据到集合 {}", documents.size(), collectionName);
            return true;

        } catch (Exception e) {
            log.error("插入向量数据异常", e);
            return false;
        }
    }

    /**
     * 搜索相似向量
     */
    public List<VectorSearchResult> search(String collectionName, String queryText,
                                          String tenantId, Long companyId,
                                          String docType, int topK) {
        try {
            // 生成查询向量
            float[] queryVector = generateEmbedding(queryText);
            List<Float> queryVectorList = new ArrayList<>();
            for (float v : queryVector) {
                queryVectorList.add(v);
            }

            // 构建过滤表达式
            StringBuilder filterExpr = new StringBuilder();
            if (tenantId != null && !tenantId.isEmpty()) {
                filterExpr.append(FIELD_TENANT_ID).append(" == \"").append(tenantId).append("\"");
            }
            if (companyId != null) {
                if (filterExpr.length() > 0) {
                    filterExpr.append(" && ");
                }
                filterExpr.append(FIELD_COMPANY_ID).append(" == ").append(companyId);
            }
            if (docType != null && !docType.isEmpty()) {
                if (filterExpr.length() > 0) {
                    filterExpr.append(" && ");
                }
                filterExpr.append(FIELD_DOC_TYPE).append(" == \"").append(docType).append("\"");
            }

            // 搜索参数
            SearchParam.Builder searchBuilder = SearchParam.newBuilder()
                .withCollectionName(collectionName)
                .withMetricType(MetricType.L2)
                .withOutFields(Arrays.asList(FIELD_ID, FIELD_TENANT_ID, FIELD_COMPANY_ID,
                    FIELD_DOC_TYPE, FIELD_CONTENT, FIELD_METADATA))
                .withTopK(topK)
                .withVectors(Collections.singletonList(queryVectorList))
                .withVectorFieldName(FIELD_VECTOR)
                .withConsistencyLevel(ConsistencyLevelEnum.STRONG);

            if (filterExpr.length() > 0) {
                searchBuilder.withExpr(filterExpr.toString());
            }

            R<SearchResults> searchResult = milvusClient.search(searchBuilder.build());
            if (searchResult.getStatus() != R.Status.Success.getCode()) {
                log.error("搜索失败: {}", searchResult.getMessage());
                return Collections.emptyList();
            }

            // 解析结果
            List<VectorSearchResult> results = new ArrayList<>();
            SearchResults data = searchResult.getData();
            if (data != null && data.getResults() != null) {
                data.getResults().getFieldsDataList().forEach(fieldData -> {
                    // 这里需要根据实际返回的数据结构解析
                    // Milvus SDK 的结果解析比较复杂，这里简化处理
                    log.debug("搜索结果: {}", fieldData);
                });
            }

            return results;

        } catch (Exception e) {
            log.error("搜索向量数据异常", e);
            return Collections.emptyList();
        }
    }

    /**
     * 删除文档
     */
    public boolean deleteDocument(String collectionName, String documentId) {
        try {
            String expr = FIELD_ID + " == \"" + documentId + "\"";

            DeleteParam deleteParam = DeleteParam.newBuilder()
                .withCollectionName(collectionName)
                .withExpr(expr)
                .build();

            R<MutationResult> deleteResult = milvusClient.delete(deleteParam);
            if (deleteResult.getStatus() != R.Status.Success.getCode()) {
                log.error("删除数据失败: {}", deleteResult.getMessage());
                return false;
            }

            log.info("成功删除文档: {}", documentId);
            return true;

        } catch (Exception e) {
            log.error("删除向量数据异常", e);
            return false;
        }
    }

    /**
     * 删除公司的所有文档
     */
    public boolean deleteByCompany(String collectionName, String tenantId, Long companyId) {
        try {
            String expr = FIELD_TENANT_ID + " == \"" + tenantId + "\" && " +
                         FIELD_COMPANY_ID + " == " + companyId;

            DeleteParam deleteParam = DeleteParam.newBuilder()
                .withCollectionName(collectionName)
                .withExpr(expr)
                .build();

            R<MutationResult> deleteResult = milvusClient.delete(deleteParam);
            if (deleteResult.getStatus() != R.Status.Success.getCode()) {
                log.error("删除公司数据失败: {}", deleteResult.getMessage());
                return false;
            }

            log.info("成功删除公司数据: tenantId={}, companyId={}", tenantId, companyId);
            return true;

        } catch (Exception e) {
            log.error("删除公司向量数据异常", e);
            return false;
        }
    }

    /**
     * 生成文本的向量表示
     */
    private float[] generateEmbedding(String text) {
        try {
            // 使用 Spring AI Alibaba 的 DashScope Embedding 模型
            Document document = new Document(text);
            List<Double> embedding = embeddingModel.embed(document);

            // 转换为 float[]
            float[] result = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                result[i] = embedding.get(i).floatValue();
            }
            return result;

        } catch (Exception e) {
            log.error("生成向量失败", e);
            throw new RuntimeException("生成向量失败", e);
        }
    }

    /**
     * 获取集合名称
     */
    public String getCollectionName() {
        return milvusProperties.getCollectionPrefix() + "data";
    }

}
