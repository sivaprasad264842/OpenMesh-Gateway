package main.java.io.openmesh.gateway.model;


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
