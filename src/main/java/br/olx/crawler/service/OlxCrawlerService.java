package br.olx.crawler.service;

import br.olx.crawler.dto.Produto;
import br.olx.crawler.util.SystemOutUtil;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OlxCrawlerService {

    private static final int MAX_PAGES = 20;
    private static final int REQUESTS_INTERVAL_IN_MS = 2000;
    private static final String BASE_URL = "https://www.olx.com.br/autos-e-pecas/motos/yamaha/mt-09/estado-rs";

    public Mono<List<Produto>> buscarProdutos(String term, Integer maxPages) {
        return Mono.fromCallable(() -> {
            SystemOutUtil.configureConsoleEncoding();

            try (HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build()) {

                int page = 1;
                boolean hasMore = true;
                Map<String, Produto> produtosUnicos = new LinkedHashMap<>();

                while (hasMore && page <= maxPages) {
                    // OLX usa paginação de 10 produtos por página (fixo)
                    String url = BASE_URL + "?o=" + page;

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                            .header("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8")
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                            .header("Referer", "https://www.olx.com.br/")
                            .header("Cache-Control", "max-age=0")
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
                                Thread.sleep(REQUESTS_INTERVAL_IN_MS);
                            } else {
                                hasMore = false;
                            }
                        } else if (response.statusCode() == 403) {
                            Thread.sleep(5000);
                            if (page == 1) {
                                Thread.sleep(3000);
                                continue;
                            } else {
                                hasMore = false;
                            }
                        } else {
                            hasMore = false;
                        }
                    } catch (Exception e) {
                        hasMore = false;
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
    public Mono<List<Produto>> buscarProdutos() {
        return buscarProdutos("tracer", MAX_PAGES);
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
}
