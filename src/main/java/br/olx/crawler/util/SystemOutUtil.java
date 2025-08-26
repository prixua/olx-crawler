package br.olx.crawler.util;

import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

public class SystemOutUtil {
    
    public static void configureConsoleEncoding() {
        try {
            // Configurar a saída padrão para UTF-8
            System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8.name()));
            System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8.name()));

            // Configurar propriedades do sistema para UTF-8
            System.setProperty("file.encoding", "UTF-8");
            System.setProperty("console.encoding", "UTF-8");

        } catch (UnsupportedEncodingException e) {
            System.err.println("Erro ao configurar codificação UTF-8: " + e.getMessage());
        }
    }
}
