package org.dromara.common.ai.config;

import org.eclipse.jetty.client.HttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JettyClientHttpRequestFactory;

import java.time.Duration;

/**
 * AI HTTP 客户端全局超时配置
 * <p>
 * Jetty 有两类超时：
 * - readTimeout (total timeout)：整个请求最长允许的时间，setReadTimeout 控制
 * - idleTimeout：socket 空闲多久没新字节回来就断开，默认 30 秒
 * <p>
 * qwen-long 等大模型在处理长文档时，TTFT（首个 token 到达时间）可能超过 30 秒，
 * 必须把 Jetty HttpClient 的 idleTimeout 拉长，否则会被静默断开（Idle timeout expired）。
 */
@Configuration
public class AiHttpConfig {

    @Value("${spring.ai.dashscope.http-client.read-timeout:300000}")
    private int readTimeout;

    @Value("${spring.ai.dashscope.http-client.connect-timeout:30000}")
    private int connectTimeout;

    /**
     * 全局 Jetty HttpClient（idleTimeout 与 readTimeout 一致），所有 AI 走 RestClient 的请求共用。
     * destroyMethod=stop 让 Spring 在销毁时释放 Jetty 线程池。
     */
    @Bean(destroyMethod = "stop")
    public HttpClient aiJettyHttpClient() throws Exception {
        HttpClient client = new HttpClient();
        client.setIdleTimeout(readTimeout);
        client.setConnectTimeout(connectTimeout);
        client.start();
        return client;
    }

    @Bean
    public RestClientCustomizer aiRestClientCustomizer(HttpClient aiJettyHttpClient) {
        return builder -> {
            JettyClientHttpRequestFactory factory = new JettyClientHttpRequestFactory(aiJettyHttpClient);
            factory.setReadTimeout(Duration.ofMillis(readTimeout));
            builder.requestFactory(factory);
        };
    }
}
