package main.java.io.openmesh.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@slf4j
@SpringBootApplication
public class OpenMeshGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenMeshGatewayApplication.class, args);
        log.info("--------------------------------------------------------------------");
        log.info("  OpenMesh-Gateway: Reactive Multi-Tenant Control Plane Started     ");
        log.info("  Java 21 LTS | Netty Reactive | Redis 7 Token Bucket | RS256 Auth  ");
        log.info("--------------------------------------------------------------------");

    }

}