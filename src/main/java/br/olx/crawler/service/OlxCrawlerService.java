package br.olx.crawler.service;

import br.olx.crawler.dto.Produto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OlxCrawlerService {

    private static final int MAX_PAGES = 20;
    private static final int MAX_PAGES_TO_CRAWL = 7;
    private static final String BASE_URL = "https://www.olx.com.br/autos-e-pecas/motos/yamaha/mt-09/estado-rs";

    private static final List<String> USER_AGENTS = List.of(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.1 Safari/605.1.15",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:120.0) Gecko/20100101 Firefox/120.0"
    );
    private static final List<String> ACCEPT_LANGUAGES = List.of(
            "pt-BR,pt;q=0.9,en;q=0.8",
            "en-US,en;q=0.9,pt;q=0.8",
            "es-ES,es;q=0.9,en;q=0.8"
    );
    private static final int MIN_REQUESTS_INTERVAL_MS = 30000; // 30s
    private static final int MAX_REQUESTS_INTERVAL_MS = 60000; // 60s
    private static final int MAX_RETRIES = 3;
    private static final Random RANDOM = new Random();
    public static final int DELAY = 2000;

    public Mono<List<Produto>> lookForProducts(String term, Integer maxPages) {
        return Mono.fromCallable(() -> {
                    try {
                        HttpClient client = HttpClient.newBuilder()
                                .followRedirects(HttpClient.Redirect.NORMAL)
                                .build();
                        int page = 1;
                        boolean hasMore = true;
                        Map<String, Produto> produtosUnicos = new LinkedHashMap<>();

                        while (hasMore && page <= maxPages) {
                            String url = BASE_URL + "?o=" + page;
                            int retryCount = 0;
                            boolean success = false;
                            while (!success && retryCount < MAX_RETRIES) {
                                String userAgent = USER_AGENTS.get(RANDOM.nextInt(USER_AGENTS.size()));
                                String acceptLanguage = ACCEPT_LANGUAGES.get(RANDOM.nextInt(ACCEPT_LANGUAGES.size()));
                                HttpRequest request = HttpRequest.newBuilder()
                                        .uri(URI.create(url))
                                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                                        .header("Accept-Language", acceptLanguage)
                                        .header("User-Agent", userAgent)
                                        .header("Referer", "https://www.olx.com.br/")
                                        .header("Cache-Control", "max-age=0")
                                        .header("Connection", "keep-alive")
                                        .build();
                                try {
                                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                                    if (response.statusCode() == 200) {
                                        String html = response.body();
                                        List<Produto> produtosPagina = extrairProdutosDoHtml(html, term);
                                        if (!produtosPagina.isEmpty()) {
                                            for (Produto produto : produtosPagina) {
                                                if (!produtosUnicos.containsKey(produto.getLink())) {
                                                    produtosUnicos.put(produto.getLink(), produto);
                                                }
                                            }
                                            page++;
                                            int delay = MIN_REQUESTS_INTERVAL_MS + RANDOM.nextInt(MAX_REQUESTS_INTERVAL_MS - MIN_REQUESTS_INTERVAL_MS + 1);
                                            TimeUnit.MILLISECONDS.sleep(delay);
                                        } else {
                                            hasMore = false;
                                        }
                                        success = true;
                                    } else if (response.statusCode() == 403) {
                                        retryCount++;
                                        int backoff = (int) Math.pow(2, retryCount) * 1000 + RANDOM.nextInt(DELAY);
                                        TimeUnit.MILLISECONDS.sleep(backoff);
                                        if (retryCount >= MAX_RETRIES) {
                                            hasMore = false;
                                        }
                                    } else {
                                        hasMore = false;
                                        success = true;
                                    }
                                } catch (Exception e) {
                                    retryCount++;
                                    int backoff = (int) Math.pow(2, retryCount) * 1000 + RANDOM.nextInt(DELAY);
                                    TimeUnit.MILLISECONDS.sleep(backoff);
                                    if (retryCount >= MAX_RETRIES) {
                                        hasMore = false;
                                    }
                                }
                            }
                        }
                        List<Produto> todosProdutos = new ArrayList<>(produtosUnicos.values());
                        todosProdutos.sort(Comparator.comparingDouble(Produto::getPrecoNumerico));
                        return todosProdutos.stream().limit(10).toList();
                    } catch (Exception e) {
                        throw new RuntimeException("Erro ao buscar produtos", e);
                    }
                })
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .timeout(Duration.ofMinutes(5));
    }

    // Método de compatibilidade
    public Mono<List<Produto>> lookForProducts() {
        return lookForProducts("tracer", MAX_PAGES);
    }

    private List<Produto> extrairProdutosDoHtml(String html, String term) {
        List<Produto> produtos = new ArrayList<>();

        Pattern produtoComPrecoPattern = Pattern.compile(
                "<a[^>]*href=\"([^\"]*)\"[^>]*>.*?" +
                        "<h2[^>]*>([^<]+)</h2>.*?" +
                        "(?:R\\$\\s*([\\d.,]+)|([\\d.,]+)\\s*mil)",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        Pattern produtoSemPrecoPattern = Pattern.compile(
                "<a[^>]*href=\"([^\"]*)\"[^>]*>.*?" +
                        "<h2[^>]*>([^<]+)</h2>",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        Matcher matcherComPreco = produtoComPrecoPattern.matcher(html);
        while (matcherComPreco.find()) {
            String link = matcherComPreco.group(1);
            String titulo = matcherComPreco.group(2);
            String precoReais = matcherComPreco.group(3);
            String precoMil = matcherComPreco.group(4);

            if (link == null || link.startsWith("#") || link.isEmpty()) {
                continue;
            }

            titulo = titulo.replaceAll("\\s+", " ").trim();

            if (!titulo.toLowerCase().contains(term.toLowerCase())) {
                continue;
            }

            String preco;
            if (precoReais != null && !precoReais.trim().isEmpty()) {
                preco = "R$ " + precoReais.trim();
            } else if (precoMil != null && !precoMil.trim().isEmpty()) {
                preco = precoMil.trim() + " mil";
            } else {
                preco = "Preço não informado";
            }

            if (link.startsWith("/")) {
                link = "https://www.olx.com.br" + link;
            }

            String imagem = extrairImagemProxima(html, matcherComPreco.start());
            produtos.add(new Produto(titulo, preco, link, imagem));
        }

        if (produtos.isEmpty()) {
            Matcher matcherSemPreco = produtoSemPrecoPattern.matcher(html);
            while (matcherSemPreco.find()) {
                String link = matcherSemPreco.group(1);
                String titulo = matcherSemPreco.group(2);

                if (link == null || link.startsWith("#") || link.isEmpty() ||
                        titulo.toLowerCase().contains("acesse sua conta")) {
                    continue;
                }

                titulo = titulo.replaceAll("\\s+", " ").trim();

                if (!titulo.toLowerCase().contains(term.toLowerCase())) {
                    continue;
                }

                if (link.startsWith("/")) {
                    link = "https://www.olx.com.br" + link;
                }

                String imagem = extrairImagemProxima(html, matcherSemPreco.start());
                produtos.add(new Produto(titulo, "Preço não informado", link, imagem));
            }
        }

        return produtos;
    }

    private String extrairImagemProxima(String html, int posicaoInicial) {
        Pattern imagemPattern = Pattern.compile("<img[^>]*src=\"([^\"]*(?:jpg|jpeg|png|webp)[^\"]*)", Pattern.CASE_INSENSITIVE);
        Matcher imagemMatcher = imagemPattern.matcher(html);

        while (imagemMatcher.find()) {
            if (imagemMatcher.start() > posicaoInicial) {
                return imagemMatcher.group(1);
            }
        }
        return "";
    }

    public List<Produto> crawlerPorUri(String uri) {
        return crawlerMultiplePages(uri, MAX_PAGES_TO_CRAWL);
    }

    public List<Produto> crawlerMultiplePages(String baseUri, int maxPages) {
        List<Produto> todosProdutos = new ArrayList<>();

        try {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            for (int page = 1; page <= maxPages; page++) {
                try {
                    String uriWithPage = buildUriWithPage(baseUri, page);
                    log.info("Fazendo crawler da página {} de {}: {}", page, maxPages, uriWithPage);

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(uriWithPage))
                            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                            .header("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8")
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                            .header("Referer", "https://www.olx.com.br/")
                            .header("Cache-Control", "max-age=0")
                            .build();

                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() == 200) {
                        String html = response.body();
                        List<Produto> produtosPagina = extrairProdutosDoHtml(html, "");
                        todosProdutos.addAll(produtosPagina);
                        log.info("Página {}: {} produtos encontrados", page, produtosPagina.size());

                        // Delay entre páginas para evitar bloqueio
                        if (page < maxPages) {
                            Thread.sleep(DELAY); // 2 segundos entre páginas
                        }
                    } else {
                        log.warn("Erro ao acessar página {}: status code {}", page, response.statusCode());
                        // Continua para próxima página em caso de erro
                    }

                } catch (Exception e) {
                    log.error("Erro ao processar página {}: {}", page, e.getMessage());
                    // Continua para próxima página em caso de erro
                }
            }

            log.info("Total de produtos coletados de {} páginas: {}", maxPages, todosProdutos.size());

            // Remove duplicatas baseado no link do produto
            List<Produto> produtosSemDuplicatas = todosProdutos.stream()
                    .collect(Collectors.toMap(
                            Produto::getLink,
                            produto -> produto,
                            (existing, replacement) -> existing,
                            LinkedHashMap::new))
                    .values()
                    .stream()
                    .toList();

            log.info("Produtos únicos após remoção de duplicatas: {}", produtosSemDuplicatas.size());
            return produtosSemDuplicatas;

        } catch (Exception e) {
            log.error("Erro geral no crawler de múltiplas páginas", e);
            throw new RuntimeException("Erro ao fazer crawler de múltiplas páginas", e);
        }
    }

    private String buildUriWithPage(String baseUri, int page) {
        if (page == 1) {
            return baseUri; // Primeira página não precisa de parâmetro
        }

        String separator = baseUri.contains("?") ? "&" : "?";
        return baseUri + separator + "o=" + page;
    }
}
