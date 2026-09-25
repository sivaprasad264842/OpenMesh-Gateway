package io.openmesh.gateway.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j 
@Service
@RequiredArgsConstructor 
public class TenantMeteringService {
    private final MeterRegistry meterRegistry;
    private final Map<String, AtomicLong> tenantRequestCounts = new ConcurrentHashMap<>();

    public void recordRequest(String tenantId, String routeId, String method, int statusCode, Duration duration) {
        String safeTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : "anonymous";
        String safeRoute = (routeId != null && routeId.isBlank()) ? routeId : "unknown";
        String statusCategory = (statusCode / 100) + "xx";

        //Increment tenant internal in-memory meter
        tenantRequestCounts.computeIfAbsent(safeTenant, k -> new AtomicLong(0)).incrementAndGet();

        //Micrometer counter for Prometheus
        Counter.builder("openmesh.gateway.requests.total")
                .tag("tenant", safeTenant)
                .tag("route", safeRoute)
                .tag("method", method)
                .tag("status", String.valueOf(statusCode))
                .tag("status_category", statusCategory)
                .description("Total ingress gateway requests per tenant and route")
                .register(meterRegistry)
                .increment();

        Timer.builder("openmesh.gateway.request.duration")
                .tag("tenant", safeTenant)
                .tag("route", safeRoute)
                .tag("status_category", statusCategory)
                .publishPercentiles(0.5, 0.9, 0.99)
                .description("ingress gateway latency distribution")
                .register(meterRegistry)
                .record(duration);

    }

    public void recordRateLimitDrop(String tenantId, String routeId) {
        String safeTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : "anonymous";
        String safeRoute = (routeId != null && !routeId.isBlank()) ? routeId : "unknown";

        Counter.builder("openmesh.gateway.ratelimit.rejected")
                .tag("tenant", safeTenant)
                .tag("route", safeRoute)
                .description("Requested dropped due to tenant rate limit quota exhaustion")
                .register(meterRegistry)
                .increment();
    }

    public long getTenantTotalRequests(String tenantId) {
        AtomicLong counter = tenantRequestCounts.get(tenantId);
        return counter != null ? counter.get() : 0L;
    }


    
    

}
