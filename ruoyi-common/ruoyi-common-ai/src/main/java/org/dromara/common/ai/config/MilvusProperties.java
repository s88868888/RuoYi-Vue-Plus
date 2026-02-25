package org.dromara.common.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Milvus 配置属性
 *
 * @author ruoyi
 */
@Data
@Component
@ConfigurationProperties(prefix = "milvus")
public class MilvusProperties {

    /**
     * Milvus 服务器地址
     */
    private String host = "192.168.169.205";

    /**
     * Milvus 服务器端口
     */
    private Integer port = 19530;

    /**
     * 用户名（可选）
     */
    private String username;

    /**
     * 密码（可选）
     */
    private String password;

    /**
     * 数据库名称
     */
    private String database = "default";

    /**
     * 连接超时时间（毫秒）
     */
    private Long connectTimeout = 10000L;

    /**
     * 保持连接时间（毫秒）
     */
    private Long keepAliveTime = 55000L;

    /**
     * 向量维度
     */
    private Integer dimension = 1536;

    /**
     * 集合名称前缀
     */
    private String collectionPrefix = "company_";

}
