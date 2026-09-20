package main.java.io.openmesh.gateway.config;

@Configuration
public class MetricsConfig {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
            .commonTags("application", "openmesh-gateway", "env", "production")
            .meterFilter(MeterFilter.maximumAllowableTags("openmesh.gateway.requests.total", "tenant", 1000, MeterFilter.deny()));
    }
    
}
