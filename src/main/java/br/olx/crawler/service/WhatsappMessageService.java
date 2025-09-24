package br.olx.crawler.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class WhatsappMessageService {
    private final WebClient webClient;

    @Value("${whatsapp.api.url:http://localhost:3500}")
    private String whatsappApiUrl;

    public WhatsappMessageService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public Mono<Boolean> sendMessage(String number, String message) {
        String url = whatsappApiUrl + "/send-message";
        log.info("Enviando mensagem WhatsApp para número: {} via URL: {}", number, url);

        // Cria um mapa para o corpo da requisição - Spring converte automaticamente para JSON
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("number", number);
        requestBody.put("message", message);

        return webClient.post()
                .uri(url)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody) // Usa Map em vez de String concatenada
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(response -> log.info("Resposta da API WhatsApp: {}", response))
                .map(response -> {
                    boolean success = response.contains("\"success\":true");
                    if (!success) {
                        log.warn("API WhatsApp retornou sucesso=false. Resposta: {}", response);
                        throw new RuntimeException("API WhatsApp retornou sucesso=false: " + response);
                    }
                    return true;
                })
                .doOnError(error -> {
                    if (error instanceof WebClientResponseException) {
                        WebClientResponseException webError = (WebClientResponseException) error;
                        log.error("Erro na API WhatsApp - Status: {}, Body: {}",
                                webError.getStatusCode(), webError.getResponseBodyAsString());
                    } else {
                        log.error("Erro ao enviar mensagem WhatsApp", error);
                    }
                })
                .onErrorResume(WebClientResponseException.class, ex -> {
                    String errorMessage = String.format("Erro %d ao chamar API WhatsApp: %s",
                            ex.getStatusCode().value(), ex.getResponseBodyAsString());
                    return Mono.error(new RuntimeException(errorMessage, ex));
                })
                .onErrorResume(Exception.class, ex -> {
                    String errorMessage = "Falha na comunicação com API WhatsApp: " + ex.getMessage();
                    return Mono.error(new RuntimeException(errorMessage, ex));
                });
    }
}
