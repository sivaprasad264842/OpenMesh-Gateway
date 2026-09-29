package io.openmesh.gateway.controller;

import com.nimbusds.jwt.SignedJWT;
import io.openmesh.gateway.config.RsaKeyProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class DevTokenControllerTest {

    private DevTokenController controller;
    private RsaKeyProvider rsaKeyProvider;

    @BeforeEach
    void setUp() {
        rsaKeyProvider = new RsaKeyProvider();
        ReflectionTestUtils.setField(rsaKeyProvider, "publicKeyResource", new ClassPathResource("certs/dev-public-key.pem"));
        ReflectionTestUtils.setField(rsaKeyProvider, "privateKeyResource", new ClassPathResource("certs/dev-private-key.pem"));
        controller = new DevTokenController(rsaKeyProvider);
    }

    @Test
    @DisplayName("Should generate valid RS256 signed JWT with custom claims")
    void shouldGenerateValidRs256Token() {
        DevTokenController.TokenRequest req = new DevTokenController.TokenRequest();
        req.setTenantId("tenant-omega");
        req.setUserId("dev-user");
        req.setTier("ENTERPRISE");

        StepVerifier.create(controller.generateToken(req))
                .assertNext(res -> {
                    assertThat(res.getBody()).isNotNull();
                    String token = res.getBody().getToken();
                    assertThat(token).isNotBlank();

                    try {
                        SignedJWT parsedJwt = SignedJWT.parse(token);
                        assertThat(parsedJwt.getHeader().getAlgorithm().getName()).isEqualTo("RS256");
                        assertThat(parsedJwt.getJWTClaimsSet().getSubject()).isEqualTo("dev-user");
                        assertThat(parsedJwt.getJWTClaimsSet().getStringClaim("tenant_id")).isEqualTo("tenant-omega");
                        assertThat(parsedJwt.getJWTClaimsSet().getStringClaim("tier")).isEqualTo("ENTERPRISE");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .verifyComplete();
    }
}
