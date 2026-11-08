package com.acme.shipping;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class PricingPropertiesTest {
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PricingProperties.class)
    static class PricingConfiguration {}

    @ParameterizedTest
    @ValueSource(
            strings = {
                "shipping.pricing.flat-cost=-0.01",
                "shipping.pricing.flat-cost=7.501",
                "shipping.pricing.free-threshold=0",
                "shipping.pricing.free-threshold=1000000000000.00"
            })
    void invalidConfigurationStopsStartup(String invalidProperty) {
        new ApplicationContextRunner()
                .withUserConfiguration(PricingConfiguration.class)
                .withPropertyValues(
                        "shipping.pricing.flat-cost=7.50",
                        "shipping.pricing.free-threshold=200.00",
                        invalidProperty)
                .run(context -> org.assertj.core.api.Assertions.assertThat(context).hasFailed());
    }
}
