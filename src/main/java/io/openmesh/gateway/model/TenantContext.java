package io.openmesh.gateway.model;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantContext {
    private String tenantId;
    private String userId;
    private String username;
    @Builder.Default
    private List<String> roles = Collections.emptyList();
    @Builder.Default
    private List<String> scopes = Collections.emptyList();
    private String tier;
    private String apiKey;
    private boolean authenticated;

    public static final String DEFAULT_TENANT_ID = "anonymous";
    
}
