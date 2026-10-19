package com.acme.operations.shipping;

import com.acme.operations.common.BusinessException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class ShippingClient {
    private static final Logger log = LoggerFactory.getLogger(ShippingClient.class);
    private final RestClient client;

    public record Quote(String reference, BigDecimal cost) {}

    public ShippingClient(
            @Value("${shipping.base-url}") String baseUrl,
            @Value("${shipping.connect-timeout:2s}") Duration connectTimeout,
            @Value("${shipping.read-timeout:3s}") Duration readTimeout) {
        var factory =
                new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder().connectTimeout(connectTimeout).build());
        factory.setReadTimeout(readTimeout);
        client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public Quote quote(long orderId, BigDecimal amount) {
        try {
            var request =
                    client.get()
                            .uri(
                                    builder ->
                                            builder.path("/quotes")
                                                    .queryParam("orderId", orderId)
                                                    .queryParam("amount", amount)
                                                    .build());
            String requestId = MDC.get("requestId");
            if (requestId != null) request.header("X-Request-ID", requestId);
            Quote quote = request.retrieve().body(Quote.class);
            if (quote == null
                    || quote.reference() == null
                    || quote.reference().isBlank()
                    || quote.reference().length() > 100
                    || quote.cost() == null
                    || quote.cost().signum() < 0
                    || quote.cost().scale() > 2
                    || quote.cost().compareTo(new BigDecimal("999999999999.99")) > 0)
                throw new IllegalArgumentException("Invalid shipping quote");
            return quote;
        } catch (RestClientException | IllegalArgumentException ex) {
            log.warn(
                    "Shipping quote failed for order {}: {}",
                    orderId,
                    ex.getClass().getSimpleName());
            throw new BusinessException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "SHIPPING_UNAVAILABLE",
                    "Shipping service unavailable; order remains confirmed and reserved, retry"
                        + " later");
        }
    }
}
