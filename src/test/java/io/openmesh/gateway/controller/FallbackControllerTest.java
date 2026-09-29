package io.openmesh.gateway.controller;

import io.openmesh.gateway.model.FallbackResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class FallbackControllerTest {

    private FallbackController controller;

    @BeforeEach
    void setUp() {
        controller = new FallbackController();
    }

    @Test
    @DisplayName("Should return 503 Service Unavailable on circuit breaker trip")
    void shouldReturn503OnCircuitBreakerFallback() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/fallback/service-unavailable").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(
                ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR,
                new RuntimeException("Connection refused to downstream")
        );

        StepVerifier.create(controller.handleServiceUnavailable(exchange))
                .assertNext(res -> {
                    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    FallbackResponse body = res.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getStatus()).isEqualTo(503);
                    assertThat(body.getError()).isEqualTo("Service Unavailable");
                    assertThat(body.getRetryAfterSeconds()).isEqualTo(10);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return 504 Gateway Timeout on downstream timeout")
    void shouldReturn504OnTimeoutFallback() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/fallback/timeout").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(controller.handleTimeout(exchange))
                .assertNext(res -> {
                    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
                    FallbackResponse body = res.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getStatus()).isEqualTo(504);
                    assertThat(body.getError()).isEqualTo("Gateway Timeout");
                    assertThat(body.getRetryAfterSeconds()).isEqualTo(5);
                })
                .verifyComplete();
    }
}
