package br.olx.crawler.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {
    private final JavaMailSender mailSender;

    @Value("${crawler.email.to}")
    private String to;

    @Value("${crawler.email.from}")
    private String from;

    public Mono<Void> sendSummaryEmail(String corpo) {
        return Mono.fromRunnable(() -> {
            String subject = "Resumo dos produtos dos links monitorados";
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setFrom(from);
            message.setSubject(subject);
            message.setText(corpo);
            if (mailSender instanceof org.springframework.mail.javamail.JavaMailSenderImpl impl) {
                log.info("[EMAIL DEBUG] Tentando autenticar com usuário: {} e remetente: {}", impl.getUsername(), from);
                log.info("[EMAIL DEBUG] Senha configurada: {}", impl.getPassword());
            } else {
                log.warn("[EMAIL DEBUG] mailSender não é uma instância de JavaMailSenderImpl, não é possível logar credenciais.");
            }
            mailSender.send(message);
            log.info("E-mail resumo enviado para {}", to);
        });
    }
}
