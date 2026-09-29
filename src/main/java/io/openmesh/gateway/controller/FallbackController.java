package io.openmesh.gateway.controller;

import io.openmesh.gateway.model.FallbackResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Slf4j
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping("/service-unavailable")
    public Mono<ResponseEntity<FallbackResponse>> handleServiceUnavailable(ServerWebExchange exchange) {
        Throwable exception = exchange.getAttribute(ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR);
        String details = (exception != null) ? exception.getMessage() : "Downstream microservice returned 5xx error or is unresponsive.";
        String exceptionType = (exception != null) ? exception.getClass().getSimpleName() : "CircuitBreakerOpenException";

        log.warn("Fallback triggered: /fallback/service-unavailable. Cause: {} - {}", exceptionType, details);

        FallbackResponse response = FallbackResponse.builder()
                .timestamp(Instant.now().toString())
                .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                .error("Service Unavailable")
                .message("Downstream target microservice is currently unavailable. Circuit breaker fallback active.")
                .circuitBreaker(exceptionType)
                .path(exchange.getRequest().getPath().value())
                .retryAfterSeconds(10)
                .build();

        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response));
    }

    @RequestMapping("/timeout")
    public Mono<ResponseEntity<FallbackResponse>> handleTimeout(ServerWebExchange exchange) {
        Throwable exception = exchange.getAttribute(ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR);
        String details = (exception != null) ? exception.getMessage() : "Downstream service exceeded SLA threshold (>1500ms).";

        log.warn("Fallback triggered: /fallback/timeout. Cause: {}", details);

        FallbackResponse response = FallbackResponse.builder()
                .timestamp(Instant.now().toString())
                .status(HttpStatus.GATEWAY_TIMEOUT.value())
                .error("Gateway Timeout")
                .message("Downstream microservice response exceeded 1500ms timeout threshold.")
                .circuitBreaker("TimeLimiterTimeoutException")
                .path(exchange.getRequest().getPath().value())
                .retryAfterSeconds(5)
                .build();

        return Mono.just(ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(response));
    }

    @RequestMapping("/default")
    public Mono<ResponseEntity<FallbackResponse>> handleDefaultFallback(ServerWebExchange exchange) {
        return handleServiceUnavailable(exchange);
    }
}
