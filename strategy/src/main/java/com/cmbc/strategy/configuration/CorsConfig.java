package com.cmbc.strategy.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @Author: Cly
 * @Date: 2025/12/02 11:27
 * @Description: 头寸查询 RestTemplate配置
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowCredentials(true).maxAge(3600)
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("*").allowedOriginPatterns("*");
    }
}
