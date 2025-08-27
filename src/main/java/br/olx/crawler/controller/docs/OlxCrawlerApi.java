package br.olx.crawler.controller.docs;

import br.olx.crawler.dto.Produto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Interface para o controlador de crawling do OLX
 * Define os endpoints da API REST
 */
public interface OlxCrawlerApi {

    @Operation(
        summary = "Buscar produtos no OLX",
        description = "Realiza crawling no site OLX para buscar produtos.",
        tags = {"Crawling", "Produtos"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Lista de produtos encontrados com sucesso. " +
                         "Produtos únicos (sem duplicatas) ordenados por preço decrescente.",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = Produto.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Parâmetros inválidos fornecidos",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(example = "{'error': 'maxPages deve estar entre 1 e 50'}")
            )
        ),
        @ApiResponse(
            responseCode = "408",
            description = "Timeout - operação excedeu 5 minutos",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(example = "{'error': 'Operação excedeu tempo limite'}")
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Erro interno do servidor durante o crawling",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(example = "{'error': 'Erro ao buscar produtos'}")
            )
        ),
        @ApiResponse(
            responseCode = "503",
            description = "Serviço indisponível - OLX pode estar bloqueando requisições",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(example = "{'error': 'Serviço temporariamente indisponível'}")
            )
        )
    })
    Mono<List<Produto>> lookForProducts(
        @Parameter(
            description = "Termo de busca para filtrar produtos. " +
                         "Busca case-insensitive no título dos produtos.",
            example = "tracer",
            required = false,
            schema = @Schema(minLength = 1, maxLength = 50)
        ) @RequestParam(value = "term", defaultValue = "tracer") String term,

        @Parameter(
            description = "Número máximo de páginas a serem percorridas. " +
                         "Cada página do OLX contém até 10 produtos. " +
                         "Valor deve estar entre 1 e 50 para evitar sobrecarga.",
            example = "5",
            required = false,
            schema = @Schema(minimum = "1", maximum = "50")
        ) @RequestParam(value = "maxPages", defaultValue = "20") Integer maxPages
    );

    @Operation(
        summary = "Health check do serviço",
        description = "Endpoint para verificar se o serviço está funcionando",
        tags = {"Monitoramento"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Serviço funcionando corretamente",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(example = "OLX Crawler Service is running!")
            )
        )
    })
    Mono<String> health();
}
