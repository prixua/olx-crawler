package br.olx.crawler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Resposta de autenticação com JWT token")
public class JwtResponse {

    @Schema(description = "Token JWT para autenticação", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String token;

    @Schema(description = "Tipo do token", example = "Bearer")
    private String type = "Bearer";

    @Schema(description = "Nome de usuário", example = "elieser")
    private String username;

    @Schema(description = "Roles do usuário", example = "[\"ADMIN\"]")
    private Set<String> roles;

    public JwtResponse(String token, String username, Set<String> roles) {
        this.token = token;
        this.username = username;
        this.roles = roles;
    }
}
