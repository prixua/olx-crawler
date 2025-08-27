package br.olx.crawler.service;

import br.olx.crawler.dto.JwtResponse;
import br.olx.crawler.dto.LoginRequest;
import br.olx.crawler.entity.User;
import br.olx.crawler.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public Mono<JwtResponse> authenticate(LoginRequest loginRequest) {
        return userRepository.findByUsername(loginRequest.getUsername())
                .filter(user -> passwordEncoder.matches(loginRequest.getPassword(), user.getPassword()))
                .filter(User::isEnabled)
                .map(user -> {
                    String token = jwtService.generateToken(user.getUsername(), user.getRoles());
                    return new JwtResponse(token, user.getUsername(), user.getRoles());
                })
                .switchIfEmpty(Mono.error(new RuntimeException("Credenciais inválidas")));
    }

    public Mono<Void> initializeDefaultUser() {
        return userRepository.existsByUsername("elieser")
                .flatMap(exists -> {
                    if (!exists) {
                        User defaultUser = new User();
                        defaultUser.setUsername("elieser");
                        defaultUser.setPassword(passwordEncoder.encode("123"));
                        defaultUser.setRoles(Set.of("ADMIN"));
                        defaultUser.setEnabled(true);

                        return userRepository.save(defaultUser)
                                .doOnSuccess(user -> log.info("Usuário padrão criado: {}", user.getUsername()))
                                .then();
                    }
                    return Mono.empty();
                });
    }
}
