package io.openmesh.gateway.controller;

import io.openmesh.gateway.model.RouteDefinitionDto;
import io.openmesh.gateway.service.DynamicRouteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DynamicRouteAdminControllerTest {

    @Mock
    private DynamicRouteService dynamicRouteService;

    private DynamicRouteAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new DynamicRouteAdminController(dynamicRouteService);
    }

    @Test
    @DisplayName("Should list all dynamic routes")
    void shouldListAllRoutes() {
        RouteDefinitionDto route = RouteDefinitionDto.builder()
                .id("test-route")
                .uri("http://localhost:8081")
                .predicates(List.of("Path=/test/**"))
                .build();

        when(dynamicRouteService.getAllRoutes()).thenReturn(Flux.just(route));

        StepVerifier.create(controller.listAllRoutes())
                .assertNext(res -> {
                    assertThat(res.getId()).isEqualTo("test-route");
                    assertThat(res.getUri()).isEqualTo("http://localhost:8081");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should create dynamic route")
    void shouldCreateRoute() {
        RouteDefinitionDto input = RouteDefinitionDto.builder()
                .id("new-route")
                .uri("http://downstream:8080")
                .predicates(List.of("Path=/new/**"))
                .build();

        when(dynamicRouteService.saveRoute(any(RouteDefinitionDto.class)))
                .thenReturn(Mono.just(input));

        StepVerifier.create(controller.createRoute(input))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody().getId()).isEqualTo("new-route");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should trigger routes refresh event")
    void shouldTriggerRoutesRefresh() {
        when(dynamicRouteService.refreshRoutes()).thenReturn(Mono.empty());

        StepVerifier.create(controller.refreshRoutes())
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody().get("status")).isEqualTo("SUCCESS");
                })
                .verifyComplete();

        verify(dynamicRouteService, times(1)).refreshRoutes();
    }

    @Test
    @DisplayName("Should delete route and return 204 No Content")
    void shouldDeleteRoute() {
        when(dynamicRouteService.deleteRoute(eq("delete-me"))).thenReturn(Mono.empty());

        StepVerifier.create(controller.deleteRoute("delete-me"))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
                })
                .verifyComplete();
    }
}
