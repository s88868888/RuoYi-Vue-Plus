package org.dromara.common.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

/**
 * AI HTTP 客户端全局超时配置
 * 使用 Spring Boot 官方 RestClientCustomizer 扩展点，全局覆盖所有 RestClient 实例的超时
 */
@Configuration
public class AiHttpConfig {

    @Value("${spring.ai.dashscope.http-client.connect-timeout:30000}")
    private int connectTimeout;

    @Value("${spring.ai.dashscope.http-client.read-timeout:120000}")
    private int readTimeout;

    @Bean
    public RestClientCustomizer aiRestClientCustomizer() {
        return builder -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofMillis(connectTimeout));
            factory.setReadTimeout(Duration.ofMillis(readTimeout));
            builder.requestFactory(factory);
        };
    }
}
