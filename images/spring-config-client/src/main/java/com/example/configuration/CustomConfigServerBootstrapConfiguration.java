package com.example.configuration;

import org.springframework.web.client.RestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.config.client.ConfigClientProperties;
import org.springframework.cloud.config.client.ConfigServicePropertySourceLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class CustomConfigServerBootstrapConfiguration {

    @Bean
    public ConfigServicePropertySourceLocator configServicePropertySourceLocator(ConfigClientProperties configClientProperties) throws IOException {
        ConfigServicePropertySourceLocator configServicePropertySourceLocator =  new ConfigServicePropertySourceLocator(configClientProperties);
        configServicePropertySourceLocator.setRestTemplate(customRestTemplate());
        return configServicePropertySourceLocator;
    }

    private RestTemplate customRestTemplate() {
        return new RestTemplateBuilder()
                .interceptors(new CloudRunClientHttpRequestInterceptor())
                .build();
    }
}
