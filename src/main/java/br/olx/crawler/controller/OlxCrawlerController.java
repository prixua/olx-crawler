package br.olx.crawler.controller;

import br.olx.crawler.dto.Produto;
import br.olx.crawler.service.OlxCrawlerService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/crawler")
@Tag(name = "OLX Crawler", description = "API para buscar motos no OLX do Rio Grande do Sul")
@RequiredArgsConstructor
public class OlxCrawlerController implements OlxCrawlerAPi {

    private final OlxCrawlerService olxCrawlerService;

    @Override
    @GetMapping(value = "/produtos", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<List<Produto>> buscarMotos(
            @RequestParam(value = "term", defaultValue = "tracer") String term,
            @RequestParam(value = "maxPages", defaultValue = "10") Integer maxPages) {

        if (maxPages <= 0 || maxPages > 50) {
            return Mono.error(new IllegalArgumentException("maxPages deve estar entre 1 e 50"));
        }

        if (term == null || term.trim().isEmpty()) {
            return Mono.error(new IllegalArgumentException("term não pode estar vazio"));
        }

        return olxCrawlerService.buscarProdutos(term.trim(), maxPages);
    }

    @Override
    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<String> health() {
        return Mono.just("OLX Crawler Service is running!");
    }
}
