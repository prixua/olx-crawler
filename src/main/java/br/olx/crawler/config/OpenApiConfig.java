package br.olx.crawler.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("OLX Crawler API")
                        .description("API reativa para buscar motos Yamaha MT-09 Tracer no OLX do Rio Grande do Sul. " +
                                   "Utiliza Spring WebFlux (Reactor) para operações não-bloqueantes e retorna " +
                                   "os 10 produtos mais baratos ordenados por preço.")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("OLX Crawler Team")
                                .email("suporte@olxcrawler.com")
                                .url("https://github.com/olx-crawler"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Servidor de desenvolvimento"),
                        new Server()
                                .url("https://olx-crawler.herokuapp.com")
                                .description("Servidor de produção")
                ));
    }
}
