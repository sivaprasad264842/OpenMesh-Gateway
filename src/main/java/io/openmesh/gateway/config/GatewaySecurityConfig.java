package main.java.io.openmesh.gateway.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

import java.security.interfaces.RSAPublicKey;
import java.util.*;
import java.util.stream.Collectors;


@Slf4j
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {
    private final RSAPublicKey rsaPublicKey;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(ServerHttpSecurity.CorsSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(
                                "/",
                                "/index.html",
                                "/favicon.ico",
                                "/static/**",
                                "/*.css",
                                "/*.js",
                                "/actuator/**",
                                "/auth/**",
                                "/fallback/**",
                                "/public/**",
                                "/admin/**")
                        .permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtDecoder(jwtDecoder())
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();

    }
    
    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        return NimbusReactiveJwtDecoder.withPublicKey(rsaPublicKey).build();
    }

    @Bean
    public converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter() {
        return new ReactiveJwtAuthenticationConverterAdapter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();


            // Extract scopes
            Object scopeClaim = jwt.getClaims().get("scope");
            if (scopeClaim instanceof String scopeStr) {
                Arrays.stream(scopesStr.split(" "))
                        .filter(s -> !s.isEmpty())
                        .map(s -> new SimpleGrantedAuthority("SCOPE_" + s))
                        .forEach(authorities::add);
            } else if (scopeClaim instanceof List<?> scopeList) {
                scopeList.stream()
                        .map(Object::toString)
                        .map(s -> new SimpleGrantedAuthority("SCOPE_" + s))
                        .forEach(authorities::add);
            }
            

            // Extract roles
            Object rolesClaim = jwt.getClaims().get("roles");
            if (rolesClaim instanceof List<?> rolesList) {
                rolesList.stream()
                        .map(Object::toString)
                        .map(r -> new SimpleGrantedAuthority(r.startsWith("ROLE_") ? r : "ROLE_" + r))
                        .forEach(authorities::add);
            }

            String principalName = jwt.getSubject() != null ? jwt.getSubject() : "anonymous";
            return new JwtAuthenticationToken(jwt, authorities, principalName);

        });
    }







    
}
