package com.acme.shipping;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Validated
@ConfigurationProperties("shipping.pricing")
public record PricingProperties(
        @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal flatCost,
        @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2)
                BigDecimal freeThreshold) {}
