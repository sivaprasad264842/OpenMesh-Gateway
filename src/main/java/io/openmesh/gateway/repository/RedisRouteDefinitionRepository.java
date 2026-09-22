package io.openmesh.gateway.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openmesh.gateway.model.RouteDefinitionDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisRouteDefinitionRepository implements RouteDefinitionRepository {

    public static final String GATEWAY_ROUTES_HASH_KEY = "openmesh:gateway:dynamic_routes";

    private final ReactiveRedisTemplate<String, String> reactiveStringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        if (reactiveStringRedisTemplate == null || reactiveStringRedisTemplate.opsForHash() == null) {
            return Flux.empty();
        }
        return reactiveStringRedisTemplate.opsForHash()
                .values(GATEWAY_ROUTES_HASH_KEY)
                .flatMap(rawJson -> {
                    try {
                        RouteDefinitionDto dto = objectMapper.readValue((String) rawJson, RouteDefinitionDto.class);
                        return Mono.just(dto.toRouteDefinition());
                    } catch (Exception e) {
                        log.error("Failed to deserialize route from Redis: {}", rawJson, e);
                        return Mono.empty();
                    }
                })
                .onErrorResume(e -> {
                    log.warn("Error reading route definitions from Redis: {}. Returning empty.", e.getMessage());
                    return Flux.empty();
                });
    }

    @Override
    public Mono<Void> save(Mono<RouteDefinition> route) {
        if (reactiveStringRedisTemplate == null || reactiveStringRedisTemplate.opsForHash() == null) {
            return Mono.empty();
        }
        return route.flatMap(routeDef -> {
            try {
                RouteDefinitionDto dto = RouteDefinitionDto.fromRouteDefinition(routeDef);
                String json = objectMapper.writeValueAsString(dto);
                return reactiveStringRedisTemplate.opsForHash()
                        .put(GATEWAY_ROUTES_HASH_KEY, routeDef.getId(), json)
                        .doOnSuccess(v -> log.info("Successfully persisted dynamic route '{}' to Redis", routeDef.getId()))
                        .then();
            } catch (JsonProcessingException e) {
                log.error("Error serializing route definition '{}'", routeDef.getId(), e);
                return Mono.error(new IllegalArgumentException("Cannot serialize route definition", e));
            }
        });
    }


    
    @Override
    public Mono<Void> delete(Mono<String> routeId) {
        if (reactiveStringRedisTemplate == null || reactiveStringRedisTemplate.opsForHash() == null) {
            return Mono.empty();
        }
        return routeId.flatMap(id ->
                reactiveStringRedisTemplate.opsForHash()
                        .remove(GATEWAY_ROUTES_HASH_KEY, id)
                        .flatMap(removedCount -> {
                            if (removedCount > 0) {
                                log.info("Deleted dynamic route '{}' from Redis", id);
                                return Mono.empty();
                            } else {
                                log.warn("Route '{}' not found in Redis for deletion", id);
                                return Mono.error(new NotFoundException("Route definition not found: " + id));
                            }
                        })
        );
    }
}
