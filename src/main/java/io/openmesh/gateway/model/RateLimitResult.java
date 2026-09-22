package io.openmesh.gateway.model;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitResult {
    private boolean allowed;
    private long remaining;
    private long waitOrResetSeconds;
    private String tenantId;
    private double replenishRate;
    private double burstCapacity;
}
