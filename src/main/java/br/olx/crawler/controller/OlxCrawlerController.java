package br.olx.crawler.controller;

import br.olx.crawler.controller.docs.OlxCrawlerApi;
import br.olx.crawler.dto.Produto;
import br.olx.crawler.service.OlxCrawlerService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/crawler")
@Tag(name = "OLX Crawler", description = "API para buscar produtos")
@RequiredArgsConstructor
public class OlxCrawlerController implements OlxCrawlerApi {

    private final OlxCrawlerService olxCrawlerService;

    @Override
    @GetMapping(value = "/produtos", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<List<Produto>> lookForProducts(
            @RequestParam(value = "term", defaultValue = "tracer") String term,
            @RequestParam(value = "maxPages", defaultValue = "10") Integer maxPages) {

        if (maxPages <= 0 || maxPages > 50) {
            return Mono.error(new IllegalArgumentException("maxPages deve estar entre 1 e 50"));
        }

        if (term == null || term.trim().isEmpty()) {
            return Mono.error(new IllegalArgumentException("term não pode estar vazio"));
        }

        return olxCrawlerService.lookForProducts(term.trim(), maxPages);
    }

    @Override
    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<String> health() {
        return Mono.just("OLX Crawler Service is running!");
    }
}
