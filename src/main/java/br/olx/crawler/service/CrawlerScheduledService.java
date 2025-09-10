package br.olx.crawler.service;

import br.olx.crawler.config.CrawlerScheduleProperties;
import br.olx.crawler.entity.Link;
import br.olx.crawler.dto.Produto;
import br.olx.crawler.repository.LinkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlerScheduledService {
    private final LinkRepository linkRepository;
    private final OlxCrawlerService olxCrawlerService;
    private final EmailService emailService;

    @Scheduled(cron = "#{@crawlerScheduleProperties.schedule}")
    public void runCrawlerJob() {
        linkRepository.findAll()
            .filter(Link::isEnabled)
            .collectList()
            .flatMap(links -> {
                Map<String, List<Produto>> resultados = new LinkedHashMap<>();
                for (Link link : links) {
                    List<Produto> produtos = olxCrawlerService.crawlerPorUri(link.getUri());
                    List<Produto> top5 = produtos.stream()
                        .sorted(Comparator.comparingDouble(Produto::getPrecoNumerico))
                        .limit(5)
                        .collect(Collectors.toList());
                    resultados.put(link.getUri(), top5);
                }
                StringBuilder corpo = new StringBuilder();
                for (Map.Entry<String, List<Produto>> entry : resultados.entrySet()) {
                    corpo.append(entry.getKey()).append(" (");
                    List<Produto> produtos = entry.getValue();
                    for (int i = 0; i < produtos.size(); i++) {
                        Produto p = produtos.get(i);
                        corpo.append(p.getTitulo());
                        if (i < produtos.size() - 1) corpo.append(", ");
                    }
                    corpo.append("); ");
                }
                return emailService.sendSummaryEmail(corpo.toString());
            })
            .doOnError(e -> log.error("Erro no job de crawler", e))
            .subscribe();
    }

    public Mono<Void> run(){
        return linkRepository.findAll()
                .filter(Link::isEnabled)
                .collectList()
                .flatMap(links -> {
                    Map<String, List<Produto>> resultados = new LinkedHashMap<>();
                    for (Link link : links) {
                        List<Produto> produtos = olxCrawlerService.crawlerPorUri(link.getUri());
                        List<Produto> top5 = produtos.stream()
                                .sorted(Comparator.comparingDouble(Produto::getPrecoNumerico))
                                .limit(5)
                                .collect(Collectors.toList());
                        resultados.put(link.getUri(), top5);
                    }
                    StringBuilder corpo = new StringBuilder();
                    for (Map.Entry<String, List<Produto>> entry : resultados.entrySet()) {
                        corpo.append(entry.getKey()).append(" (");
                        List<Produto> produtos = entry.getValue();
                        for (int i = 0; i < produtos.size(); i++) {
                            Produto p = produtos.get(i);
                            corpo.append(p.getTitulo());
                            if (i < produtos.size() - 1) corpo.append(", ");
                        }
                        corpo.append("); ");
                    }
                    return emailService.sendSummaryEmail(corpo.toString());
                })
                .doOnError(e -> log.error("Erro no job de crawler", e));
    }
}
