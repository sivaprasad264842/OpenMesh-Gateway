package io.openmesh.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openmesh.gateway.model.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ZeroTrustIdentityFilterTest {

    private ZeroTrustIdentityFilter filter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        filter = new ZeroTrustIdentityFilter(objectMapper);
    }

    @Test
    @DisplayName("Should strip incoming spoofed identity headers from untrusted clients")
    void shouldStripSpoofedHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/resource")
                .header(ZeroTrustIdentityFilter.HEADER_USER_ID, "attacker-user")
                .header(ZeroTrustIdentityFilter.HEADER_TENANT_ID, "evil-tenant")
                .header(ZeroTrustIdentityFilter.HEADER_GATEWAY_VERIFIED, "true")
                .header("Tenant-ID", "tenant-regular")
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<ServerHttpRequest> executedRequest = new AtomicReference<>();

        GatewayFilterChain chain = filterExchange -> {
            executedRequest.set(filterExchange.getRequest());
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ServerHttpRequest downstreamReq = executedRequest.get();
        assertThat(downstreamReq).isNotNull();

        // The downstream request should have verified headers injected based on context, not attacker's spoofed user
        assertThat(downstreamReq.getHeaders().getFirst(ZeroTrustIdentityFilter.HEADER_USER_ID)).isEqualTo("anonymous");
        assertThat(downstreamReq.getHeaders().getFirst(ZeroTrustIdentityFilter.HEADER_TENANT_ID)).isEqualTo("tenant-regular");
        assertThat(downstreamReq.getHeaders().getFirst(ZeroTrustIdentityFilter.HEADER_GATEWAY_VERIFIED)).isEqualTo("true");
        assertThat(downstreamReq.getHeaders().getFirst(ZeroTrustIdentityFilter.HEADER_GATEWAY_TIMESTAMP)).isNotNull();
    }

    @Test
    @DisplayName("Should extract verified JWT claims and propagate identity downstream")
    void shouldPropagateVerifiedJwtIdentity() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .subject("alice-123")
                .claim("tenant_id", "acme-corp")
                .claim("tier", "ENTERPRISE")
                .claim("scope", "read write")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build();

        JwtAuthenticationToken auth = new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("SCOPE_read")),
                "alice-123"
        );

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/orders")
                .header(ZeroTrustIdentityFilter.HEADER_USER_ID, "forged-id")
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<ServerHttpRequest> executedRequest = new AtomicReference<>();

        GatewayFilterChain chain = filterExchange -> {
            executedRequest.set(filterExchange.getRequest());
            return Mono.empty();
        };

        Mono<Void> testPipeline = filter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(new SecurityContextImpl(auth))));

        StepVerifier.create(testPipeline)
                .verifyComplete();

        ServerHttpRequest downstreamReq = executedRequest.get();
        assertThat(downstreamReq).isNotNull();
        assertThat(downstreamReq.getHeaders().getFirst(ZeroTrustIdentityFilter.HEADER_USER_ID)).isEqualTo("alice-123");
        assertThat(downstreamReq.getHeaders().getFirst(ZeroTrustIdentityFilter.HEADER_TENANT_ID)).isEqualTo("acme-corp");
        assertThat(downstreamReq.getHeaders().getFirst(ZeroTrustIdentityFilter.HEADER_GATEWAY_VERIFIED)).isEqualTo("true");

        TenantContext tenantCtx = exchange.getAttribute(ZeroTrustIdentityFilter.TENANT_CONTEXT_ATTR);
        assertThat(tenantCtx).isNotNull();
        assertThat(tenantCtx.getUserId()).isEqualTo("alice-123");
        assertThat(tenantCtx.getTenantId()).isEqualTo("acme-corp");
        assertThat(tenantCtx.getTier()).isEqualTo("ENTERPRISE");
    }

    @Test
    @DisplayName("Should block request with HTTP 403 when route requiredScopes are missing")
    void shouldBlockWhenRequiredScopesMissing() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .subject("bob-456")
                .claim("tenant_id", "beta-corp")
                .claim("scope", "read") // Missing 'admin:write'
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build();

        JwtAuthenticationToken auth = new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                "bob-456"
        );

        MockServerHttpRequest request = MockServerHttpRequest.delete("/api/v1/orders/99").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Attach route with requiredScopes
        Route route = Route.async()
                .id("protected-route")
                .uri(URI.create("http://localhost:8081"))
                .order(1)
                .predicate(swe -> true)
                .metadata("requiredScopes", List.of("admin:write"))
                .build();
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route);

        GatewayFilterChain chain = filterExchange -> Mono.empty();

        Mono<Void> testPipeline = filter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(new SecurityContextImpl(auth))));

        StepVerifier.create(testPipeline)
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
