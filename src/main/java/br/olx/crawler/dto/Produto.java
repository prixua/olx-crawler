package br.olx.crawler.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Produto encontrado no OLX")
public class Produto {

    @Schema(description = "Título do anúncio", example = "Yamaha MT-09 TRACER 900-GT")
    private final String titulo;

    @Schema(description = "Preço do produto", example = "R$ 49.500")
    private final String preco;

    @Schema(description = "Preço convertido para número (usado para ordenação)", example = "49500.0")
    private final double precoNumerico;

    @Schema(description = "Link para o anúncio no OLX", example = "https://rs.olx.com.br/...")
    private final String link;

    @Schema(description = "URL da imagem do produto", example = "https://img.olx.com.br/images/...")
    private final String imagem;

    public Produto(String titulo, String preco, String link, String imagem) {
        this.titulo = titulo;
        this.preco = preco;
        this.link = link;
        this.imagem = imagem;
        this.precoNumerico = extrairPreco(preco);
    }

    public String getTitulo() {
        return titulo;
    }

    public String getPreco() {
        return preco;
    }

    public double getPrecoNumerico() {
        return precoNumerico;
    }

    public String getLink() {
        return link;
    }

    public String getImagem() {
        return imagem;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Produto produto = (Produto) o;
        return link.equals(produto.link);
    }

    @Override
    public int hashCode() {
        return link.hashCode();
    }

    private double extrairPreco(String precoTexto) {
        if (precoTexto == null || precoTexto.isEmpty() || precoTexto.equals("Preço não informado")) {
            return Double.MAX_VALUE; // Preço inválido vai para o final
        }

        // Remove "R$", pontos e vírgulas para extrair o número
        String numeroLimpo = precoTexto.replaceAll("[R$\\s.]", "").replace(",", ".");

        try {
            return Double.parseDouble(numeroLimpo);
        } catch (NumberFormatException e) {
            return Double.MAX_VALUE; // Se não conseguir converter, vai para o final
        }
    }

    @Override
    public String toString() {
        return "Título: " + titulo + "\n" +
               "Preço: " + preco + "\n" +
               "Link: " + link + "\n" +
               "Imagem: " + imagem + "\n" +
               "------------------------------------------------";
    }
}
