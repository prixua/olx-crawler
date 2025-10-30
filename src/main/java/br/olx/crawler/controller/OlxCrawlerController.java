package br.olx.crawler.controller;

import br.olx.crawler.controller.docs.OlxCrawlerApi;
import br.olx.crawler.dto.Produto;
import br.olx.crawler.service.CrawlerScheduledService;
import br.olx.crawler.service.LinkService;
import br.olx.crawler.service.OlxCrawlerService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/crawler")
@Tag(name = "OLX Crawler", description = "API para buscar produtos")
@RequiredArgsConstructor
public class OlxCrawlerController implements OlxCrawlerApi {

    private final OlxCrawlerService olxCrawlerService;
    private final LinkService linkService;
    private final CrawlerScheduledService crawlerScheduledService;

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

    @PostMapping(value = "/link", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<?> registerOlxLink(@RequestBody @Valid @NotEmpty String url) {
        return linkService.registerLink(url.trim())
                .map(link -> ResponseEntity.ok().body("URL cadastrada com sucesso"))
                .onErrorResume(ResponseStatusException.class, ex ->
                        Mono.just(ResponseEntity.status(ex.getStatusCode()).body(ex.getReason()))
                );
    }

    @GetMapping(value = "/send-mail", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<String>> sendTestEmail() {
        return crawlerScheduledService.run()
                .thenReturn(ResponseEntity.ok().body("E-mail de teste enviado com sucesso"))
                .onErrorResume(e ->
                        Mono.just(ResponseEntity.status(500).body("Erro ao enviar e-mail de teste: " + e.getMessage()))
                );
    }

    @Override
    @PostMapping(value = "/send-message", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<String>> sendMessage() {
        return crawlerScheduledService.runWhatsappCrawler()
                .thenReturn(ResponseEntity.ok("Top 10 anúncios enviados via WhatsApp com sucesso"))
                .onErrorResume(e ->
                        Mono.just(ResponseEntity.status(500).body("Erro ao enviar mensagens: " + e.getMessage()))
                );
    }
}
