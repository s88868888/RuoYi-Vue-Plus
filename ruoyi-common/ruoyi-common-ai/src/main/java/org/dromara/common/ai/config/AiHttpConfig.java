package org.dromara.common.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JettyClientHttpRequestFactory;

import java.time.Duration;

/**
 * AI HTTP 客户端全局超时配置
 * JettyClientHttpRequestFactory.setReadTimeout 实际控制 Jetty 的 total timeout
 */
@Configuration
public class AiHttpConfig {

    @Value("${spring.ai.dashscope.http-client.read-timeout:300000}")
    private int readTimeout;

    @Bean
    public RestClientCustomizer aiRestClientCustomizer() {
        return builder -> {
            JettyClientHttpRequestFactory factory = new JettyClientHttpRequestFactory();
            factory.setReadTimeout(Duration.ofMillis(readTimeout));
            builder.requestFactory(factory);
        };
    }
}
