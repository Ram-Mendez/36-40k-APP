package com.acme.carrier;

import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CarrierController {
    private static final Logger log = LoggerFactory.getLogger(CarrierController.class);
    private final String mode;

    public CarrierController(@Value("${carrier.mode:normal}") String mode) {
        this.mode = mode;
    }

    record Quote(String reference, BigDecimal cost) {}
    record ErrorResponse(String error) {}
    record Health(String status) {}

    @GetMapping("/health")
    Health health() {
        return new Health("UP");
    }

    @GetMapping(value = "/quotes", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> quote(@RequestParam(value = "orderId", required = false) String orderId,
                            @RequestParam(value = "amount", required = false) String amount,
                            @RequestHeader(value = "X-Request-ID", required = false) String requestId) throws InterruptedException {
        // The modes reproduce the failures of the previous carrier.
        if ("slow".equals(mode)) Thread.sleep(5000);
        String id = orderId != null && orderId.matches("[0-9]+") ? orderId : "unknown";
        int status = "unavailable".equals(mode) ? 503 : 200;
        log.info("carrier method=GET orderId={} status={} requestId={}", id, status, requestId);
        if ("malformed".equals(mode)) return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body("{invalid-json");
        if (status == 503) return ResponseEntity.status(503).body(new ErrorResponse("temporarily unavailable"));
        return ResponseEntity.ok(new Quote("QUOTE-" + id, new BigDecimal("7.50")));
    }
}
