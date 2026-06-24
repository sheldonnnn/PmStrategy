package com.cmbc.oms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    // ======================== 2. 普通短连接 RestTemplate (原生实现，无连接池) ======================== /** *
    // 普通HTTP短连接 (命名: restTemplateNormal), 加 @Primary 设为默认注入 * 适用:
    // 低频调用、一次性请求、不需要长连接场景 * 每次请求新建TCP, 请求完成主动断开 */
    @Bean
    @Primary
    public RestTemplate restTemplateNormal() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // 基础超时配置
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        System.setProperty("http.keepAlive","false");
        return new RestTemplate(factory);
    }

    /**
     * 长连接 RestTemplate (命名: restTemplateLong)  * 适用: 高频接口、连续调用、需要复用TCP连接
     */
    @Bean("restTemplateLong")
    public RestTemplate restTemplateLong() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // 基础超时配置
        // 连接超时
        factory.setConnectTimeout(2000);
        // 响应读取超时
        factory.setReadTimeout(5000);
        System.setProperty("http.keepAlive","true");
        System.setProperty("http.maxConnections","100");

        return new RestTemplate(factory);
    }
}
