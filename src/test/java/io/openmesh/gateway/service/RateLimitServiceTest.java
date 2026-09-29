package io.openmesh.gateway.service;

import io.openmesh.gateway.model.RateLimitResult;
import io.openmesh.gateway.model.RateLimitTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock
    private ReactiveRedisTemplate<String, String> reactiveStringRedisTemplate;

    @Mock
    private RedisScript<List> tokenBucketRedisScript;

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitService(reactiveStringRedisTemplate, tokenBucketRedisScript);
    }

    @Test
    @DisplayName("Should return allowed = true when Lua script returns 1")
    void shouldReturnAllowedWhenLuaPermits() {
        // Lua returns [1, 15, 2]
        when(reactiveStringRedisTemplate.execute(eq(tokenBucketRedisScript), anyList(), anyList()))
                .thenReturn(Flux.just(List.of(1L, 15L, 2L)));

        StepVerifier.create(rateLimitService.checkRateLimit("tenant-1", 10.0, 20.0, 1))
                .assertNext(result -> {
                    assertThat(result.isAllowed()).isTrue();
                    assertThat(result.getRemainingTokens()).isEqualTo(15L);
                    assertThat(result.getWaitOrResetSeconds()).isEqualTo(2L);
                    assertThat(result.getTenantId()).isEqualTo("tenant-1");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return allowed = false when Lua script returns 0")
    void shouldReturnRejectedWhenLuaDenies() {
        // Lua returns [0, 0, 4]
        when(reactiveStringRedisTemplate.execute(eq(tokenBucketRedisScript), anyList(), anyList()))
                .thenReturn(Flux.just(List.of(0L, 0L, 4L)));

        StepVerifier.create(rateLimitService.checkRateLimit("tenant-1", 10.0, 20.0, 1))
                .assertNext(result -> {
                    assertThat(result.isAllowed()).isFalse();
                    assertThat(result.getRemainingTokens()).isEqualTo(0L);
                    assertThat(result.getWaitOrResetSeconds()).isEqualTo(4L);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should fail-open when Redis throws an exception")
    void shouldFailOpenOnRedisError() {
        when(reactiveStringRedisTemplate.execute(eq(tokenBucketRedisScript), anyList(), anyList()))
                .thenReturn(Flux.error(new RuntimeException("Redis connection refused")));

        StepVerifier.create(rateLimitService.checkRateLimit("tenant-error", 10.0, 20.0, 1))
                .assertNext(result -> {
                    assertThat(result.isAllowed()).isTrue();
                    assertThat(result.getTenantId()).isEqualTo("tenant-error");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should check rate limit using RateLimitTier")
    void shouldCheckRateLimitForTier() {
        when(reactiveStringRedisTemplate.execute(eq(tokenBucketRedisScript), anyList(), anyList()))
                .thenReturn(Flux.just(List.of(1L, 99L, 1L)));

        StepVerifier.create(rateLimitService.checkRateLimitForTier("tenant-pro", RateLimitTier.PRO))
                .assertNext(result -> {
                    assertThat(result.isAllowed()).isTrue();
                    assertThat(result.getBurstCapacity()).isEqualTo(100.0);
                    assertThat(result.getReplenishRate()).isEqualTo(50.0);
                })
                .verifyComplete();
    }
}
