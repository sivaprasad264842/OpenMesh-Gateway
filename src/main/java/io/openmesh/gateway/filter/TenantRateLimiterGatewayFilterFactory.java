package io.openmesh.gateway.filter;



import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openmesh.gateway.model.FallbackResponse;
import io.openmesh.gateway.model.RateLimitResult;
import io.openmesh.gateway.model.RateLimitTier;
import io.openmesh.gateway.model.TenantContext;
import io.openmesh.gateway.service.RateLimitService;
import io.openmesh.gateway.service.TenantMeteringService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component 
public class TenantRateLimiterGatewayFilterFactory 
        extends AbstractGatewayFilterFactory<TenantRateLimiterGatewayFilterFactory.Config>{
    public static final String HEADER_RATE_LIMIT_REMAINING = "X-RateLimit-Remaining";
    public static final String HEADER_RATE_LIMIT_BURST = "X-RateLimit-Burst-Capacity";
    public static final String HEADER_RATE_LIMIT_REPLENISH = "X-RateLimit-Replenish-Rate";
    public static final String HEADER_RATE_LIMIT_RESET = "X-RateLimit-Reset";

    private final RateLimitService rateLimitService;
    private final TenantMeteringService tenantMeteringService;
    private final ObjectMapper objectMapper;

    public TenantRateLimiterGatewayFilterFactory (RateLimitService rateLimitService,
        TenantMeteringService tenantMeteringService,
            ObjectMapper objectMapper) {
        super(Config.class);
        this.tenantMeteringService = tenantMeteringService;
        this.objectMapper = objectMapper;
        this.rateLimitService = rateLimitService;

    }
    
    @Override 
    public List<String> shortcutFieldOrder() {
        return Arrays.asList("replenishRate", "burstCapacity", "requestedToken", "tier");
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String tenantId = resolveTenantId(exchange);

            double replenishRate = config.getReplenishRate();
            double burstCapacity = config.getBurstCapacity();
            int requestedTokens = config.getRequestedTokens() > 0 ? config.getRequestedTokens() : 1;


            TenantContext tenantContext = exchange.getAttribute(ZeroTrustIdentityFilter.TENANT_CONTEXT_ATTR);
            if(config.getTier() != null && !config.getTier().isBlank()){
                RateLimitTier tier = RateLimitTier.fromString(config.getTier());
                replenishRate = tier.getReplenishRate();
                burstCapacity = tier.getBurstCapacity();
            }else if(tenantContext != null && tenantContext.getTier() != null){
                RateLimitTier tier = RateLimitTier.fromString(tenantContext.getTier());
                replenishRate = tier.getReplenishRate();
                burstCapacity = tier.getBurstCapacity();
            }
            return rateLimitService.checkRateLimit(tenantId, replenishRate, burstCapacity, requestedTokens)
                    .flatMap(result -> {
                        ServerHttpResponse response = exchange.getResponse();
                        HttpHeaders headers = response.getHeaders();


                        headers.add(HEADER_RATE_LIMIT_BURST, String.valueOf((int) result.getBurstCapacity()));
                        headers.add(HEADER_RATE_LIMIT_REPLENISH, String.valueOf((int) result.getReplenishRate()));


                        if(result.isAllowed()){
                            headers.add(HEADER_RATE_LIMIT_REMAINING, String.valueOf(result.getRemainingTokens()));
                            headers.add(HEADER_RATE_LIMIT_RESET, String.valueOf(result.getWaitOrResetSeconds()));
                            return chain.filter(exchange);
                        }else{
                            String routeId = "unknown";
                            Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
                            if(route != null){
                                routeId = route.getId();
                            }
                            tenantMeteringService.recordRateLimitDrop(tenantId, routeId);
                            
                            headers.add(HEADER_RATE_LIMIT_REMAINING, "0");
                            headers.add(HEADER_RATE_LIMIT_RESET, String.valueOf(result.getWaitOrResetSeconds()));
                            headers.add(HttpHeaders.RETRY_AFTER, String.valueOf(result.getWaitOrResetSeconds()));


                            return write429TooManyRequests(exchange, result);



                        }
                    });
        };
    }

    



    private Mono<Void> write429TooManyRequests(ServerWebExchange exchange, RateLimitResult result) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        FallbackResponse errorPayLoad = FallbackResponse.builder()
                .timestamp(Instant.now().toString())
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .error("Too Many Requests")
                .message(String.format("Rate limit quota exceeded for tenant '%s'. Burst capacity: %d, Replenish rate: %d/s", 
                        result.getTenantId(), (int) result.getBurstCapacity(), (int) result.getReplenishRate()))
                .path(exchange.getRequest().getPath().value())
                .retryAfterSeconds((int) result.getWaitOrResetSeconds())
                .build();

        

                try{
                    byte[] bytes = objectMapper.writeValueAsString(errorPayLoad).getBytes(StandardCharsets.UTF_8);
                    DataBuffer buffer = response.bufferFactory().wrap(bytes);
                    return response.writeWith(Mono.just(buffer));
                } catch (JsonProcessingException e) {
                    log.error("Failed to serialize 429 response", e);
                    return response.setComplete();
                }



    }
    




    private String resolveTenantId(ServerWebExchange exchange) {
        TenantContext tenantContext = exchange.getAttribute(ZeroTrustIdentityFilter.TENANT_CONTEXT_ATTR);
        if (tenantContext != null && tenantContext.getTenantId() != null
                && !TenantContext.DEFAULT_TENANT_ID.equals(tenantContext.getTenantId())) {
            return tenantContext.getTenantId();
        }

        String tenantHeader = exchange.getRequest().getHeaders().getFirst("Tenant-ID");
        if (tenantHeader != null && !tenantHeader.isBlank()) {
            return tenantHeader.trim();
        }

        String apiKeyHeader = exchange.getRequest().getHeaders().getFirst("X-API-Key");
        if (apiKeyHeader != null && !apiKeyHeader.isBlank()) {
            return "apikey-" + apiKeyHeader.trim();
        }

        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return "ip-" + remoteAddress.getAddress().getHostAddress();
        }


        return TenantContext.DEFAULT_TENANT_ID;


    }





    @Data
    public static class Config {

        private double replenishRate = 10.0;
        private double burstCapacity = 20.0;
        private int requestedTokens = 1;
        private String tier;
    
        
    }
    
}
