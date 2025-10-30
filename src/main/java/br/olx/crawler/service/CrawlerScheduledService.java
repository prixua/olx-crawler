package br.olx.crawler.service;

import br.olx.crawler.entity.Link;
import br.olx.crawler.dto.Produto;
import br.olx.crawler.repository.LinkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
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
    private final WhatsappMessageService whatsappMessageService;

    // Constantes para controle de crawler
    private static final int TOP_PRODUCTS_COUNT = 10;

    @Scheduled(cron = "#{@crawlerScheduleProperties.schedule}")
    public void runCrawlerJob() {
        processAndSendSummaryEmail().subscribe();
    }

    public Mono<Void> run() {
        return processAndSendSummaryEmail();
    }

    private Mono<Void> processAndSendSummaryEmail() {
        log.info("Iniciando job de crawler para envio de e-mail...");
        return linkRepository.findByEnabledTrue()
                .collectList()
                .flatMap(links -> {
                    Map<String, List<Produto>> resultados = new LinkedHashMap<>();
                    for (Link link : links) {
                        List<Produto> produtos = olxCrawlerService.crawlerPorUri(link.getUri());
                        List<Produto> top10 = produtos.stream()
                                .sorted(Comparator.comparingDouble(Produto::getPrecoNumerico))
                                .limit(TOP_PRODUCTS_COUNT)
                                .collect(Collectors.toList());
                        resultados.put(link.getUri(), top10);
                    }
                    StringBuilder corpo = new StringBuilder();
                    corpo.append("<html><body>");
                    for (Map.Entry<String, List<Produto>> entry : resultados.entrySet()) {
                        String link = entry.getKey();
                        List<Produto> produtos = entry.getValue();
                        corpo.append("<h3><a href='").append(link).append("' target='_blank'>").append(link).append("</a></h3>");
                        corpo.append("<table border='1' cellpadding='5' cellspacing='0' style='border-collapse:collapse;'>");
                        corpo.append("<tr><th>Nome do anúncio</th><th>Preço</th><th>Link</th></tr>");
                        for (Produto p : produtos) {
                            corpo.append("<tr>");
                            corpo.append("<td>").append(p.getTitulo()).append("</td>");
                            corpo.append("<td>").append(p.getPreco()).append("</td>");
                            corpo.append("<td><a href='").append(p.getLink()).append("' target='_blank'>Ver anúncio</a></td>");
                            corpo.append("</tr>");
                        }
                        corpo.append("</table><br/>");
                    }
                    corpo.append("</body></html>");
                    return emailService.sendSummaryEmail(corpo.toString());
                })
                .doOnError(e -> log.error("Erro no job de crawler", e));
    }

    public Mono<Void> runWhatsappCrawler() {
        log.info("Iniciando job de crawler para envio via WhatsApp...");
        return linkRepository.findByEnabledTrue()
                .collectList()
                .flatMap(links -> {
                    if (links.isEmpty()) {
                        log.warn("Nenhum link ativo encontrado para envio via WhatsApp");
                        return Mono.error(new RuntimeException("Nenhum link ativo encontrado para envio via WhatsApp"));
                    }

                    log.info("Processando {} links para envio via WhatsApp", links.size());

                    return Flux.fromIterable(links)
                            .flatMap(link -> {
                                log.info("Processando link: {}", link.getUri());

                                try {
                                    List<Produto> produtos = olxCrawlerService.crawlerPorUri(link.getUri());
                                        List<Produto> top10 = produtos.stream()
                                                .sorted(Comparator.comparingDouble(Produto::getPrecoNumerico))
                                                .limit(TOP_PRODUCTS_COUNT)
                                                .toList();

                                        if (top10.isEmpty()) {
                                        log.warn("Nenhum produto encontrado para o link: {}", link.getUri());
                                        return Mono.just(false); // Continua processamento mesmo sem produtos
                                    }

                                    StringBuilder mensagem = new StringBuilder();
                                    mensagem.append("*Top 10 Anúncios*\n");

                                    for (Produto p : top10) {
                                        mensagem.append("*").append(p.getTitulo()).append("*\n");
                                        mensagem.append("💰 Preço: ").append(p.getPreco()).append("\n");
                                        mensagem.append("🔗 ").append(p.getLink()).append("\n\n");
                                    }

                                    return whatsappMessageService.sendMessage("51999353392", mensagem.toString())
                                            .doOnSuccess(success -> log.info("Mensagem enviada com sucesso para link: {}", link.getUri()))
                                            .onErrorResume(error -> {
                                                log.error("Erro ao enviar mensagem para link {}: {}", link.getUri(), error.getMessage());
                                                return Mono.error(new RuntimeException("Falha ao enviar mensagem para link " + link.getUri() + ": " + error.getMessage(), error));
                                            });

                                } catch (Exception e) {
                                    log.error("Erro ao processar crawler para link {}: {}", link.getUri(), e.getMessage());
                                    return Mono.error(new RuntimeException("Erro ao processar link " + link.getUri() + ": " + e.getMessage(), e));
                                }
                            })
                            .then()
                            .doOnSuccess(v -> log.info("Todos os links processados com sucesso"));
                })
                .doOnError(e -> log.error("Erro geral no job de crawler para WhatsApp: {}", e.getMessage()));
    }
}
