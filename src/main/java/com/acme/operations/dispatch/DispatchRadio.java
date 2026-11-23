package com.acme.operations.dispatch;

import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Profile("turno-cero")
class DispatchRadio {
    record Signal(String requestId, String worker) {}

    private static final Logger log = LoggerFactory.getLogger(DispatchRadio.class);

    @Async("dispatchExecutor")
    public CompletableFuture<Signal> receive() {
        log.info("Dispatch radio received a message");
        return CompletableFuture.completedFuture(
                new Signal(MDC.get("requestId"), Thread.currentThread().getName()));
    }
}
