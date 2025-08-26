package br.olx.crawler;

import br.olx.crawler.dto.Produto;
import br.olx.crawler.util.SystemOutUtil;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OlxCrawler {

    private static final int MAX_PAGES = 20;
    private static final int REQUESTS_INTERVAL_IN_MS = 2000; // Aumentar intervalo

    private static final String BASE_URL = "https://www.olx.com.br/autos-e-pecas/motos/yamaha/mt-09/estado-rs";
    public static final String TERM = "tracer";

    public static void main(String[] args) {

        SystemOutUtil.configureConsoleEncoding();

        try (HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()) {

            int page = 1;
            boolean hasMore = true;
            Map<String, Produto> produtosUnicos = new LinkedHashMap<>();

            System.out.println("Iniciando busca... (máximo " + MAX_PAGES + " páginas)");

            while (hasMore && page <= MAX_PAGES) {
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
                    System.out.println("Acessando: " + url);
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                    System.out.println("Status: " + response.statusCode());

                    if (response.statusCode() == 200) {
                        String html = response.body();

                        // Debug: verificar se recebemos HTML válido
                        if (html.length() < 1000) {
                            System.out.println("Resposta muito pequena, pode ser bloqueio. Conteúdo: " + html.substring(0, Math.min(200, html.length())));
                        }

                        // Extrair produtos do HTML usando regex
                        List<Produto> produtosPagina = extrairProdutosDoHtml(html);

                        if (!produtosPagina.isEmpty()) {
                            System.out.println("Processando página " + page + " - " + produtosPagina.size() + " produtos encontrados");

                            for (Produto produto : produtosPagina) {
                                if (!produtosUnicos.containsKey(produto.getLink())) {
                                    produtosUnicos.put(produto.getLink(), produto);
                                    System.out.println("  ✓ Produto único adicionado: " + produto.getTitulo());
                                } else {
                                    System.out.println("  ⚠ Produto duplicado ignorado: " + produto.getTitulo());
                                }
                            }

                            page++;
                            Thread.sleep(REQUESTS_INTERVAL_IN_MS);
                        } else {
                            System.out.println("Nenhum produto encontrado na página " + page + ". Finalizando...");
                            hasMore = false;
                        }

                    } else if (response.statusCode() == 403) {
                        System.out.println("Erro 403 - Acesso negado. Tentando aguardar mais tempo...");
                        Thread.sleep(5000); // Esperar 5 segundos

                        // Tentar uma vez mais com delay maior
                        if (page == 1) {
                            System.out.println("Tentativa 2 para página " + page);
                            Thread.sleep(3000);
                            continue; // Tentar novamente sem incrementar página
                        } else {
                            hasMore = false;
                        }
                    } else {
                        System.out.println("Erro HTTP " + response.statusCode() + " na página " + page);
                        if (response.body() != null && !response.body().isEmpty()) {
                            System.out.println("Resposta: " + response.body().substring(0, Math.min(200, response.body().length())));
                        }
                        hasMore = false;
                    }

                } catch (IOException | InterruptedException e) {
                    System.err.println("Erro durante a requisição: " + e.getMessage());
                    hasMore = false;
                }
            }

            // Converter Map para List para ordenação
            List<Produto> todosProdutos = new ArrayList<>(produtosUnicos.values());

            // Ordenar por preço (mais barato primeiro)
            todosProdutos.sort(Comparator.comparingDouble(Produto::getPrecoNumerico));

            // Mostrar resultados
            System.out.println("\n=== RESULTADOS FINAIS (Primeiros 10 mais baratos) ===");
            System.out.println("Total de produtos únicos encontrados: " + todosProdutos.size());

            int count = 0;
            for (Produto produto : todosProdutos) {
                if (count >= 10) break;

                System.out.println("\n" + (count + 1) + ". " + produto.getTitulo());
                System.out.println("   Preco: " + produto.getPreco());
                System.out.println("   Link: " + produto.getLink());
                if (!produto.getImagem().isEmpty()) {
                    System.out.println("   Imagem: " + produto.getImagem());
                }
                count++;
            }

        } catch (Exception e) {
            System.err.println("Erro geral: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static List<Produto> extrairProdutosDoHtml(String html) {
        List<Produto> produtos = new ArrayList<>();

        // Padrões regex mais específicos para capturar dados dos produtos
        // Padrão para produtos com título e preço
        Pattern produtoComPrecoPattern = Pattern.compile(
            "<a[^>]*href=\"([^\"]*)\"[^>]*>.*?" +
            "<h2[^>]*>([^<]+)</h2>.*?" +
            "(?:R\\$\\s*([\\d.,]+)|([\\d.,]+)\\s*mil)",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        // Padrão alternativo para produtos sem preço visível
        Pattern produtoSemPrecoPattern = Pattern.compile(
            "<a[^>]*href=\"([^\"]*)\"[^>]*>.*?" +
            "<h2[^>]*>([^<]+)</h2>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        Pattern imagemPattern = Pattern.compile("<img[^>]*src=\"([^\"]*(?:jpg|jpeg|png|webp)[^\"]*)", Pattern.CASE_INSENSITIVE);

        // Primeiro tentar capturar produtos com preço
        Matcher matcherComPreco = produtoComPrecoPattern.matcher(html);
        while (matcherComPreco.find()) {
            String link = matcherComPreco.group(1);
            String titulo = matcherComPreco.group(2);
            String precoReais = matcherComPreco.group(3);
            String precoMil = matcherComPreco.group(4);

            // Validar link (ignorar links inválidos como #header)
            if (link == null || link.startsWith("#") || link.isEmpty()) {
                continue;
            }

            // Limpar título
            titulo = titulo.replaceAll("\\s+", " ").trim();

            // Filtrar apenas produtos que contenham "tracer"
            if (!titulo.toLowerCase().contains(TERM)) {
                continue;
            }

            // Processar preço
            String preco;
            if (precoReais != null && !precoReais.trim().isEmpty()) {
                preco = "R$ " + precoReais.trim();
            } else if (precoMil != null && !precoMil.trim().isEmpty()) {
                preco = precoMil.trim() + " mil";
            } else {
                preco = "Preço não informado";
            }

            // Garantir que o link seja absoluto
            if (link.startsWith("/")) {
                link = "https://www.olx.com.br" + link;
            }

            // Extrair imagem
            String imagem = extrairImagemProxima(html, matcherComPreco.start());

            produtos.add(new Produto(titulo, preco, link, imagem));
        }

        // Se não encontrou produtos com preço, tentar sem preço
        if (produtos.isEmpty()) {
            Matcher matcherSemPreco = produtoSemPrecoPattern.matcher(html);
            while (matcherSemPreco.find()) {
                String link = matcherSemPreco.group(1);
                String titulo = matcherSemPreco.group(2);

                // Validar link (ignorar links inválidos como #header)
                if (link == null || link.startsWith("#") || link.isEmpty() ||
                    titulo.toLowerCase().contains("acesse sua conta")) {
                    continue;
                }

                // Limpar título
                titulo = titulo.replaceAll("\\s+", " ").trim();

                // Filtrar apenas produtos que contenham "tracer"
                if (!titulo.toLowerCase().contains(TERM)) {
                    continue;
                }

                // Garantir que o link seja absoluto
                if (link.startsWith("/")) {
                    link = "https://www.olx.com.br" + link;
                }

                // Extrair imagem
                String imagem = extrairImagemProxima(html, matcherSemPreco.start());

                produtos.add(new Produto(titulo, "Preço não informado", link, imagem));
            }
        }

        return produtos;
    }

    private static String extrairImagemProxima(String html, int posicaoInicial) {
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
