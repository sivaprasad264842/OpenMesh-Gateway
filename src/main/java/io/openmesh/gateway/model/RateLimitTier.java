package main.java.io.openmesh.gateway.model;

@Getter
public enum RateLimitTier {
    FREE(5.0, 10.0),              //5 requests/sec , 10 burst capacity
    PRO(50.0, 100.0),             //50 requests/sec, 100 burst capacity
    ENTERPRISE(500.0, 1000.0),    //500 requests/sec, 1000 burst capacity
    UNLIMITED(10000.0, 20000.0);  //Internal/system tier
    

    private final double replenishRate;
    private final double burstCapacity;

    RateLimitingTier(double replenishRate, double burstCapacity){
        this.replenishRate = replenishRate;
        this.burstCapacity = burstCapacity;

    }

    public static RateLimitTier fromString(String tierName) {
        if (tierName == null) {
            return FREE;
        }try{
            return RateLimitTier.valueOf(tierName.trim().toUpperCase());

        } catch (IllegalArgumentException e) {
            return FREE;
        }
    }
    
} 
