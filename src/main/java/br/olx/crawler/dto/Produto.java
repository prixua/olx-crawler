package br.olx.crawler.dto;

public class Produto {
    private final String titulo;
    private final String preco;
    private final double precoNumerico;
    private final String link;
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
