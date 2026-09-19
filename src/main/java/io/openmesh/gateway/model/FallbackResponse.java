package main.java.io.openmesh.gateway.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NILL)
public class FallbackResponse {
    @Builder.Default
    private String timestamp = Instant.now().toString();
    private int status;
    private String error;
    private String message;
    private String path;
    private String circuitBreaker;
    private Integer retryAfterSeconds;
    private String traceId;
}
