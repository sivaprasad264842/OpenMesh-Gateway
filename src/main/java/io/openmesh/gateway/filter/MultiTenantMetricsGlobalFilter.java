package io.openmesh.gateway.filter;



import io.openmesh.gateway.model.TenantContext;
import io.openmesh.gateway.service.TenantMeteringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;


@Slf4j
@Component
@RequiredArgsConstructor 
public class MultiTenantMetricsGlobalFilter implements GlobalFilter, Ordered {
    

    public static final String TRACEPARENT_HEADER = "traceparent";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private final TenantMeteringService tenantMeteringService;

    @Override 
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;

    }

    @Override 
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.nanoTime();

        //ensure w3c Trace context headder (traceparent)
        ServerHttpRequest request = exchange.getRequest();
        String traceparent = request.getHeaders().getFirst(TRACEPARENT_HEADER);
        String traceId;

        if (traceparent == null || traceparent.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
            String spanId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            traceparent = String.format("00-%s-%s-01", traceId, spanId);

            ServerHttpRequest mutateRequest = request.mutate()
                    .header(TRACEPARENT_HEADER, traceparent)
                    .header(TRACE_ID_HEADER, traceId)
                    .build();
            exchange = exchange.mutate().request(mutateRequest).build();
        } else {
            String[] parts = traceparent.split("-");
            traceId = (parts.length > 1) ? parts[1] : UUID.randomUUID().toString().replace("-", "");
        }

        final String currentTraceId = traceId;
        final ServerWebExchange currentExchange = exchange;

        return chain.filter(currentExchange)
                .doFinally(signalType -> {
                    long durationManos = System.nanoTime() - startTime;
                    Duration duration = Duration.ofNanos(durationManos);


                    HttpStatusCode statusCode = currentExchange.getResponse().getStatusCode();
                    int status = (statusCode != null) ? statusCode.value() : 500;
                    
                    TenantContext tenantContext = currentExchange.getAttribute(ZeroTrustIdentityFilter.TENANT_CONTEXT_ATTR);
                    String tenantId = (tenantContext != null) ? tenantContext.getTenantId() : "anonymous";

                    Route route = currentExchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
                    String routeId = (route != null) ? route.getId() : "default";

                    String method = currentExchange.getRequest().getMethod().name();

                    tenantMeteringService.recordRequest(tenantId, routeId, method, status, duration);

                    log.debug("{Trace; {} Route: {} | Tenant: {} | Method: {} | Status: {} | Duration: {}ms}",
                            currentTraceId, routeId, tenantId, method, status, duration.toMillis());
                });

    }

    
}
