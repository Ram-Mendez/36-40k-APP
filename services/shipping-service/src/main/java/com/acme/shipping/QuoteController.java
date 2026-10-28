package com.acme.shipping;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
public class QuoteController {
    private final QuoteService quotes;

    public QuoteController(QuoteService quotes) {
        this.quotes = quotes;
    }

    @GetMapping("/quotes")
    public QuoteService.Quote quote(
            @RequestParam @Positive long orderId,
            @RequestParam @DecimalMin("0.00") @Digits(integer = 12, fraction = 2)
                    BigDecimal amount) {
        return quotes.quote(orderId, amount);
    }
}
