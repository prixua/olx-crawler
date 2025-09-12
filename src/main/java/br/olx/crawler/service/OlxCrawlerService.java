package br.olx.crawler.service;

import br.olx.crawler.dto.Produto;
import br.olx.crawler.util.SystemOutUtil;
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

@Service
public class OlxCrawlerService {

    private static final int MAX_PAGES = 20;
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

    public Mono<List<Produto>> lookForProducts(String term, Integer maxPages) {
        return Mono.fromCallable(() -> {
                    SystemOutUtil.configureConsoleEncoding();
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
                                        int backoff = (int) Math.pow(2, retryCount) * 1000 + RANDOM.nextInt(2000);
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
                                    int backoff = (int) Math.pow(2, retryCount) * 1000 + RANDOM.nextInt(2000);
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
                .timeout(Duration.ofMinutes(3));
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
        SystemOutUtil.configureConsoleEncoding();
        try {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uri))
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                    .header("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Referer", "https://www.olx.com.br/")
                    .header("Cache-Control", "max-age=0")
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String html = response.body();
                return extrairProdutosDoHtml(html, ""); // Busca todos os produtos, sem filtro por termo
            } else {
                throw new RuntimeException("Erro ao buscar URI: status code " + response.statusCode());
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao fazer crawler na URI", e);
        }
    }
}
