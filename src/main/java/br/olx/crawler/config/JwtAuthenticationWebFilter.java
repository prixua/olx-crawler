package br.olx.crawler.config;

import br.olx.crawler.service.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationWebFilter implements WebFilter {

    private final JwtService jwtService;
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Pular autenticação para endpoints públicos
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        return extractToken(exchange)
                .filter(jwtService::validateToken)
                .filter(token -> !jwtService.isTokenExpired(token))
                .flatMap(token -> {
                    String username = jwtService.getUsernameFromToken(token);
                    Set<String> roles = jwtService.getRolesFromToken(token);

                    Set<SimpleGrantedAuthority> authorities = roles.stream()
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                            .collect(Collectors.toSet());

                    Authentication auth = new UsernamePasswordAuthenticationToken(
                            username, null, authorities);

                    return chain.filter(exchange)
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
                })
                .switchIfEmpty(chain.filter(exchange));
    }

    private Mono<String> extractToken(ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
                return authHeader.substring(BEARER_PREFIX.length());
            }
            return null;
        });
    }

    private boolean isPublicPath(String path) {
        return path.equals("/api/v1/auth/login") ||
               path.equals("/api/v1/crawler/health") ||
               path.startsWith("/swagger-ui") ||
               path.equals("/swagger-ui.html") ||
               path.startsWith("/api-docs") ||
               path.equals("/api-docs") ||
               path.startsWith("/v3/api-docs") ||
               path.startsWith("/actuator") ||
               path.startsWith("/webjars/") ||
               path.startsWith("/swagger-resources/") ||
               path.equals("/favicon.ico");
    }
}
