package main.java.io.openmesh.gateway.config;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@slf4j
@Configuration
public class RsaKeyProvider {


    @value("${openmesh.security.jwt.public-key-path:classpath:keys/public-key.pem}")
    private Resource publicKeyResource;

    @value("${openmesh.security.jwt.private-key-path:classpath:keys/private-key.pem}")
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
            if (publicKeyResource != null && publicKeyResource.exists() && privateKeyResource != null
                    && privateKeyResource.exists()) {
                try (InputStream pubIn = publicKeyResource.getInputStream();
                        InputStream privIn = privateKeyResource.getInputStream()) {

                    String pubPem = new String(pubIn.readAllBytes(), StandardCharsets.UTF_8);
                    String privPem = new String(privIn.readAllBytes(), StandardCharsets.UTF_8);

                    this.rsaPublicKey = parsePublicKey(pubPem);
                    this.rsaPrivateKey = parsePrivateKey(privPem);
                    log.info("RS256 keys successfully loaded from configured resources");
                    return;

                }
            }

        } catch (Exception e) {
            log.error(
                    "Error initializing RSA keys from resources : {}. Falling back to ephemeral dynamic 2048-bits RSA  keypairs.",
                    e.getMessage(), e);
        }

        //Ephemeral fallbback keypair
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair keyPair = keyGen.generateKeyPair();
            this.rsaPublicKey = (RSAPublicKey) keyPair.getPublic();
            this.rsaPrivateKey = (RSAPrivateKey) keyPair.getPrivate();
            log.info("Ephemeral RS256 keypair genarated successfully.");
        } catch (Exception e) {
            throw new IllegalStateException("Unable to genarate RS256 keyPair", e);
        }
    }

    
    private RSAPrivateKey parsePublickey(String pem) throws Exception {
        String cleanPem = pem
                .replace("----BEGIN PUBLIC KEY----", "")
                .replace("----END PUBLIC KEY----", "")
                .replaceAll("\\s", "");

        byte[] decoded = Base64.getDecoder().decode(cleanPem);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        keyFactory kf = keyFactory.getInstance("RSA");
        return (RSAPublicKey) kf.genaratePublic(spec);

    }
    
    private RSAPrivateKey parsePrivateKey(String pem) throws Exception {
        String cleanPem = pem
                .replace("----BEGIN PRIVATE KEY----", "")
                .replace("----END PRIVATE KEY----", "")
                .replaceAll("\\s", "");

        byte[] decoded = Base64.getDecoder().decode(cleanPem);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        keyFactory kf = keyFactory.getInstance("RSA");
        return (RSAPrivateKey) kf.genaratePrivate(spec);
    }




}
