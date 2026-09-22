package io.openmesh.gateway.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import jakarta.annotation.PostConstruct;

@Slf4j
@Configuration
public class RsaKeyProvider {

    @Value("${openmesh.security.jwt.public-key-path:classpath:certs/dev-public-key.pem}")
    private Resource publicKeyResource;

    @Value("${openmesh.security.jwt.private-key-path:classpath:certs/dev-private-key.pem}")
    private Resource privateKeyResource;

    private RSAPublicKey rsaPublicKey;
    private RSAPrivateKey rsaPrivateKey;

    @Bean
    public RSAPublicKey rsaPublicKey() {
        return getRsaPublicKey();
    }

    @Bean
    public RSAPrivateKey rsaPrivateKey() {
        return getRsaPrivateKey();
    }

    public RSAPublicKey getRsaPublicKey() {
        if (this.rsaPublicKey == null) {
            initKeys();
        }
        return this.rsaPublicKey;
    }

    public RSAPrivateKey getRsaPrivateKey() {
        if (this.rsaPrivateKey == null) {
            initKeys();
        }
        return this.rsaPrivateKey;
    }

    @PostConstruct
    public synchronized void initKeys() {
        try {
            if (publicKeyResource != null && publicKeyResource.exists() &&
                privateKeyResource != null && privateKeyResource.exists()) {
                
                try (InputStream pubIn = publicKeyResource.getInputStream();
                     InputStream privIn = privateKeyResource.getInputStream()) {
                    
                    String pubPem = new String(pubIn.readAllBytes(), StandardCharsets.UTF_8);
                    String privPem = new String(privIn.readAllBytes(), StandardCharsets.UTF_8);
                    
                    this.rsaPublicKey = parsePublicKey(pubPem);
                    this.rsaPrivateKey = parsePrivateKey(privPem);
                    log.info("RS256 keys successfully loaded from configured resources.");
                    return;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load RSA keys from resources ({}). Falling back to ephemeral dynamic 2048-bit RSA keypair.", e.getMessage());
        }

        // Ephemeral fallback keypair
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair keyPair = keyGen.generateKeyPair();
            this.rsaPublicKey = (RSAPublicKey) keyPair.getPublic();
            this.rsaPrivateKey = (RSAPrivateKey) keyPair.getPrivate();
            log.info("Ephemeral RS256 KeyPair generated successfully.");
        } catch (Exception e) {
            throw new IllegalStateException("Unable to generate RS256 KeyPair", e);
        }
    }

    private RSAPublicKey parsePublicKey(String pem) throws Exception {
        String cleanPem = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(cleanPem);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return (RSAPublicKey) kf.generatePublic(spec);
    }

    private RSAPrivateKey parsePrivateKey(String pem) throws Exception {
        String cleanPem = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(cleanPem);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return (RSAPrivateKey) kf.generatePrivate(spec);
    }
}
