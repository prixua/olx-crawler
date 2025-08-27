package br.olx.crawler.controller;

import br.olx.crawler.dto.JwtResponse;
import br.olx.crawler.dto.LoginRequest;
import br.olx.crawler.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String ADMIN_AUTH_TYPE = "ADMIN";
    private final AuthService authService;

    @PostMapping(value = "/login", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<JwtResponse> login(
            @RequestHeader(value = "x-auth-type") String authType,
            @RequestBody LoginRequest loginRequest) {

        if (!ADMIN_AUTH_TYPE.equals(authType)) {
            return Mono.error(new RuntimeException("Header x-auth-type deve ser ADMIN"));
        }

        return authService.authenticate(loginRequest);
    }
}
