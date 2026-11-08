package com.acme.shipping;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

class QuoteServiceTest {
    private final QuoteService quotes =
            new QuoteService(
                    new PricingProperties(new BigDecimal("7.50"), new BigDecimal("200.00")));

    @ParameterizedTest
    @CsvSource({
        "0.00, 7.50",
        "199.99, 7.50",
        "200.00, 0.00",
        "200.01, 0.00",
        "999999999999.99, 0.00"
    })
    void appliesShippingPolicyAtItsBoundaries(String amount, String expectedCost) {
        var quote = quotes.quote(42, new BigDecimal(amount));
        assertThat(quote.cost()).isEqualTo(new BigDecimal(expectedCost));
    }

    @Test
    void usesConfiguredPricesAndNormalizesEquivalentAmounts() {
        var custom =
                new QuoteService(
                        new PricingProperties(new BigDecimal("12.30"), new BigDecimal("100.00")));
        assertThat(custom.quote(42, new BigDecimal("99.99")).cost())
                .isEqualTo(new BigDecimal("12.30"));
        assertThat(custom.quote(42, new BigDecimal("100.00")).cost())
                .isEqualTo(new BigDecimal("0.00"));
        assertThat(custom.quote(42, new BigDecimal("100")))
                .isEqualTo(custom.quote(42, new BigDecimal("100.00")));
    }
}
