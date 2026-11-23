package com.acme.operations.dispatch;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@Profile("turno-cero")
@EnableCaching
@EnableAsync
@EnableConfigurationProperties(DispatchConfiguration.DispatchProperties.class)
class DispatchConfiguration {
    @ConfigurationProperties("dispatch")
    record DispatchProperties(Duration loadingWindow) {}

    @Bean
    CacheManager dispatchCacheManager() {
        return new ConcurrentMapCacheManager("dock-capacity");
    }

    @Bean
    ThreadPoolTaskExecutor dispatchExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(16);
        executor.setThreadNamePrefix("dispatch-");
        return executor;
    }
}
