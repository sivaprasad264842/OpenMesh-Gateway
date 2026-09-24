package io.openmesh.gateway.service;

import io.openmesh.gateway.model.RateLimitResult;
import io.openmesh.gateway.model.RateLimitTier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.List;


@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {
    private final ReactiveRedisTemplate<String, String> reactiveStringRedisTemplate;
    private final RedisScript<List> tokenBucketRedisScript;


/**
     * Executes atomic Redis token bucket check.
     *
     * @param tenantId Tenant or API key identifier
     * @param replenishRate Tokens added per second
     * @param burstCapacity Maximum bucket capacity
     * @param requestedTokens Tokens consumed by this request (typically 1)
     * @return Mono<RateLimitResult>
     */

    public Mono<RateLimitResult> checkRateLimit(
        String tenantId,
        double replenishRate,
        double burstCapacity,
        int requestedTokens
    ){
        String key = "openmesh:ratelimit:" + tenantId;
        double now = System.currentTimeMillis() / 1000.0;

        List<String> keys = Collections.singletonList(key);
        String[] args = new String[]{
            String.valueOf(replenishRate),
            String.valueOf(burstCapacity),
            String.valueOf(now),
            String.valueOf(requestedTokens)
        };

        return reactiveStringRedisTemplate.execute(tokenBucketRedisScript, keys, List.of(args))
            .timeout(Duration.ofMillis(500))
            .next()
            .map(rawResult -> {
                List<?> resultList = ((List<?>) rawResult);
                long allowedNum = ((Number) resultList.get(0)).longValue();
                long remaining = ((Number) resultList.get(1)).longValue();
                long waitOrReset = ((Number) resultList.get(2)).longValue();

                return RateLimitResult.builder()
                        .allowed(allowedNum == 1L)
                        .remaining(remaining)
                        .waitOrResetSeconds(waitOrReset)
                        .tenantId(tenantId)
                        .replenishRate(replenishRate)
                        .burstCapacity(burstCapacity)
                        .build();
            })
            .onErrorResume(e -> {
                log.error("Redis rate limit evaluation failed for tenant'{}' : {}. Defaulting to fail-open.",
                            tenantId, e.getMessage());


                            return Mono.just(RateLimitResult.builder()
                                .allowed(true)
                                .remaining((long) burstCapacity)
                                .waitOrResetSeconds(0)
                                .tenantId(tenantId)
                                .replenishRate(replenishRate)
                                .burstCapacity(burstCapacity)
                                .build()
                            );
                        });
    }


    public Mono<RateLimitResult> checkRateLimitForTier(String tenantId, RateLimitTier tier) {
        return checkRateLimit(tenantId, tier.getReplenishRate(), tier.getBurstCapacity(), 1);
    }
    
    

    
    
}