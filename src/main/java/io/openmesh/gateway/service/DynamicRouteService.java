package io.openmesh.gateway.service;

import io.openmesh.gateway.model.RouteDefinitionDto;
import io.openmesh.gateway.repository.RedisRouteDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicRouteService {

    private final RouteDefinitionRepository routeDefinitionRepository;
    private final ApplicationEventPublisher eventPublisher;

    public Flux<RouteDefinitionDto> getAllRoutes() {
        return routeDefinitionRepository.getRouteDefinitions()
                .map(RouteDefinitionDto::fromRouteDefinition);

    }
    
    public Mono<RouteDefinitionDto> getRouteById(String routeId) {
        return routeDefinitionRepository.getRouteDefinitions()
                .filter(route -> route.getId().equals(routeId))
                .next()
                .map(RouteDefinitionDto::fromRouteDefinition);
    }

    public Mono<RouteDefinitionDto> saveRoute(RouteDefinitionDto dto) {
        RouteDefinition definition = dto.toRouteDefinition();
        return routeDefinitionRepository.save(Mono.just(definition))
                .doOnSuccess(v -> {
                    publishRefreshEvent();
                    log.info("Successfully added route '{}' and triggered RefreshRoutesEvent ", dto.getId());

                })
                .thenReturn(dto);

    }
    
    public Mono<Void> deleteRoute(String routeId) {
        return routeDefinitionRepository.delete(Mono.just(routeId))
                .doOnSuccess(v -> {
                    publishRefreshEvent();
                    log.info("Successfully removed route'{}' and triggered RefreshRoutesEvent", routeId);
                });
    }

    public Mono<Void> refreshRoutes() {
        return Mono.fromRunnable(this::publishRefreshEvent).then();
    }

    private void publishRefreshEvent() {
        eventPublisher.publishEvent(new RefreshRoutesEvent(this));
        log.info("Published RefreshRouteEvent to dynamically reload Gateway routing table");
    }

    
}
