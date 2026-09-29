package io.openmesh.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openmesh.gateway.model.RateLimitResult;
import io.openmesh.gateway.model.TenantContext;
import io.openmesh.gateway.service.RateLimitService;
import io.openmesh.gateway.service.TenantMeteringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantRateLimiterFilterTest {

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private TenantMeteringService tenantMeteringService;

    private ObjectMapper objectMapper;
    private TenantRateLimiterGatewayFilterFactory factory;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        factory = new TenantRateLimiterGatewayFilterFactory(rateLimitService, tenantMeteringService, objectMapper);
    }

    @Test
    @DisplayName("Should permit request and set rate limit headers when tokens are available")
    void shouldPermitRequestWhenTokensAvailable() {
        TenantRateLimiterGatewayFilterFactory.Config config = new TenantRateLimiterGatewayFilterFactory.Config();
        config.setReplenishRate(10.0);
        config.setBurstCapacity(20.0);

        RateLimitResult allowedResult = RateLimitResult.builder()
                .allowed(true)
                .remainingTokens(19)
                .waitOrResetSeconds(1)
                .tenantId("tenant-alpha")
                .replenishRate(10.0)
                .burstCapacity(20.0)
                .build();

        when(rateLimitService.checkRateLimit(eq("tenant-alpha"), eq(10.0), eq(20.0), eq(1)))
                .thenReturn(Mono.just(allowedResult));

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/data")
                .header("Tenant-ID", "tenant-alpha")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainExecuted = new AtomicBoolean(false);
        GatewayFilterChain chain = filterExchange -> {
            chainExecuted.set(true);
            return Mono.empty();
        };

        GatewayFilter filter = factory.apply(config);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(chainExecuted.get()).isTrue();
        assertThat(exchange.getResponse().getHeaders().getFirst(TenantRateLimiterGatewayFilterFactory.HEADER_RATE_LIMIT_REMAINING))
                .isEqualTo("19");
        assertThat(exchange.getResponse().getHeaders().getFirst(TenantRateLimiterGatewayFilterFactory.HEADER_RATE_LIMIT_BURST))
                .isEqualTo("20");
        assertThat(exchange.getResponse().getHeaders().getFirst(TenantRateLimiterGatewayFilterFactory.HEADER_RATE_LIMIT_REPLENISH))
                .isEqualTo("10");
        assertThat(exchange.getResponse().getHeaders().getFirst(TenantRateLimiterGatewayFilterFactory.HEADER_RATE_LIMIT_RESET))
                .isEqualTo("1");
    }

    @Test
    @DisplayName("Should block request with HTTP 429 when rate limit is exceeded")
    void shouldBlockWith429WhenLimitExceeded() {
        TenantRateLimiterGatewayFilterFactory.Config config = new TenantRateLimiterGatewayFilterFactory.Config();
        config.setReplenishRate(5.0);
        config.setBurstCapacity(10.0);

        RateLimitResult rejectedResult = RateLimitResult.builder()
                .allowed(false)
                .remainingTokens(0)
                .waitOrResetSeconds(3)
                .tenantId("tenant-spammer")
                .replenishRate(5.0)
                .burstCapacity(10.0)
                .build();

        when(rateLimitService.checkRateLimit(eq("tenant-spammer"), eq(5.0), eq(10.0), eq(1)))
                .thenReturn(Mono.just(rejectedResult));

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/heavy-endpoint")
                .header("Tenant-ID", "tenant-spammer")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainExecuted = new AtomicBoolean(false);
        GatewayFilterChain chain = filterExchange -> {
            chainExecuted.set(true);
            return Mono.empty();
        };

        GatewayFilter filter = factory.apply(config);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(chainExecuted.get()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getFirst(TenantRateLimiterGatewayFilterFactory.HEADER_RATE_LIMIT_REMAINING))
                .isEqualTo("0");
        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER))
                .isEqualTo("3");

        verify(tenantMeteringService, times(1)).recordRateLimitDrop(eq("tenant-spammer"), anyString());
    }

    @Test
    @DisplayName("Should extract tenant from TenantContext attribute when set by ZeroTrust filter")
    void shouldExtractTenantFromContext() {
        TenantRateLimiterGatewayFilterFactory.Config config = new TenantRateLimiterGatewayFilterFactory.Config();
        config.setTier("PRO");

        RateLimitResult allowedResult = RateLimitResult.builder()
                .allowed(true)
                .remainingTokens(99)
                .waitOrResetSeconds(1)
                .tenantId("tenant-jwt-verified")
                .replenishRate(50.0)
                .burstCapacity(100.0)
                .build();

        when(rateLimitService.checkRateLimit(eq("tenant-jwt-verified"), eq(50.0), eq(100.0), eq(1)))
                .thenReturn(Mono.just(allowedResult));

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/profile").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        TenantContext tenantContext = TenantContext.builder()
                .tenantId("tenant-jwt-verified")
                .userId("user-555")
                .tier("PRO")
                .authenticated(true)
                .build();
        exchange.getAttributes().put(ZeroTrustIdentityFilter.TENANT_CONTEXT_ATTR, tenantContext);

        GatewayFilter filter = factory.apply(config);

        StepVerifier.create(filter.filter(exchange, filterExchange -> Mono.empty()))
                .verifyComplete();

        verify(rateLimitService).checkRateLimit("tenant-jwt-verified", 50.0, 100.0, 1);
    }
}
