package br.olx.crawler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Dados para autenticação")
public class LoginRequest {

    @Schema(description = "Nome de usuário", required = true)
    private String username;

    @Schema(description = "Senha do usuário", required = true)
    private String password;
}
