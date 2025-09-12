package br.olx.crawler.service;

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
        processAndSendSummaryEmail().subscribe();
    }

    public Mono<Void> run() {
        return processAndSendSummaryEmail();
    }

    private Mono<Void> processAndSendSummaryEmail() {

        log.info("Iniciando job de crawler para envio de e-mail...");
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
                    corpo.append("<html><body>");
                    for (Map.Entry<String, List<Produto>> entry : resultados.entrySet()) {
                        String link = entry.getKey();
                        List<Produto> produtos = entry.getValue();
                        corpo.append("<h3><a href='" + link + "' target='_blank'>" + link + "</a></h3>");
                        corpo.append("<table border='1' cellpadding='5' cellspacing='0' style='border-collapse:collapse;'>");
                        corpo.append("<tr><th>Nome do anúncio</th><th>Preço</th><th>Link</th></tr>");
                        for (Produto p : produtos) {
                            corpo.append("<tr>");
                            corpo.append("<td>" + p.getTitulo() + "</td>");
                            corpo.append("<td>" + p.getPreco() + "</td>");
                            corpo.append("<td><a href='" + p.getLink() + "' target='_blank'>Ver anúncio</a></td>");
                            corpo.append("</tr>");
                        }
                        corpo.append("</table><br/>");
                    }
                    corpo.append("</body></html>");
                    return emailService.sendSummaryEmail(corpo.toString());
                })
                .doOnError(e -> log.error("Erro no job de crawler", e));
    }
}
