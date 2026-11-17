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
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.time.Duration;

// Spring manages this component and injects it wherever needed.
@Component
public class ShippingClient {
    // Diagnostic Logger; reports failure without reversing the complete remote response.
    private static final Logger log = LoggerFactory.getLogger(ShippingClient.class);
    // Reusable HTTP client with already configured base URL and waiting limits.
    private final CarrierApi client;

    // DTO: reference and cost received from the external service.
    public record Quote(String reference, BigDecimal cost) {}

    // Spring implements this HTTP contract and turns the response into Quote.
    public interface CarrierApi {
        @GetExchange("/quotes")
        Quote quote(@RequestParam("orderId") long orderId,
                    @RequestParam("amount") BigDecimal amount,
                    @RequestHeader(value = "X-Request-ID", required = false) String requestId);
    }

    // Spring passes the URL and the configuration timeouts when building the client.
    public ShippingClient(
            // The base URL is defined outside the code to be able to change the destination by environment.
            @Value("${shipping.base-url}") String baseUrl,
            // Limit to open connection; the following parameter limits reading wait.
            @Value("${shipping.connect-timeout:2s}") Duration connectTimeout,
            @Value("${shipping.read-timeout:3s}") Duration readTimeout) {
        // It creates HTTP transport with maximum time to establish connection.
        var factory =
                new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder().connectTimeout(connectTimeout).build());
        // It limits the waiting for a response to prevent the request from remaining pending indefinitely.
        factory.setReadTimeout(readTimeout);
        // It builds the client; it still doesn't call the remote service.
        var restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        client = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
                .build().createClient(CarrierApi.class);
    }

    // It requests a reading-only quotation; this method does not create a real shipment.
    public Quote quote(long orderId, BigDecimal amount) {
        // It groups transport and content failures to translate them into a business error.
        try {
            // Recovers the traceability identifier of the current request.
            String requestId = MDC.get("requestId");
            // Spring sends the parameters and automatically turns the JSON into Quote.
            Quote quote = client.quote(orderId, amount, requestId);
            // It rejects incomplete quotations, invalid references or amounts outside the accepted format.
            if (quote == null
                    || quote.reference() == null
                    || quote.reference().isBlank()
                    || quote.reference().length() > 100
                    || quote.cost() == null
                    || quote.cost().signum() < 0
                    || quote.cost().scale() > 2
                    || quote.cost().compareTo(new BigDecimal("999999999999.99")) > 0)
                // An invalid content enters the catch just like an HTTP failure.
                throw new IllegalArgumentException("Invalid shipping quote");
            // Returns the validated quote so that the service can complete the order.
            return quote;
        // Translates HTTP or validation errors to a 503; does not make automatic reattempts.
        } catch (RestClientException | IllegalArgumentException ex) {
            // Record order and type of error for diagnosis.
            log.warn(
                    "Shipping quote failed for order {}: {}",
                    orderId,
                    ex.getClass().getSimpleName());
            // The handler returns 503; the caller does not continue to the section that consumes inventory.
            throw new BusinessException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "SHIPPING_UNAVAILABLE",
                    "Shipping service unavailable; order remains confirmed and reserved, retry"
                        + " later");
        }
    }
}
