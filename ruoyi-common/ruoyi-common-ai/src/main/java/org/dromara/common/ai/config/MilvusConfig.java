package org.dromara.common.ai.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Milvus 配置类
 *
 * @author ruoyi
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(MilvusProperties.class)
@ConditionalOnProperty(prefix = "milvus", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MilvusConfig {

    private final MilvusProperties milvusProperties;

    @Bean
    public MilvusServiceClient milvusClient() {
        log.info("初始化 Milvus 客户端，连接地址: {}:{}",
            milvusProperties.getHost(), milvusProperties.getPort());

        ConnectParam.Builder builder = ConnectParam.newBuilder()
            .withHost(milvusProperties.getHost())
            .withPort(milvusProperties.getPort())
            .withConnectTimeout(milvusProperties.getConnectTimeout())
            .withKeepAliveTime(milvusProperties.getKeepAliveTime());

        // 如果配置了用户名和密码
        if (milvusProperties.getUsername() != null && !milvusProperties.getUsername().isEmpty()) {
            builder.withAuthorization(milvusProperties.getUsername(), milvusProperties.getPassword());
        }

        // 如果配置了数据库名称
        if (milvusProperties.getDatabase() != null && !milvusProperties.getDatabase().isEmpty()) {
            builder.withDatabaseName(milvusProperties.getDatabase());
        }

        MilvusServiceClient client = new MilvusServiceClient(builder.build());
        log.info("Milvus 客户端初始化成功");

        return client;
    }

}
