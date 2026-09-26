package io.openmesh.gateway.filter;



import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openmesh.gateway.model.FallbackResponse;
import io.openmesh.gateway.model.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequest.Builder;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;


@Slf4j
@Component
@RequiredArgsConstructor 
public class ZeroTrustIdentifyFilter implements GlobalFilter, Ordered {

    public static final String TENANT_CONTEXT_ATTR = "openmesh.tenantContext";
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_TENANT_ID = "X-Tenant-Id";
    public static final String HEADER_USER_ROLES = "X-User-Roles";
    public static final String HEADER_GATEWAY_VERIFIED = "X-Gateway-Verified";
    public static final String HEADER_GATEWAY_TIMESTAMP = "X-Gateway-Timestamp";

    private final ObjectMapper objectMapper;
    
    @Override 
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 50;
    }

    @Override 
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest.Builder sanitizedRequestBuilder = exchange.getRequest().mutate()
            .headers(httpHeaders -> {
                httpHeaders.remove(HEADER_USER_ID);
                httpHeaders.remove(HEADER_TENANT_ID);
                httpHeaders.remove(HEADER_USER_ROLES);
                httpHeaders.remove(HEADER_GATEWAY_VERIFIED);
                httpHeaders.remove(HEADER_GATEWAY_TIMESTAMP);
                });

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext :: getAuthentication)
                .defaultIfEmpty(createAnonymousAuth())
                .flatMap(authentication -> processAuthentication(exchange, sanitizedRequestBuilder, authentication,
                        chain));
    }

    private Mono<Void> processAuthentication(ServerWebExchange exchange, Builder reqBuilder,
            Authentication auth, GatewayFilterChain chain) {

        TenantContext tenantContext;

        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();

            String userId = jwt.getSubject() != null ? jwt.getSubject() : "unknown-user";
            String tenantId = extractTenantId(jwt);
            List<String> roles = jwtAuth.getAuthorities().stream()
                    .map(a -> a.getAuthority())
                    .collect(Collectors.toList());

            List<String> scopes = extractScopes(jwt);
            String tier = jwt.getClaimAsString("tier");
            if (tier == null) {
                tier = "PRO";
            }

            tenantContext = TenantContext.builder()
                    .tenantId(tenantId)
                    .userId(userId)
                    .username(userId)
                    .roles(roles)
                    .scopes(scopes)
                    .tier(tier)
                    .authenticated(true)
                    .build();

            Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            if (route != null && route.getMetadata() != null) {
                Object requiredScopesObj = route.getMetadata().get("requiredScopes");
                if (requiredScopesObj != null) {
                    List<String> requiredScopes = parseRequiredScopes(requiredScopesObj);
                    boolean hasAllScopes = requiredScopes.stream()
                            .allMatch(rs -> scopes.contains(rs) || roles.contains("ROLE_ADMIN"));

                    if (!hasAllScopes) {
                        log.warn("Access denied for user '{}', tenant '{}': missing required scopes {}",
                                userId, tenantId, requiredScopes);
                        return writeForbiddenResponse(exchange,
                                "Insufficient scope permission. Required: " + requiredScopes);
                    }

                }

            }
        } else {

            //Unauthorised or Public request

            String tenantHeader = exchange.getRequest().getHeaders().getFirst("Tenant-ID");
            if(tenantHeader == null || tenantHeader.isBlank()){
                tenantHeader = exchange.getRequest().getHeaders().getFirst("X-API-Key");
            }
            if(tenantHeader == null || tenantHeader.isBlank()){
                tenantHeader = TenantContext.DEFAULT_TENANT_ID;
            }
            tenantContext = TenantContext.builder()
                    .tenantId(tenantHeader)
                    .userId("anonymous")
                    .username("anonymous")
                    .roles(Collections.singletonList("ROLE_ANONYMOUS"))
                    .scopes(Collections.emptyList())
                    .tier("FREE")
                    .authenticated(false)
                    .build();
                
        }
            //context info exchange attributes for downstream filters (RateLimiter, Metrics))
        exchange.getAttributes().put(TENANT_CONTEXT_ATTR, tenantContext);

        //Inject cryptographically verified identity headers for downstream microservices

        ServerHttpRequest mutatedRequest = reqBuilder
                .header(HEADER_USER_ID, tenantContext.getUserId())
                .header(HEADER_TENANT_ID, tenantContext.getTenantId())
                .header(HEADER_USER_ROLES, String.join(",", tenantContext.getRoles()))
                .header(HEADER_GATEWAY_VERIFIED, "true")
                .header(HEADER_GATEWAY_TIMESTAMP, String.valueOf(Instant.now().toEpochMilli()))
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());


    }

    @SuppressWarnings("unchecked")
    private List<String> parseRequiredScopes(Object requiredScopesObj) {
        if (requiredScopesObj instanceof List<?> list) {
            return list.stream().map(Object::toString).collect(Collectors.toList());
        } else if (requiredScopesObj instanceof String s) {
            return Arrays.stream(s.split("[, ]+")).filter(str -> !str.isBlank()).collect(Collectors.toList());

        }
        return Collections.emptyList();
    }

    //Method for create and define a anonymousAuth
    private Authentication createAnonymousAuth() {
        return new org.springframework.security.authentication.AnonymousAuthenticationToken("openmesh-anonymous",
                "anonymous",
                org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
    }
    

    //method for extracting the tenantId from the jwt\
    private String extractTenantId(Jwt jwt) {
        String tenantId = jwt.getClaimAsString("tenant_id");
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = jwt.getClaimAsString("tenant");
        }
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = jwt.getClaimAsString("tenantId");
        }
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = TenantContext.DEFAULT_TENANT_ID;
        }
        return tenantId;
    }

    //method for extracting the scopes
    private List<String> extractScopes(Jwt jwt) {
        Object scopeObj = jwt.getClaims().get("scope");
        if (scopeObj instanceof String s) {
            return Arrays.stream(s.split(" "))
                    .filter(str -> !str.isBlank())
                    .collect(Collectors.toList());
        } else if (scopeObj instanceof List<?> list) {
            return list.stream().map(Object::toString).collect(Collectors.toList());
        }
        return Collections.emptyList();

    }

    //method for Forbidden Response
    private Mono<Void> writeForbiddenResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        FallbackResponse body = FallbackResponse.builder()
                .timestamp(Instant.now().toString())
                .status(HttpStatus.FORBIDDEN.value())
                .error("Forbidden")
                .message(message)
                .path(exchange.getRequest().getPath().value())
                .build();


        try {
            byte[] bytes = objectMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));

        } catch (JsonProcessingException e) {
            return response.setComplete();
        }
    }



    




}
