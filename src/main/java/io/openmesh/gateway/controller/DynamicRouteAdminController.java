package io.openmesh.gateway.controller;


import io.openmesh.gateway.model.RouteDefinitionDto;
import io.openmesh.gateway.service.DynamicRouteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/admin/v1/routes")
@RequiredArgsConstructor 
public class DynamicRouteAdminController {

    private final DynamicRouteService dynamicRouteService;

    @GetMapping 
    public Flux<RouteDefinitionDto> listAllRoutes() {
        return dynamicRouteService.getAllRoutes();
    }

    @GetMapping("/{routeId}")
    public Mono<ResponseEntity<RouteDefinitionDto>> getRouteById(@PathVariable String routeId) {
        return dynamicRouteService.getRouteById(routeId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping 
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ResponseEntity<RouteDefinitionDto>> createRoute(@Valid @RequestBody RouteDefinitionDto routeDto) {
        return dynamicRouteService.saveRoute(routeDto)
                .map(created -> ResponseEntity.status(HttpStatus.CREATED).body(created));
    }

    @PostMapping("/routeId")
    public Mono<ResponseEntity<RouteDefinitionDto>> updateRoute(
        @PathVariable  String routeId,
        @Valid  @RequestBody RouteDefinitionDto routeDto
    ) {

        routeDto.setId(routeId);
        return dynamicRouteService.saveRoute(routeDto)
                .map(ResponseEntity::ok);

    }
    
    @DeleteMapping("/{routeId}")
    public Mono<ResponseEntity<Void>> deleteRoute(@PathVariable String routeId) {
        return dynamicRouteService.deleteRoute(routeId)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()))
                .onErrorResume(e -> Mono.just(ResponseEntity.notFound().build()));

    }
    
    @PostMapping("/refresh")
    public Mono<ResponseEntity<Map<String, String>>> refreshRoutes() {
        return dynamicRouteService.refreshRoutes()
        .then(Mono.just(ResponseEntity.ok(Map.of(
            "status", "SUCCESS",
            "message", "Gateway routes dynamic reload triggered successfully."

                ))));
    }


    
}
