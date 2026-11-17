package com.acme.shipping;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class Batch17QuoteHistoryTest {
    private final QuoteService service = new QuoteService(
            new PricingProperties(new BigDecimal("7.50"), new BigDecimal("200.00")));

    @Test
    void sameOrderCanCrossTheThresholdInBothDirections() {
        assertThat(service.quote(701, new BigDecimal("199.99")).cost()).isEqualByComparingTo("7.50");
        var free = service.quote(701, new BigDecimal("200.00"));
        assertThat(free.cost()).isEqualByComparingTo("0.00");
        assertThat(free.reference()).isEqualTo("QUOTE-701-200.00");
        assertThat(service.quote(701, new BigDecimal("199.99")).cost()).isEqualByComparingTo("7.50");
    }

    @Test
    void quoteHistoryCannotAffectAnotherOrderOrEquivalentAmounts() {
        service.quote(702, new BigDecimal("200"));
        assertThat(service.quote(703, new BigDecimal("199.99")).cost()).isEqualByComparingTo("7.50");
        assertThat(service.quote(702, new BigDecimal("200.00")))
                .isEqualTo(service.quote(702, new BigDecimal("200")));
        assertThat(service.quote(702, new BigDecimal("10.00")).cost()).isEqualByComparingTo("7.50");
    }
}
