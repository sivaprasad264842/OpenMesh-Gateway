package io.openmesh.gateway.controller;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.openmesh.gateway.config.RsaKeyProvider;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.security.interfaces.RSAPrivateKey;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class DevTokenController {

    private final RsaKeyProvider rsaKeyProvider;

    @Data
    public static class TokenRequest {
        private String tenantId = "tenant-alpha";
        private String userId = "user-101";
        private List<String> roles = List.of("ROLE_USER", "ROLE_ADMIN");
        private List<String> scopes = List.of("read", "write");
        private String tier = "PRO";
        private int expiresInMinutes = 60;
    }

    @Data
    @Builder
    public static class TokenResponse {
        private String token;
        private String tokenType;
        private long expiresInSeconds;
        private String tenantId;
        private String userId;
        private List<String> roles;
        private List<String> scopes;
        private String tier;
    }

    @PostMapping("/token")
    public Mono<ResponseEntity<TokenResponse>> generateToken(@RequestBody(required = false) TokenRequest request) {
        if (request == null) {
            request = new TokenRequest();
        }

        try {
            RSAPrivateKey privateKey = rsaKeyProvider.getRsaPrivateKey();
            JWSSigner signer = new RSASSASigner(privateKey);

            long nowMillis = System.currentTimeMillis();
            long expMillis = nowMillis + (request.getExpiresInMinutes() * 60 * 1000L);

            JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                    .subject(request.getUserId())
                    .issuer("openmesh-gateway")
                    .claim("tenant_id", request.getTenantId())
                    .claim("roles", request.getRoles() != null ? request.getRoles() : List.of("ROLE_USER"))
                    .claim("scope", String.join(" ", request.getScopes() != null ? request.getScopes() : List.of("read")))
                    .claim("tier", request.getTier() != null ? request.getTier() : "PRO")
                    .issueTime(new Date(nowMillis))
                    .expirationTime(new Date(expMillis))
                    .jwtID(UUID.randomUUID().toString())
                    .build();

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("openmesh-key-1").build(),
                    claimsSet
            );

            signedJWT.sign(signer);
            String serializedToken = signedJWT.serialize();

            TokenResponse response = TokenResponse.builder()
                    .token(serializedToken)
                    .tokenType("Bearer")
                    .expiresInSeconds(request.getExpiresInMinutes() * 60L)
                    .tenantId(request.getTenantId())
                    .userId(request.getUserId())
                    .roles(request.getRoles())
                    .scopes(request.getScopes())
                    .tier(request.getTier())
                    .build();

            return Mono.just(ResponseEntity.ok(response));
        } catch (Exception e) {
            log.error("Failed to generate RS256 token", e);
            return Mono.error(new RuntimeException("Unable to generate RS256 JWT", e));
        }
    }

    @GetMapping("/token")
    public Mono<ResponseEntity<TokenResponse>> generateDefaultToken(
            @RequestParam(defaultValue = "tenant-alpha") String tenantId,
            @RequestParam(defaultValue = "user-101") String userId,
            @RequestParam(defaultValue = "PRO") String tier) {
        TokenRequest req = new TokenRequest();
        req.setTenantId(tenantId);
        req.setUserId(userId);
        req.setTier(tier);
        return generateToken(req);
    }
}
