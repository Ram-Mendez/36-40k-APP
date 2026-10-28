package com.acme.shipping;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class QuoteService {
    private final PricingProperties pricing;

    public QuoteService(PricingProperties pricing) {
        this.pricing = pricing;
    }

    public record Quote(String reference, BigDecimal cost) {}

    /** Pure quotation: it does not book a shipment or read the Warehouse database. */
    public Quote quote(long orderId, BigDecimal amount) {
        BigDecimal normalized = amount.setScale(2, RoundingMode.UNNECESSARY);
        BigDecimal cost =
                normalized.compareTo(pricing.freeThreshold()) >= 0
                        ? BigDecimal.ZERO
                        : pricing.flatCost();
        return new Quote(
                "QUOTE-" + orderId + "-" + normalized.toPlainString(),
                cost.setScale(2, RoundingMode.UNNECESSARY));
    }
}
