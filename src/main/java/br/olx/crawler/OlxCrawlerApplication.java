package br.olx.crawler;

import br.olx.crawler.service.AuthService;
import io.github.cdimascio.dotenv.Dotenv;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class OlxCrawlerApplication {

    private final AuthService authService;

    public static void main(String[] args) {
        // Carregar variáveis de ambiente ANTES do Spring Boot inicializar
        loadEnvironmentVariables();

        SpringApplication.run(OlxCrawlerApplication.class, args);
    }

    private static void loadEnvironmentVariables() {
        try {
            // Primeiro tenta carregar do arquivo .env.production (desenvolvimento/local)
            Dotenv dotenv = Dotenv.configure()
                    .directory("./")
                    .filename(".env.production")
                    .ignoreIfMalformed()
                    .ignoreIfMissing()
                    .load();

            if (!dotenv.entries().isEmpty()) {
                dotenv.entries().forEach(entry -> {
                    System.setProperty(entry.getKey(), entry.getValue());
                });
                System.out.println("Total de " + dotenv.entries().size() + " variaveis carregadas do .env.production");
            } else {
                System.out.println("Arquivo .env.production nao encontrado. Usando variaveis de ambiente do sistema.");
            }

            // Verifica se as variáveis essenciais estão disponíveis (seja do arquivo ou do sistema)
//            validateRequiredEnvironmentVariables();

        } catch (Exception e) {
            System.err.println("Erro ao carregar variáveis de ambiente: " + e.getMessage());
            System.out.println("Tentando usar variáveis de ambiente do sistema...");
        }
    }

    private static void validateRequiredEnvironmentVariables() {
        String[] requiredVars = {"MONGO_USER", "MONGO_PASSWORD", "MONGO_HOST"};

        for (String var : requiredVars) {
            String value = System.getProperty(var, System.getenv(var));
            if (value == null || value.trim().isEmpty()) {
                System.err.println("AVISO: Variável de ambiente obrigatória não encontrada: " + var);
            } else {
                System.out.println("✓ " + var + " = " + (var.contains("PASSWORD") ? "***" : value));
            }
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeDefaultUser() {
        authService.initializeDefaultUser().subscribe();
    }
}
