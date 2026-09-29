package io.openmesh.gateway.integration;

import io.openmesh.gateway.model.RouteDefinitionDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class GatewayEndToEndIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private ReactiveRedisConnectionFactory connectionFactory;

    @MockBean
    private org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;

    @MockBean
    private org.springframework.data.redis.core.ReactiveStringRedisTemplate reactiveStringRedisTemplate;

    @Test
    @DisplayName("Should expose Actuator health endpoint")
    void shouldExposeActuatorHealth() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    @DisplayName("Should expose Prometheus metrics endpoint")
    void shouldExposePrometheusMetrics() {
        webTestClient.get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_PLAIN);
    }

    @Test
    @DisplayName("Should serve static control plane UI index.html")
    void shouldServeStaticUiIndexHtml() {
        webTestClient.get()
                .uri("/")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("OpenMesh-Gateway"));
    }

    @Test
    @DisplayName("Should mint valid RS256 token via /auth/token endpoint")
    void shouldMintValidRs256Token() {
        webTestClient.post()
                .uri("/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                        "tenantId": "tenant-integration",
                        "userId": "user-integration",
                        "roles": ["ROLE_ADMIN"],
                        "scopes": ["read", "write"]
                    }
                """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.token").isNotEmpty()
                .jsonPath("$.tenantId").isEqualTo("tenant-integration")
                .jsonPath("$.userId").isEqualTo("user-integration");
    }

    @Test
    @DisplayName("Should handle circuit breaker fallback endpoint /fallback/service-unavailable")
    void shouldHandleCircuitBreakerFallback() {
        webTestClient.get()
                .uri("/fallback/service-unavailable")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.error").isEqualTo("Service Unavailable");
    }

    @Test
    @DisplayName("Should handle timeout fallback endpoint /fallback/timeout")
    void shouldHandleTimeoutFallback() {
        webTestClient.get()
                .uri("/fallback/timeout")
                .exchange()
                .expectStatus().isEqualTo(504)
                .expectBody()
                .jsonPath("$.status").isEqualTo(504)
                .jsonPath("$.error").isEqualTo("Gateway Timeout");
    }

    @Test
    @DisplayName("Should perform dynamic route CRUD via /admin/v1/routes")
    @SuppressWarnings("unchecked")
    void shouldPerformDynamicRouteCrud() {
        ReactiveHashOperations<String, Object, Object> hashOps = mock(ReactiveHashOperations.class);
        when(reactiveStringRedisTemplate.opsForHash()).thenReturn((ReactiveHashOperations) hashOps);
        when(hashOps.values(anyString())).thenReturn(Flux.empty());
        when(hashOps.put(anyString(), anyString(), anyString())).thenReturn(Mono.just(true));

        RouteDefinitionDto newRoute = RouteDefinitionDto.builder()
                .id("dynamic-inventory-service")
                .uri("http://inventory-service:8080")
                .predicates(List.of("Path=/inventory/**"))
                .filters(List.of("StripPrefix=1"))
                .build();

        webTestClient.post()
                .uri("/admin/v1/routes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(newRoute)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo("dynamic-inventory-service")
                .jsonPath("$.uri").isEqualTo("http://inventory-service:8080");

        // Test refresh endpoint
        webTestClient.post()
                .uri("/admin/v1/routes/refresh")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("SUCCESS");
    }
}
