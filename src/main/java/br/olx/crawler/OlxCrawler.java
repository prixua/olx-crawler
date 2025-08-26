package br.olx.crawler;

import br.olx.crawler.service.OlxCrawlerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

// Classe opcional para executar o crawler via linha de comando
@Component
public class OlxCrawler implements CommandLineRunner {

    @Autowired
    private OlxCrawlerService olxCrawlerService;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("Executando crawler via linha de comando...");
        olxCrawlerService.buscarProdutos()
                .subscribe(produtos -> {
                    System.out.println("=== RESULTADOS (Top 10) ===");
                    for (int i = 0; i < produtos.size(); i++) {
                        var produto = produtos.get(i);
                        System.out.println((i + 1) + ". " + produto.getTitulo());
                        System.out.println("   Preço: " + produto.getPreco());
                        System.out.println("   Link: " + produto.getLink());
                    }
                });
    }
}
